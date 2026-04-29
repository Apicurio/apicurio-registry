package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.microsoft.kiota.ApiException;
import io.apicurio.registry.serde.protobuf.ref.RefOuterClass.Ref;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link ProtobufDeserializerConfig#fallbackOnSchemaError()} configuration
 * and the fallback deserialization path in {@link ProtobufDeserializer}.
 * <p>
 * The fallback allows the deserializer to skip schema resolution and parse raw protobuf bytes
 * directly when a {@code specificReturnClass} is configured and schema lookup fails for a
 * recoverable reason (registry unreachable, missing schema, missing transitive imports, ...).
 * </p>
 * <p>
 * Uses {@link DescriptorProtos.FileDescriptorProto} as the test message type since it is always
 * on the classpath (part of protobuf-java) and has a standard {@code parseFrom(InputStream)} method.
 * </p>
 * <p>
 * The schema resolver used in these tests is configured with realistic failure modes - the
 * exceptions it throws are exactly the ones the real {@code DefaultSchemaResolver} surfaces when
 * the registry is unreachable or returns an error. This keeps the tests honest about which
 * categories of failure the production code is actually willing to recover from, addressing the
 * review concern about "forcing" the fallback with arbitrary exceptions.
 * </p>
 */
class ProtobufDeserializerFallbackTest {

    // =========================================================================
    // Config tests
    // =========================================================================

    /**
     * Verifies that fallbackOnSchemaError defaults to false when not configured,
     * preserving backward compatibility.
     */
    @Test
    void testFallbackDisabledByDefault() {
        Map<String, Object> configs = new HashMap<>();
        configs.put("apicurio.registry.url", "http://localhost:8081/apis/registry/v3");

        ProtobufDeserializerConfig config = new ProtobufDeserializerConfig(configs, false);

        assertFalse(config.fallbackOnSchemaError(),
                "Fallback should be disabled by default to maintain backward compatibility");
    }

    /**
     * Verifies that fallbackOnSchemaError can be enabled via String config value.
     */
    @Test
    void testFallbackEnabledViaStringConfig() {
        Map<String, Object> configs = new HashMap<>();
        configs.put("apicurio.registry.url", "http://localhost:8081/apis/registry/v3");
        configs.put(ProtobufDeserializerConfig.FALLBACK_ON_SCHEMA_ERROR, "true");

        ProtobufDeserializerConfig config = new ProtobufDeserializerConfig(configs, false);

        assertTrue(config.fallbackOnSchemaError(),
                "Fallback should be enabled when configured with String 'true'");
    }

    /**
     * Verifies that fallbackOnSchemaError accepts Boolean values directly.
     */
    @Test
    void testFallbackAcceptsBooleanValue() {
        Map<String, Object> configs = new HashMap<>();
        configs.put("apicurio.registry.url", "http://localhost:8081/apis/registry/v3");
        configs.put(ProtobufDeserializerConfig.FALLBACK_ON_SCHEMA_ERROR, Boolean.TRUE);

        ProtobufDeserializerConfig config = new ProtobufDeserializerConfig(configs, false);

        assertTrue(config.fallbackOnSchemaError(),
                "Fallback should accept Boolean.TRUE directly");
    }

    /**
     * Verifies the config constant has the expected property name prefix,
     * consistent with the existing {@code apicurio.protobuf.derive.class} convention.
     */
    @Test
    void testConfigPropertyNamespace() {
        assertTrue(ProtobufDeserializerConfig.FALLBACK_ON_SCHEMA_ERROR.startsWith("apicurio.protobuf."),
                "Config property should be in the apicurio.protobuf namespace");
    }

    // =========================================================================
    // parseFallback wire-format tests (simulating realistic registry failures)
    // =========================================================================

