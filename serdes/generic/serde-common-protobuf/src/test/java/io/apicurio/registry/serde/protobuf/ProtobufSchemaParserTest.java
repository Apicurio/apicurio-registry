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
}
