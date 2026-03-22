package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.DescriptorProtos;
import io.apicurio.registry.serde.protobuf.ref.RefOuterClass.Ref;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link ProtobufDeserializerConfig#fallbackOnSchemaError()} configuration
 * and the fallback deserialization path in {@link ProtobufDeserializer}.
 * <p>
 * The fallback allows the deserializer to skip schema resolution and parse raw protobuf bytes
 * directly when a {@code specificReturnClass} is configured and schema lookup fails.
 * </p>
 * <p>
 * Uses {@link DescriptorProtos.FileDescriptorProto} as the test message type since it is always
 * on the classpath (part of protobuf-java) and has a standard {@code parseFrom(InputStream)} method.
 * </p>
 */
public class ProtobufDeserializerFallbackTest {

    // =========================================================================
    // Config tests
    // =========================================================================

    /**
     * Verifies that fallbackOnSchemaError defaults to false when not configured,
     * preserving backward compatibility.
     */
    @Test
    public void testFallbackDisabledByDefault() {
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
    public void testFallbackEnabledViaStringConfig() {
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
    public void testFallbackAcceptsBooleanValue() {
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
    public void testConfigPropertyNamespace() {
        assertTrue(ProtobufDeserializerConfig.FALLBACK_ON_SCHEMA_ERROR.startsWith("apicurio.protobuf."),
                "Config property should be in the apicurio.protobuf namespace");
    }

    // =========================================================================
    // parseFallback wire-format tests
    // =========================================================================

    /**
     * Verifies that the fallback correctly strips the Apicurio wire-format prefix
     * (magic byte + 4-byte content ID + Ref message) and parses the protobuf payload.
     */
    @Test
    public void testParseFallbackStripsWireFormatAndParsesPayload() throws Exception {
        DescriptorProtos.FileDescriptorProto testMessage = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .setPackage("test.fallback")
                .setSyntax("proto3")
                .build();

        byte[] wireFormatBytes = buildApicurioWireFormat(testMessage.toByteArray(), 42, "TestMessage");

        ProtobufDeserializer<DescriptorProtos.FileDescriptorProto> deserializer = createFallbackDeserializer(
                DescriptorProtos.FileDescriptorProto.class);

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
     * Verifies fallback works with an empty Ref message (varint 0 length prefix).
     */
    @Test
    public void testParseFallbackWithEmptyRef() throws Exception {
        DescriptorProtos.FileDescriptorProto testMessage = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("empty-ref.proto")
                .build();

        byte[] wireFormatBytes = buildApicurioWireFormatEmptyRef(testMessage.toByteArray(), 1);

        ProtobufDeserializer<DescriptorProtos.FileDescriptorProto> deserializer = createFallbackDeserializer(
                DescriptorProtos.FileDescriptorProto.class);

        DescriptorProtos.FileDescriptorProto result = deserializer.deserializeData("test-topic", wireFormatBytes);

        assertNotNull(result, "Fallback should work with empty Ref prefix");
        assertEquals("empty-ref.proto", result.getName(),
                "Parsed message should survive empty Ref prefix");
    }

    /**
     * Verifies that null data returns null per the standard Kafka deserializer contract.
     * This is handled by {@link io.apicurio.registry.serde.AbstractDeserializer#deserializeData}
     * before the fallback path is reached.
     */
    @Test
    public void testNullDataReturnsNull() throws Exception {
        ProtobufDeserializer<DescriptorProtos.FileDescriptorProto> deserializer = createFallbackDeserializer(
                DescriptorProtos.FileDescriptorProto.class);

        DescriptorProtos.FileDescriptorProto result = deserializer.deserializeData("test-topic", null);

        assertNull(result, "Null input should return null per Kafka contract");
    }

    // =========================================================================
    // Negative tests: fallback should NOT activate
    // =========================================================================

    /**
     * Verifies that when fallback is disabled (the default), schema resolution failure
     * propagates as an exception without attempting direct parsing.
     */
    @Test
    public void testFallbackDisabledPropagatesException() throws Exception {
        DescriptorProtos.FileDescriptorProto testMessage = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();

        byte[] wireFormatBytes = buildApicurioWireFormat(testMessage.toByteArray(), 1, "Test");

        // Create deserializer with fallback DISABLED (default)
        ProtobufDeserializer<DescriptorProtos.FileDescriptorProto> deserializer =
                new ProtobufDeserializer<>(new ThrowingSchemaResolver<>());

        Map<String, Object> config = new HashMap<>();
        config.put("apicurio.registry.url", "http://localhost:99999/nonexistent");
        config.put("apicurio.registry.deserializer.value.return-class",
                DescriptorProtos.FileDescriptorProto.class);
        // fallbackOnSchemaError NOT set — defaults to false

        deserializer.configure(new io.apicurio.registry.serde.config.SerdeConfig(config), false);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> deserializer.deserializeData("test-topic", wireFormatBytes),
                "With fallback disabled, schema resolution failure should propagate");

        assertTrue(thrown.getMessage().contains("Simulated schema resolution failure"),
                "Exception should be the original schema resolution error");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Creates a {@link ProtobufDeserializer} configured with fallback enabled and a specific return class.
     * Uses a {@link ThrowingSchemaResolver} to force the fallback path.
     *
     * @param returnClass the protobuf message class to deserialize into
     * @param <T>         the message type
     * @return a configured deserializer that will use the fallback path
     */
    private <T extends com.google.protobuf.Message> ProtobufDeserializer<T> createFallbackDeserializer(
            Class<T> returnClass) {
        ProtobufDeserializer<T> deserializer = new ProtobufDeserializer<>(new ThrowingSchemaResolver<>());

        Map<String, Object> config = new HashMap<>();
        config.put("apicurio.registry.url", "http://localhost:99999/nonexistent");
        config.put("apicurio.registry.deserializer.value.return-class", returnClass);
        config.put(ProtobufDeserializerConfig.FALLBACK_ON_SCHEMA_ERROR, "true");

        deserializer.configure(new io.apicurio.registry.serde.config.SerdeConfig(config), false);
        return deserializer;
    }

    /**
     * Builds bytes in Apicurio wire format with a 4-byte content ID (Default4ByteIdHandler):
     * {@code [0x00 magic][4-byte contentId][Ref delimited][protobuf payload]}.
     *
     * @param payload   the raw protobuf bytes
     * @param contentId the schema content ID
     * @param refName   the message type name for the Ref prefix
     * @return the complete wire-format byte array
     */
    private byte[] buildApicurioWireFormat(byte[] payload, int contentId, String refName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x00); // magic byte
        out.write(ByteBuffer.allocate(4).putInt(contentId).array()); // 4-byte content ID
        Ref ref = Ref.newBuilder().setName(refName).build();
        ref.writeDelimitedTo(out); // Ref length-delimited
        out.write(payload); // protobuf payload
        return out.toByteArray();
    }

    /**
     * Builds bytes in Apicurio wire format with an empty Ref (varint 0 length).
     *
     * @param payload   the raw protobuf bytes
     * @param contentId the schema content ID
     * @return the complete wire-format byte array
     */
    private byte[] buildApicurioWireFormatEmptyRef(byte[] payload, int contentId) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x00); // magic byte
        out.write(ByteBuffer.allocate(4).putInt(contentId).array()); // 4-byte content ID
        out.write(0x00); // empty Ref: varint 0 length
        out.write(payload); // protobuf payload
        return out.toByteArray();
    }

    /**
     * A {@link io.apicurio.registry.resolver.SchemaResolver} that always throws on resolution,
     * forcing the deserializer into the fallback path. All non-resolution methods are no-ops.
     *
     * @param <T> the protobuf message type
     */
    private static class ThrowingSchemaResolver<T extends com.google.protobuf.Message>
            implements io.apicurio.registry.resolver.SchemaResolver<
            io.apicurio.registry.utils.protobuf.schema.ProtobufSchema, T> {

        @Override
        public void setClientFacade(io.apicurio.registry.resolver.client.RegistryClientFacade client) {
        }

        @Override
        public void setArtifactResolverStrategy(
                io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy<
                        io.apicurio.registry.utils.protobuf.schema.ProtobufSchema, T> strategy) {
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
            throw new RuntimeException("Simulated schema resolution failure — registry unreachable");
        }

        @Override
        public io.apicurio.registry.resolver.SchemaLookupResult<
                io.apicurio.registry.utils.protobuf.schema.ProtobufSchema> resolveSchemaByArtifactReference(
                io.apicurio.registry.resolver.strategy.ArtifactReference reference) {
            throw new RuntimeException("Simulated schema resolution failure — registry unreachable");
        }

        @Override
        public void reset() {
        }

        @Override
        public void close() {
        }
    }
}