    /**
     * Verifies that the fallback correctly strips the Apicurio wire-format prefix
     * (magic byte + 4-byte content ID + Ref message) and parses the protobuf payload
     * when the registry returns an HTTP error.
     */
    @Test
    void testParseFallbackStripsWireFormatAndParsesPayload() throws Exception {
        DescriptorProtos.FileDescriptorProto testMessage = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .setPackage("test.fallback")
                .setSyntax("proto3")
                .build();

        byte[] wireFormatBytes = buildApicurioWireFormat(testMessage.toByteArray(), 42, "TestMessage");

        ProtobufDeserializer<DescriptorProtos.FileDescriptorProto> deserializer = createFallbackDeserializer(
                DescriptorProtos.FileDescriptorProto.class, registryUnreachable());

        DescriptorProtos.FileDescriptorProto result = deserializer.deserializeData("test-topic", wireFormatBytes);

        assertNotNull(result, "Fallback should produce a non-null result");
        assertEquals("test.proto", result.getName(),
                "Parsed message should have the correct 'name' field");
        assertEquals("test.fallback", result.getPackage(),
                "Parsed message should have the correct 'package' field");
        assertEquals("proto3", result.getSyntax(),
                "Parsed message should have the correct 'syntax' field");
    }

    /**
     * Verifies fallback works with an empty Ref message (varint 0 length prefix) when the
     * registry returns a 404 (e.g. the producer's schema has not yet been indexed).
     */
    @Test
    void testParseFallbackWithEmptyRef() throws Exception {
        DescriptorProtos.FileDescriptorProto testMessage = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("empty-ref.proto")
                .build();

        byte[] wireFormatBytes = buildApicurioWireFormatEmptyRef(testMessage.toByteArray(), 1);

        ProtobufDeserializer<DescriptorProtos.FileDescriptorProto> deserializer = createFallbackDeserializer(
                DescriptorProtos.FileDescriptorProto.class, schemaNotFound());

        DescriptorProtos.FileDescriptorProto result = deserializer.deserializeData("test-topic", wireFormatBytes);

        assertNotNull(result, "Fallback should work with empty Ref prefix");
        assertEquals("empty-ref.proto", result.getName(),
                "Parsed message should survive empty Ref prefix");
    }

    /**
     * Verifies that the fallback also fires when the registry returns a descriptor whose
     * imports cannot be resolved - the schema parser surfaces this as a
     * {@link Descriptors.DescriptorValidationException}, which is in our recoverable set.
     */
    @Test
    void testFallbackTriggeredByDescriptorValidationException() throws Exception {
        DescriptorProtos.FileDescriptorProto testMessage = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("missing-import.proto")
                .build();

        byte[] wireFormatBytes = buildApicurioWireFormat(testMessage.toByteArray(), 7, "Test");

        ProtobufDeserializer<DescriptorProtos.FileDescriptorProto> deserializer = createFallbackDeserializer(
                DescriptorProtos.FileDescriptorProto.class, missingTransitiveImport());

        DescriptorProtos.FileDescriptorProto result = deserializer.deserializeData("test-topic", wireFormatBytes);

        assertNotNull(result, "Fallback should fire on DescriptorValidationException in cause chain");
        assertEquals("missing-import.proto", result.getName());
    }

    /**
     * Verifies that null data returns null per the standard Kafka deserializer contract.
     * This is handled by {@link io.apicurio.registry.serde.AbstractDeserializer#deserializeData}
     * before the fallback path is reached.
     */
    @Test
    void testNullDataReturnsNull() {
        ProtobufDeserializer<DescriptorProtos.FileDescriptorProto> deserializer = createFallbackDeserializer(
                DescriptorProtos.FileDescriptorProto.class, registryUnreachable());

        DescriptorProtos.FileDescriptorProto result = deserializer.deserializeData("test-topic", null);

        assertNull(result, "Null input should return null per Kafka contract");
    }

    // =========================================================================
    // Negative tests: fallback should NOT activate
    // =========================================================================

