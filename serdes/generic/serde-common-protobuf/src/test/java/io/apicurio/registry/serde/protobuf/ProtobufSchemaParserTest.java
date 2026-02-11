package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for ProtobufSchemaParser to verify correct handling of different schema formats.
 * This includes text .proto format, raw binary FileDescriptorProto, and base64-encoded binary.
 *
 * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/7269">Issue #7269</a>
 */
public class ProtobufSchemaParserTest {

    private static final String SIMPLE_PROTO_SCHEMA = """
            syntax = "proto3";
            package test;
            message Simple {
              string name = 1;
            }
            """;

    private static final String DEP_PROTO_SCHEMA = """
            syntax = "proto3";
            package test;
            message Dep {
              string name = 1;
            }
            """;

    private static final String ROOT_PROTO_SCHEMA = """
            syntax = "proto3";
            package test;
            import "dep.proto";
            message Root {
              Dep d = 1;
            }
            """;

    private ProtobufSchemaParser<DynamicMessage> parser;

    @BeforeEach
    public void setup() {
        parser = new ProtobufSchemaParser<>();
    }

    /**
     * Test that text .proto format is parsed correctly.
     */
    @Test
    public void testParseSchemaWithTextFormat() {
        byte[] rawSchema = SIMPLE_PROTO_SCHEMA.getBytes();
        Map<String, ParsedSchema<ProtobufSchema>> resolvedReferences = Collections.emptyMap();

        ProtobufSchema result = parser.parseSchema(rawSchema, resolvedReferences);

        assertNotNull(result);
        assertNotNull(result.getFileDescriptor());
        assertEquals("test", result.getFileDescriptor().getPackage());
        assertNotNull(result.getFileDescriptor().findMessageTypeByName("Simple"));
    }

    /**
     * Test that raw binary FileDescriptorProto format is parsed correctly.
     */
    @Test
    public void testParseSchemaWithRawBinaryFormat() throws Exception {
        // Create a FileDescriptorProto from the text schema
        Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                SIMPLE_PROTO_SCHEMA, "simple.proto", Optional.of("test"));
        byte[] rawSchema = fd.toProto().toByteArray();

        Map<String, ParsedSchema<ProtobufSchema>> resolvedReferences = Collections.emptyMap();

        ProtobufSchema result = parser.parseSchema(rawSchema, resolvedReferences);

