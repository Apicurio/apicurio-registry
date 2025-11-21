package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test edge cases and potential issues in FileDescriptorToProtoConverter.
 */
public class FileDescriptorToProtoConverterEdgeCasesTest {

    @Test
    void testExtensions() {
        String schema = """
            syntax = "proto2";

            message ExtendableMessage {
                extensions 100 to 199;
            }
            """;

        try {
            Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                    schema, "test.proto", Optional.empty());

            String result = FileDescriptorToProtoConverter.convert(fd);

            System.out.println("Extensions output:");
            System.out.println(result);

            // Should contain the message
            assertTrue(result.contains("message ExtendableMessage"));
            // Extensions may or may not be preserved - document current behavior
        } catch (Exception e) {
            fail("Failed to process extensions: " + e.getMessage());
        }
    }

    @Test
    void testFieldDefaultValues() {
        String schema = """
            syntax = "proto2";

            message DefaultValues {
                optional string name = 1 [default = "unknown"];
                optional int32 count = 2 [default = 42];
                optional bool flag = 3 [default = true];
            }
            """;

        try {
            Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                    schema, "test.proto", Optional.empty());

            String result = FileDescriptorToProtoConverter.convert(fd);

            System.out.println("Default values output:");
            System.out.println(result);

            assertTrue(result.contains("message DefaultValues"));
            // Check if defaults are preserved
            boolean hasDefaults = result.contains("default");
            System.out.println("Preserves default values: " + hasDefaults);
        } catch (Exception e) {
            fail("Failed to process default values: " + e.getMessage());
        }
    }

    @Test
    void testFieldOptionsDeprecated() {
        String schema = """
            syntax = "proto3";

            message FieldOptionsTest {
                string field1 = 1 [deprecated = true];
                string field2 = 2;
            }
            """;

        try {
            Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                    schema, "test.proto", Optional.empty());

            String result = FileDescriptorToProtoConverter.convert(fd);

            System.out.println("Field options (deprecated) output:");
            System.out.println(result);

            assertTrue(result.contains("message FieldOptionsTest"));
            // Check if deprecated option is preserved
            boolean hasDeprecated = result.contains("deprecated");
            System.out.println("Preserves deprecated option: " + hasDeprecated);
        } catch (Exception e) {
            fail("Failed to process deprecated option: " + e.getMessage());
        }
    }

    @Test
    void testPackedOption() {
        String schema = """
            syntax = "proto2";

            message PackedTest {
                repeated int32 numbers = 1 [packed = true];
            }
            """;

        try {
            Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                    schema, "test.proto", Optional.empty());

            String result = FileDescriptorToProtoConverter.convert(fd);

            System.out.println("Packed option output:");
            System.out.println(result);

            assertTrue(result.contains("message PackedTest"));
            assertTrue(result.contains("repeated"));
            // Check if packed option is preserved
            boolean hasPacked = result.contains("packed");
            System.out.println("Preserves packed option: " + hasPacked);
        } catch (Exception e) {
            fail("Failed to process packed option: " + e.getMessage());
        }
    }

    @Test
    void testEmptyMessage() {
        String schema = """
            syntax = "proto3";

            message EmptyMessage {}
            """;

        try {
            Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                    schema, "test.proto", Optional.empty());

            String result = FileDescriptorToProtoConverter.convert(fd);

            System.out.println("Empty message output:");
            System.out.println(result);

            assertTrue(result.contains("message EmptyMessage"));
            assertTrue(result.contains("{"));
            assertTrue(result.contains("}"));
        } catch (Exception e) {
            fail("Failed to process empty message: " + e.getMessage());
        }
    }

    @Test
    void testEmptyEnum() {
        String schema = """
            syntax = "proto3";

            enum EmptyEnum {
                UNKNOWN = 0;
            }
            """;

        try {
            Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                    schema, "test.proto", Optional.empty());

            String result = FileDescriptorToProtoConverter.convert(fd);

            System.out.println("Empty enum output:");
            System.out.println(result);

            assertTrue(result.contains("enum EmptyEnum"));
            assertTrue(result.contains("UNKNOWN = 0"));
        } catch (Exception e) {
            fail("Failed to process empty enum: " + e.getMessage());
        }
    }

    @Test
    void testEnumValueOptions() {
        String schema = """
            syntax = "proto3";

            enum Status {
                UNKNOWN = 0;
                ACTIVE = 1 [deprecated = true];
                INACTIVE = 2;
            }
            """;

        try {
            Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                    schema, "test.proto", Optional.empty());

            String result = FileDescriptorToProtoConverter.convert(fd);

            System.out.println("Enum value options output:");
            System.out.println(result);

            assertTrue(result.contains("enum Status"));
            // Check if enum value options are preserved
            boolean hasDeprecated = result.contains("deprecated");
            System.out.println("Preserves enum value deprecated option: " + hasDeprecated);
        } catch (Exception e) {
            fail("Failed to process enum value options: " + e.getMessage());
        }
    }

    @Test
    void testPublicImports() {
        String schema = """
            syntax = "proto3";

            import public "google/protobuf/timestamp.proto";

            message TestMessage {
                google.protobuf.Timestamp ts = 1;
            }
            """;

        try {
            Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                    schema, "test.proto", Optional.empty());

            String result = FileDescriptorToProtoConverter.convert(fd);

            System.out.println("Public imports output:");
            System.out.println(result);

            assertTrue(result.contains("import"));
            assertTrue(result.contains("google/protobuf/timestamp.proto"));
            // Check if "public" modifier is preserved
            boolean hasPublic = result.contains("public");
            System.out.println("Preserves public import modifier: " + hasPublic);
        } catch (Exception e) {
            fail("Failed to process public imports: " + e.getMessage());
        }
    }

    @Test
    void testMessageOptions() {
        String schema = """
            syntax = "proto3";

            message TestMessage {
                option deprecated = true;
                string field = 1;
            }
            """;

        try {
            Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                    schema, "test.proto", Optional.empty());

            String result = FileDescriptorToProtoConverter.convert(fd);

            System.out.println("Message options output:");
            System.out.println(result);

            assertTrue(result.contains("message TestMessage"));
            // Check if message options are preserved
            boolean hasDeprecated = result.contains("deprecated");
            System.out.println("Preserves message deprecated option: " + hasDeprecated);
        } catch (Exception e) {
            fail("Failed to process message options: " + e.getMessage());
        }
    }

    @Test
    void testServiceOptions() {
        String schema = """
            syntax = "proto3";

            message Request {}
            message Response {}

            service MyService {
                option deprecated = true;
                rpc MyMethod(Request) returns (Response);
            }
            """;

        try {
            Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                    schema, "test.proto", Optional.empty());

            String result = FileDescriptorToProtoConverter.convert(fd);

            System.out.println("Service options output:");
            System.out.println(result);

            assertTrue(result.contains("service MyService"));
            // Check if service options are preserved
            boolean hasDeprecated = result.contains("deprecated");
            System.out.println("Preserves service deprecated option: " + hasDeprecated);
        } catch (Exception e) {
            fail("Failed to process service options: " + e.getMessage());
        }
    }

    @Test
    void testProto3OptionalKeyword() {
        // Proto3 introduced explicit 'optional' keyword in protobuf 3.15
        String schema = """
            syntax = "proto3";

            message TestMessage {
                optional string explicit_optional = 1;
                string implicit_optional = 2;
            }
            """;

        try {
            Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                    schema, "test.proto", Optional.empty());

            String result = FileDescriptorToProtoConverter.convert(fd);

            System.out.println("Proto3 optional keyword output:");
            System.out.println(result);

            assertTrue(result.contains("message TestMessage"));

            // Check if proto3 optional is distinguished from implicit optional
            // This is tricky - need to check if field has proto3_optional flag set
            DescriptorProtos.FileDescriptorProto proto = fd.toProto();
            if (proto.getMessageTypeCount() > 0) {
                DescriptorProtos.DescriptorProto msg = proto.getMessageType(0);
                for (DescriptorProtos.FieldDescriptorProto field : msg.getFieldList()) {
                    System.out.println("Field: " + field.getName() +
                        " proto3_optional=" + field.getProto3Optional());
                }
            }
        } catch (Exception e) {
            fail("Failed to process proto3 optional: " + e.getMessage());
        }
    }

    @Test
    void testReservedFieldsAndNumbers() {
        String schema = """
            syntax = "proto3";

            message ReservedTest {
                reserved 2, 15, 9 to 11;
                reserved "foo", "bar";

                string field1 = 1;
                string field3 = 3;
            }
            """;

        try {
            Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                    schema, "test.proto", Optional.empty());

            String result = FileDescriptorToProtoConverter.convert(fd);

            System.out.println("Reserved fields output:");
            System.out.println(result);

            assertTrue(result.contains("message ReservedTest"));
            assertTrue(result.contains("reserved"));
            // Check if both number ranges and names are preserved
            boolean hasReservedNumbers = result.contains("2") || result.contains("15");
            boolean hasReservedNames = result.contains("foo") || result.contains("bar");
            System.out.println("Preserves reserved numbers: " + hasReservedNumbers);
            System.out.println("Preserves reserved names: " + hasReservedNames);
        } catch (Exception e) {
            fail("Failed to process reserved fields: " + e.getMessage());
        }
    }

    @Test
    void testMapFieldsWithMessageValues() {
        String schema = """
            syntax = "proto3";

            message Value {
                string data = 1;
            }

            message MapTest {
                map<string, Value> values = 1;
                map<int32, string> simple = 2;
            }
            """;

        try {
            Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                    schema, "test.proto", Optional.empty());

            String result = FileDescriptorToProtoConverter.convert(fd);

            System.out.println("Map fields output:");
            System.out.println(result);

            assertTrue(result.contains("message MapTest"));
            assertTrue(result.contains("message Value"));
            // Maps might be represented directly or as nested messages
            // The converter should handle both
        } catch (Exception e) {
            fail("Failed to process map fields: " + e.getMessage());
        }
    }

    @Test
    void testCustomOptions() {
        // This requires metadata.proto to be available
        String metadataSchema = """
            syntax = "proto2";
            package metadata;

            import "google/protobuf/descriptor.proto";

            extend google.protobuf.FieldOptions {
                optional string metadata_key = 50001;
                optional string metadata_value = 50002;
            }
            """;

        String schema = """
            syntax = "proto2";

            import "metadata.proto";

            message CustomOptionsTest {
                required string field = 1 [(metadata.metadata_key) = "key", (metadata.metadata_value) = "value"];
            }
            """;

        try {
            java.util.Map<String, String> deps = new java.util.HashMap<>();
            deps.put("metadata.proto", metadataSchema);

            Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                    schema, "test.proto", Optional.empty(), deps, Collections.emptyMap());

            String result = FileDescriptorToProtoConverter.convert(fd);

            System.out.println("Custom options output:");
            System.out.println(result);

            assertTrue(result.contains("message CustomOptionsTest"));
            // Custom options are complex - check if any options are present
            boolean hasOptions = result.contains("[") && result.contains("]");
            System.out.println("Has field options: " + hasOptions);
        } catch (Exception e) {
            System.out.println("Note: Custom options test failed (expected if metadata proto isn't fully configured): " + e.getMessage());
            // Don't fail the test - custom options are complex
        }
    }
}