    /**
     * Verifies that when fallback is disabled (the default), schema resolution failure
     * propagates as the original exception without attempting direct parsing.
     */
    @Test
    void testFallbackDisabledPropagatesException() throws Exception {
        DescriptorProtos.FileDescriptorProto testMessage = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();

        byte[] wireFormatBytes = buildApicurioWireFormat(testMessage.toByteArray(), 1, "Test");

        ApiException simulated = new ApiException("registry returned 503 Service Unavailable");
        ProtobufDeserializer<DescriptorProtos.FileDescriptorProto> deserializer =
                new ProtobufDeserializer<>(new ThrowingSchemaResolver<>(simulated));

        Map<String, Object> config = new HashMap<>();
        config.put("apicurio.registry.url", "http://localhost:99999/nonexistent");
        config.put("apicurio.registry.deserializer.value.return-class",
                DescriptorProtos.FileDescriptorProto.class);
        // fallbackOnSchemaError NOT set - defaults to false

        deserializer.configure(new io.apicurio.registry.serde.config.SerdeConfig(config), false);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> deserializer.deserializeData("test-topic", wireFormatBytes),
                "With fallback disabled, schema resolution failure should propagate");

        // The original ApiException must reach the caller untouched (possibly wrapped).
        assertTrue(thrown == simulated || hasCause(thrown, simulated),
                "Exception should be (or wrap) the original schema resolution error, was: " + thrown);
    }

    /**
     * Critical negative test: a non-recoverable failure such as {@link NullPointerException}
     * from inside the schema resolver must NOT be swallowed by the fallback, even when the
     * fallback is enabled. Catching such errors would mask real bugs.
     */
    @Test
    void testFallbackDoesNotSwallowProgrammingErrors() throws Exception {
        DescriptorProtos.FileDescriptorProto testMessage = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();
        byte[] wireFormatBytes = buildApicurioWireFormat(testMessage.toByteArray(), 1, "Test");

        NullPointerException bug = new NullPointerException("simulated programming bug in resolver");
        ProtobufDeserializer<DescriptorProtos.FileDescriptorProto> deserializer = createFallbackDeserializer(
                DescriptorProtos.FileDescriptorProto.class, bug);

        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> deserializer.deserializeData("test-topic", wireFormatBytes),
                "NullPointerException must propagate even with fallback enabled - "
                        + "the fallback only catches recoverable schema-resolution errors");

        assertSame(bug, thrown, "The original NPE must be re-thrown unchanged");
    }

    /**
     * The typed catch on {@link IllegalStateException} is narrowed to "cause is
     * {@link Descriptors.DescriptorValidationException}" because {@code IllegalStateException}
     * is also thrown by the resolver for non-recoverable conditions (misconfiguration,
     * "artifact reference cannot be null", etc.). Those must propagate even with the
     * fallback enabled.
     */
    @Test
    void testFallbackDoesNotSwallowGenericIllegalState() throws Exception {
        DescriptorProtos.FileDescriptorProto testMessage = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();
        byte[] wireFormatBytes = buildApicurioWireFormat(testMessage.toByteArray(), 1, "Test");

        IllegalStateException notRecoverable = new IllegalStateException("artifact reference cannot be null");
        ProtobufDeserializer<DescriptorProtos.FileDescriptorProto> deserializer = createFallbackDeserializer(
                DescriptorProtos.FileDescriptorProto.class, notRecoverable);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> deserializer.deserializeData("test-topic", wireFormatBytes),
                "IllegalStateException without a DescriptorValidationException cause must propagate "
                        + "even with fallback enabled");

        assertSame(notRecoverable, thrown,
                "The original IllegalStateException must be re-thrown unchanged");
    }

    /**
     * Companion to the NPE test: an {@link IllegalArgumentException} (typically a configuration
     * mistake) must also propagate rather than silently activating the fallback.
     */
    @Test
    void testFallbackDoesNotSwallowIllegalArgument() throws Exception {
        DescriptorProtos.FileDescriptorProto testMessage = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();
        byte[] wireFormatBytes = buildApicurioWireFormat(testMessage.toByteArray(), 1, "Test");

        IllegalArgumentException bug = new IllegalArgumentException("bad config");
        ProtobufDeserializer<DescriptorProtos.FileDescriptorProto> deserializer = createFallbackDeserializer(
                DescriptorProtos.FileDescriptorProto.class, bug);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> deserializer.deserializeData("test-topic", wireFormatBytes),
                "IllegalArgumentException must propagate - the fallback is for transient registry "
                        + "failures, not configuration errors");

        assertSame(bug, thrown, "The original IllegalArgumentException must be re-thrown unchanged");
    }

    /**
     * Verifies the {@code DynamicMessage} guard in {@link ProtobufDeserializer#fallbackEnabled()}.
     * When {@code specificReturnClass} is {@link DynamicMessage} the fallback cannot run - direct
     * parsing requires a concrete generated class with a static {@code parseFrom(InputStream)},
     * whereas {@code DynamicMessage.parseFrom} needs a {@link Descriptors.Descriptor} that the
     * fallback does not have. Even with {@code fallbackOnSchemaError=true}, the original
     * resolver failure must propagate so the caller sees the real error instead of a misleading
     * "Fallback protobuf parsing failed" wrapping.
     */
    @Test
    void testFallbackDoesNotActivateForDynamicMessage() throws Exception {
        DescriptorProtos.FileDescriptorProto testMessage = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();
        byte[] wireFormatBytes = buildApicurioWireFormat(testMessage.toByteArray(), 1, "Test");

        ApiException simulated = new ApiException("registry returned 404 Not Found");
        ProtobufDeserializer<DynamicMessage> deserializer = createFallbackDeserializer(
                DynamicMessage.class, simulated);

        ApiException thrown = assertThrows(ApiException.class,
                () -> deserializer.deserializeData("test-topic", wireFormatBytes),
                "DynamicMessage return class must disable the fallback even when "
                        + "fallbackOnSchemaError=true - the original schema-resolution error must propagate");

        assertSame(simulated, thrown,
                "The original ApiException must be re-thrown unchanged; fallback must not run");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Creates a {@link ProtobufDeserializer} configured with fallback enabled and a specific
     * return class. The provided {@code resolverFailure} is what the schema resolver will throw
     * when invoked, simulating a real registry failure.
     */
    private <T extends com.google.protobuf.Message> ProtobufDeserializer<T> createFallbackDeserializer(
            Class<T> returnClass, RuntimeException resolverFailure) {
        ProtobufDeserializer<T> deserializer = new ProtobufDeserializer<>(
                new ThrowingSchemaResolver<>(resolverFailure));

        Map<String, Object> config = new HashMap<>();
        config.put("apicurio.registry.url", "http://localhost:99999/nonexistent");
        config.put("apicurio.registry.deserializer.value.return-class", returnClass);
        config.put(ProtobufDeserializerConfig.FALLBACK_ON_SCHEMA_ERROR, "true");

        deserializer.configure(new io.apicurio.registry.serde.config.SerdeConfig(config), false);
        return deserializer;
    }

    /** Simulates the registry being unreachable (TCP connect refused). */
    private static RuntimeException registryUnreachable() {
        return new UncheckedIOException(
                new ConnectException("Connection refused: registry unreachable"));
    }

    /** Simulates the registry returning HTTP 404 (schema not yet propagated).
     *  The status code is package-private to set; the message is enough for our assertions. */
    private static RuntimeException schemaNotFound() {
        return new ApiException("Schema content not found");
    }

    /**
     * Simulates the schema parser failing to validate a descriptor returned by the registry,
     * for example due to a missing transitive import. The {@link Descriptors.DescriptorValidationException}
     * surfaces wrapped in an {@link IllegalStateException} by {@code ProtobufSchemaParser}.
     * <p>
     * We trigger a real validation failure by constructing a {@link DescriptorProtos.FileDescriptorProto}
     * containing a message field that references an undefined type - exactly the shape of error
     * a missing transitive import produces. This guarantees the exception is the same type the
     * protobuf library actually throws in production rather than a hand-crafted impostor.
     * </p>
     */
    private static RuntimeException missingTransitiveImport() {
        DescriptorProtos.FieldDescriptorProto badField = DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("ref")
                .setNumber(1)
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName(".missing.UnresolvedType")
                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .build();
        DescriptorProtos.DescriptorProto badMessage = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Bad")
                .addField(badField)
                .build();
        DescriptorProtos.FileDescriptorProto bad = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("bad.proto")
                .setSyntax("proto3")
                .addMessageType(badMessage)
                .build();
        try {
            Descriptors.FileDescriptor.buildFrom(bad, new Descriptors.FileDescriptor[0]);
            throw new AssertionError("expected DescriptorValidationException to be thrown - "
                    + "unresolved field type should not validate");
        } catch (Descriptors.DescriptorValidationException dve) {
            return new IllegalStateException("Error parsing protobuf schema", dve);
        }
    }

    private static boolean hasCause(Throwable t, Throwable target) {
        Throwable c = t.getCause();
        while (c != null) {
            if (c == target) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }

    /**
     * Builds bytes in Apicurio wire format with a 4-byte content ID (Default4ByteIdHandler):
     * {@code [0x00 magic][4-byte contentId][Ref delimited][protobuf payload]}.
     */
    private byte[] buildApicurioWireFormat(byte[] payload, int contentId, String refName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x00);
        out.write(ByteBuffer.allocate(4).putInt(contentId).array());
        Ref ref = Ref.newBuilder().setName(refName).build();
        ref.writeDelimitedTo(out);
        out.write(payload);
        return out.toByteArray();
    }

    /**
     * Builds bytes in Apicurio wire format with an empty Ref (varint 0 length).
     */
    private byte[] buildApicurioWireFormatEmptyRef(byte[] payload, int contentId) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x00);
        out.write(ByteBuffer.allocate(4).putInt(contentId).array());
        out.write(0x00); // empty Ref: varint 0 length
        out.write(payload);
        return out.toByteArray();
    }

    /**
     * A {@link io.apicurio.registry.resolver.SchemaResolver} stub that surfaces a configurable
     * exception when {@code resolveSchemaByArtifactReference} is called. The exception passed in
     * mirrors what the real {@code DefaultSchemaResolver} would throw against a misbehaving
     * registry (e.g. {@link ApiException}, {@link UncheckedIOException}). All other resolver
     * methods are no-ops because the deserializer never touches them on the failure path.
     *
     * @param <T> the protobuf message type
     */
    private static class ThrowingSchemaResolver<T extends com.google.protobuf.Message>
            implements io.apicurio.registry.resolver.SchemaResolver<
            io.apicurio.registry.utils.protobuf.schema.ProtobufSchema, T> {

        private final RuntimeException toThrow;

        ThrowingSchemaResolver(RuntimeException toThrow) {
            this.toThrow = toThrow;
        }

        @Override
        public void setClientFacade(io.apicurio.registry.resolver.client.RegistryClientFacade client) {
            // No-op: tests wire the resolver directly and never install a client facade.
        }

        @Override
        public void setArtifactResolverStrategy(
                io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy<
                        io.apicurio.registry.utils.protobuf.schema.ProtobufSchema, T> strategy) {
            // No-op: resolver strategy is irrelevant on the failure path exercised by these tests.
        }

        @Override
        public io.apicurio.registry.resolver.SchemaParser<
                io.apicurio.registry.utils.protobuf.schema.ProtobufSchema, T> getSchemaParser() {
            return new ProtobufSchemaParser<>();
        }

        @Override
        public io.apicurio.registry.resolver.SchemaLookupResult<
                io.apicurio.registry.utils.protobuf.schema.ProtobufSchema> resolveSchema(
                io.apicurio.registry.resolver.data.Record<T> data) {
            throw toThrow;
        }

        @Override
        public io.apicurio.registry.resolver.SchemaLookupResult<
                io.apicurio.registry.utils.protobuf.schema.ProtobufSchema> resolveSchemaByArtifactReference(
                io.apicurio.registry.resolver.strategy.ArtifactReference reference) {
            throw toThrow;
        }

        @Override
        public void reset() {
            // No-op: the test resolver has no state to clear.
        }

        @Override
        public void close() {
            // No-op: the test resolver holds no resources.
        }
    }
}
