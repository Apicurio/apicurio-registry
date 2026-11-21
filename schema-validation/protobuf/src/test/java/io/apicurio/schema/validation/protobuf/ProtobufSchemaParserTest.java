package io.apicurio.schema.validation.protobuf;

import com.google.protobuf.DynamicMessage;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ProtobufSchemaParserTest {

    private ProtobufSchemaParser<DynamicMessage> parser;

    @BeforeEach
    public void setup() {
        parser = new ProtobufSchemaParser<>();
    }

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

        // Should not throw NPE when FileDescriptorUtils.toDescriptor returns null due to unresolved import
        ProtobufSchema result = parser.parseSchema(rawSchema, resolvedReferences);

        assertNotNull(result);
        assertNotNull(result.getFileDescriptor());
        assertEquals("test", result.getFileDescriptor().getPackage());
        assertNotNull(result.getFileDescriptor().findMessageTypeByName("Event"));
    }

    @Test
    public void testParseSchemaWithMissingImport() {
        String schemaWithMissingImport = """
                syntax = "proto3";
                package test;
                import "missing/dependency.proto";
                message MyRecord {
                  string id = 1;
                }
                """;
        byte[] rawSchema = schemaWithMissingImport.getBytes();
        Map<String, ParsedSchema<ProtobufSchema>> resolvedReferences = Collections.emptyMap();

        ProtobufSchema result = parser.parseSchema(rawSchema, resolvedReferences);

        assertNotNull(result);
        assertNotNull(result.getFileDescriptor());
        assertEquals("test", result.getFileDescriptor().getPackage());
        assertNotNull(result.getFileDescriptor().findMessageTypeByName("MyRecord"));
    }
}