        assertNotNull(result);
        assertNotNull(result.getFileDescriptor());
        assertEquals("test", result.getFileDescriptor().getPackage());
        assertNotNull(result.getFileDescriptor().findMessageTypeByName("Simple"));
    }

    /**
     * Test that base64-encoded binary FileDescriptorProto format is parsed correctly.
     * This is the main fix for issue #7269.
     */
    @Test
    public void testParseSchemaWithBase64EncodedFormat() throws Exception {
        // Create a FileDescriptorProto from the text schema and encode as base64
        Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                SIMPLE_PROTO_SCHEMA, "simple.proto", Optional.of("test"));
        String base64Encoded = Base64.getEncoder().encodeToString(fd.toProto().toByteArray());
        byte[] rawSchema = base64Encoded.getBytes();

        Map<String, ParsedSchema<ProtobufSchema>> resolvedReferences = Collections.emptyMap();

        ProtobufSchema result = parser.parseSchema(rawSchema, resolvedReferences);

        assertNotNull(result);
        assertNotNull(result.getFileDescriptor());
        assertEquals("test", result.getFileDescriptor().getPackage());
        assertNotNull(result.getFileDescriptor().findMessageTypeByName("Simple"));
    }

    /**
     * Test that base64-encoded binary with references works correctly.
     */
    @Test
    public void testParseSchemaWithBase64EncodedAndReferences() throws Exception {
        // First, create the dependency schema
        Descriptors.FileDescriptor depFd = FileDescriptorUtils.protoFileToFileDescriptor(
                DEP_PROTO_SCHEMA, "dep.proto", Optional.of("test"));

        // Create ParsedSchema for the dependency
        ProtobufSchema depProtobufSchema = new ProtobufSchema(depFd,
                FileDescriptorUtils.fileDescriptorToProtoFile(depFd.toProto()));
        ParsedSchema<ProtobufSchema> depParsedSchema = new ParsedSchemaImpl<ProtobufSchema>()
                .setParsedSchema(depProtobufSchema)
                .setReferenceName("dep.proto");

        // Create resolvedReferences map
        Map<String, ParsedSchema<ProtobufSchema>> resolvedReferences = new HashMap<>();
        resolvedReferences.put("dep.proto", depParsedSchema);

        // Create the root schema as base64-encoded binary
        // For this test, we need to build the root FileDescriptor with the dependency
        DescriptorProtos.FileDescriptorProto.Builder rootProtoBuilder = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("root.proto")
                .setPackage("test")
                .setSyntax("proto3")
                .addDependency("dep.proto")
                .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                        .setName("Root")
                        .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                                .setName("d")
                                .setNumber(1)
                                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                                .setTypeName(".test.Dep")
                                .build())
                        .build());

        DescriptorProtos.FileDescriptorProto rootProto = rootProtoBuilder.build();
        String base64Encoded = Base64.getEncoder().encodeToString(rootProto.toByteArray());
        byte[] rawSchema = base64Encoded.getBytes();

        ProtobufSchema result = parser.parseSchema(rawSchema, resolvedReferences);

        assertNotNull(result);
        assertNotNull(result.getFileDescriptor());
        assertEquals("test", result.getFileDescriptor().getPackage());
        assertNotNull(result.getFileDescriptor().findMessageTypeByName("Root"));
    }

    /**
     * Test that invalid content throws appropriate exception.
     */
    @Test
    public void testParseSchemaWithInvalidContent() {
        byte[] rawSchema = "this is not valid protobuf or base64".getBytes();
        Map<String, ParsedSchema<ProtobufSchema>> resolvedReferences = Collections.emptyMap();

        assertThrows(RuntimeException.class, () -> parser.parseSchema(rawSchema, resolvedReferences));
    }

    /**
     * Test that schemas importing well-known types (like google/protobuf/timestamp.proto)
     * are parsed correctly even when resolvedReferences is empty.
     * This is the fix for issue #7377.
     *
     * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/7377">Issue #7377</a>
     */
    @Test
    public void testParseSchemaWithWellKnownTypeImportAndEmptyReferences() {
        String schemaWithTimestamp = """
                syntax = "proto3";
                package test;
                import "google/protobuf/timestamp.proto";
                message Event {
                  string name = 1;
                  google.protobuf.Timestamp created_at = 2;
                }
                """;
        byte[] rawSchema = schemaWithTimestamp.getBytes();
        Map<String, ParsedSchema<ProtobufSchema>> resolvedReferences = Collections.emptyMap();

        // This should not throw NPE (the bug from issue #7377)
        ProtobufSchema result = parser.parseSchema(rawSchema, resolvedReferences);

        assertNotNull(result);
        assertNotNull(result.getFileDescriptor());
        assertEquals("test", result.getFileDescriptor().getPackage());
        assertNotNull(result.getFileDescriptor().findMessageTypeByName("Event"));
    }

    /**
     * Test that schemas importing multiple well-known types are parsed correctly
     * even when resolvedReferences is empty.
     *
     * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/7377">Issue #7377</a>
     */
    @Test
    public void testParseSchemaWithMultipleWellKnownTypeImports() {
        String schemaWithMultipleImports = """
                syntax = "proto3";
                package test;
                import "google/protobuf/timestamp.proto";
                import "google/protobuf/duration.proto";
                import "google/protobuf/any.proto";
                message Event {
                  string name = 1;
                  google.protobuf.Timestamp created_at = 2;
                  google.protobuf.Duration duration = 3;
                  google.protobuf.Any metadata = 4;
                }
                """;
        byte[] rawSchema = schemaWithMultipleImports.getBytes();
        Map<String, ParsedSchema<ProtobufSchema>> resolvedReferences = Collections.emptyMap();

        // This should not throw NPE
        ProtobufSchema result = parser.parseSchema(rawSchema, resolvedReferences);

        assertNotNull(result);
        assertNotNull(result.getFileDescriptor());
        assertEquals("test", result.getFileDescriptor().getPackage());
        assertNotNull(result.getFileDescriptor().findMessageTypeByName("Event"));
    }

    /**
     * Test that schemas importing google/protobuf/struct.proto are parsed correctly.
     *
     * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/7377">Issue #7377</a>
     */
    @Test
    public void testParseSchemaWithStructImport() {
        String schemaWithStruct = """
                syntax = "proto3";
                package test;
                import "google/protobuf/struct.proto";
                message Document {
                  string id = 1;
                  google.protobuf.Struct data = 2;
                }
                """;
        byte[] rawSchema = schemaWithStruct.getBytes();
        Map<String, ParsedSchema<ProtobufSchema>> resolvedReferences = Collections.emptyMap();

        // This should not throw NPE
        ProtobufSchema result = parser.parseSchema(rawSchema, resolvedReferences);

        assertNotNull(result);
        assertNotNull(result.getFileDescriptor());
        assertEquals("test", result.getFileDescriptor().getPackage());
        assertNotNull(result.getFileDescriptor().findMessageTypeByName("Document"));
    }
}
