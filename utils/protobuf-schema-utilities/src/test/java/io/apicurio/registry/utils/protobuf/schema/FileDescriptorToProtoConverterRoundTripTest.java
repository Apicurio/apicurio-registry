package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.Descriptors.FileDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that FileDescriptorToProtoConverter generates .proto text that round-trips correctly.
 * This is critical for schema auto-registration to avoid registering different content.
 */
public class FileDescriptorToProtoConverterRoundTripTest {

    /**
     * Test that converting FileDescriptor to .proto text and back produces an equivalent FileDescriptor.
     * This ensures that auto-registration will register the same schema content.
     */
    @Test
    public void testRoundTrip_SimpleMessage() throws Exception {
        String originalProto = "syntax = \"proto3\";\n" +
                "package test;\n" +
                "\n" +
                "message Person {\n" +
                "  string name = 1;\n" +
                "  int32 age = 2;\n" +
                "}\n";

        // Compile original .proto to FileDescriptor
        FileDescriptor originalFd = FileDescriptorUtils.protoFileToFileDescriptor(
                originalProto, "test.proto", java.util.Optional.of("test"),
                Collections.emptyMap(), Collections.emptyMap());

        // Convert FileDescriptor back to .proto text
        String generatedProto = FileDescriptorToProtoConverter.convert(originalFd);

        // Compile the generated .proto text
        FileDescriptor generatedFd = FileDescriptorUtils.protoFileToFileDescriptor(
                generatedProto, "test.proto", java.util.Optional.of("test"),
                Collections.emptyMap(), Collections.emptyMap());

        // Verify the FileDescriptors are semantically equivalent
        assertEquals(originalFd.getName(), generatedFd.getName());
        assertEquals(originalFd.getPackage(), generatedFd.getPackage());
        assertEquals(originalFd.getMessageTypes().size(), generatedFd.getMessageTypes().size());

        // Verify message structure
        var originalMsg = originalFd.findMessageTypeByName("Person");
        var generatedMsg = generatedFd.findMessageTypeByName("Person");
        assertNotNull(originalMsg);
        assertNotNull(generatedMsg);
        assertEquals(originalMsg.getFields().size(), generatedMsg.getFields().size());

        // Check that the generated proto contains key elements
        assertTrue(generatedProto.contains("syntax = \"proto3\";"));
        assertTrue(generatedProto.contains("package test;"));
        assertTrue(generatedProto.contains("message Person"));
        assertTrue(generatedProto.contains("string name = 1;"));
        assertTrue(generatedProto.contains("int32 age = 2;"));
    }

    @Test
    public void testRoundTrip_WithOptions() throws Exception {
        String originalProto = "syntax = \"proto3\";\n" +
                "package io.apicurio.test;\n" +
                "\n" +
                "option java_package = \"io.apicurio.test\";\n" +
                "option java_outer_classname = \"TestProtos\";\n" +
                "option java_multiple_files = true;\n" +
                "\n" +
                "message UUID {\n" +
                "  int64 msb = 1;\n" +
                "  int64 lsb = 2;\n" +
                "}\n";

        FileDescriptor originalFd = FileDescriptorUtils.protoFileToFileDescriptor(
                originalProto, "test.proto", java.util.Optional.of("io.apicurio.test"),
                Collections.emptyMap(), Collections.emptyMap());

        String generatedProto = FileDescriptorToProtoConverter.convert(originalFd);

        FileDescriptor generatedFd = FileDescriptorUtils.protoFileToFileDescriptor(
                generatedProto, "test.proto", java.util.Optional.of("io.apicurio.test"),
                Collections.emptyMap(), Collections.emptyMap());

        // Verify options are preserved
        assertTrue(generatedProto.contains("option java_package = \"io.apicurio.test\";"));
        assertTrue(generatedProto.contains("option java_outer_classname = \"TestProtos\";"));
        assertTrue(generatedProto.contains("option java_multiple_files = true;"));

        // Verify the FileDescriptors have same options
        assertEquals(originalFd.getOptions().getJavaPackage(), generatedFd.getOptions().getJavaPackage());
        assertEquals(originalFd.getOptions().getJavaOuterClassname(), generatedFd.getOptions().getJavaOuterClassname());
        assertEquals(originalFd.getOptions().getJavaMultipleFiles(), generatedFd.getOptions().getJavaMultipleFiles());
    }

    @Test
    public void testRoundTrip_WithEnum() throws Exception {
        String originalProto = "syntax = \"proto3\";\n" +
                "package test;\n" +
                "\n" +
                "enum Status {\n" +
                "  UNKNOWN = 0;\n" +
                "  ACTIVE = 1;\n" +
                "  INACTIVE = 2;\n" +
                "}\n" +
                "\n" +
                "message Record {\n" +
                "  string id = 1;\n" +
                "  Status status = 2;\n" +
                "}\n";

        FileDescriptor originalFd = FileDescriptorUtils.protoFileToFileDescriptor(
                originalProto, "test.proto", java.util.Optional.of("test"),
                Collections.emptyMap(), Collections.emptyMap());

        String generatedProto = FileDescriptorToProtoConverter.convert(originalFd);

        FileDescriptor generatedFd = FileDescriptorUtils.protoFileToFileDescriptor(
                generatedProto, "test.proto", java.util.Optional.of("test"),
                Collections.emptyMap(), Collections.emptyMap());

        // Verify enum is preserved
        assertEquals(1, originalFd.getEnumTypes().size());
        assertEquals(1, generatedFd.getEnumTypes().size());

        var originalEnum = originalFd.findEnumTypeByName("Status");
        var generatedEnum = generatedFd.findEnumTypeByName("Status");
        assertNotNull(originalEnum);
        assertNotNull(generatedEnum);
        assertEquals(originalEnum.getValues().size(), generatedEnum.getValues().size());
    }

    @Test
    public void testRoundTrip_NestedMessage() throws Exception {
        String originalProto = "syntax = \"proto3\";\n" +
                "package test;\n" +
                "\n" +
                "message Outer {\n" +
                "  message Inner {\n" +
                "    string value = 1;\n" +
                "  }\n" +
                "  Inner inner = 1;\n" +
                "}\n";

        FileDescriptor originalFd = FileDescriptorUtils.protoFileToFileDescriptor(
                originalProto, "test.proto", java.util.Optional.of("test"),
                Collections.emptyMap(), Collections.emptyMap());

        String generatedProto = FileDescriptorToProtoConverter.convert(originalFd);

        FileDescriptor generatedFd = FileDescriptorUtils.protoFileToFileDescriptor(
                generatedProto, "test.proto", java.util.Optional.of("test"),
                Collections.emptyMap(), Collections.emptyMap());

        // Verify nested message structure
        var originalOuter = originalFd.findMessageTypeByName("Outer");
        var generatedOuter = generatedFd.findMessageTypeByName("Outer");
        assertNotNull(originalOuter);
        assertNotNull(generatedOuter);

        assertEquals(1, originalOuter.getNestedTypes().size());
        assertEquals(1, generatedOuter.getNestedTypes().size());

        var originalInner = originalOuter.findNestedTypeByName("Inner");
        var generatedInner = generatedOuter.findNestedTypeByName("Inner");
        assertNotNull(originalInner);
        assertNotNull(generatedInner);
        assertEquals(originalInner.getFields().size(), generatedInner.getFields().size());
    }

    /**
     * Critical test: Verify that the same FileDescriptor always generates the same .proto text.
     * This ensures consistent content hashing for auto-registration.
     */
    @Test
    public void testConsistentOutput() throws Exception {
        String originalProto = "syntax = \"proto3\";\n" +
                "package test;\n" +
                "\n" +
                "message Test {\n" +
                "  string field1 = 1;\n" +
                "  int32 field2 = 2;\n" +
                "  bool field3 = 3;\n" +
                "}\n";

        FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                originalProto, "test.proto", java.util.Optional.of("test"),
                Collections.emptyMap(), Collections.emptyMap());

        // Generate .proto text multiple times
        String output1 = FileDescriptorToProtoConverter.convert(fd);
        String output2 = FileDescriptorToProtoConverter.convert(fd);
        String output3 = FileDescriptorToProtoConverter.convert(fd);

        // Verify outputs are identical
        assertEquals(output1, output2, "Converter should produce identical output");
        assertEquals(output2, output3, "Converter should produce identical output");
    }

    @Test
    public void testRoundTrip_ReservedFields() throws Exception {
        String originalProto = "syntax = \"proto3\";\n" +
                "package test;\n" +
                "\n" +
                "message Reserved {\n" +
                "  reserved 2, 15, 9 to 11;\n" +
                "  reserved \"foo\", \"bar\";\n" +
                "  string name = 1;\n" +
                "}\n";

        FileDescriptor originalFd = FileDescriptorUtils.protoFileToFileDescriptor(
                originalProto, "test.proto", java.util.Optional.of("test"),
                Collections.emptyMap(), Collections.emptyMap());

        String generatedProto = FileDescriptorToProtoConverter.convert(originalFd);

        // Verify reserved fields are present
        assertTrue(generatedProto.contains("reserved"));

        FileDescriptor generatedFd = FileDescriptorUtils.protoFileToFileDescriptor(
                generatedProto, "test.proto", java.util.Optional.of("test"),
                Collections.emptyMap(), Collections.emptyMap());

        var originalMsg = originalFd.findMessageTypeByName("Reserved");
        var generatedMsg = generatedFd.findMessageTypeByName("Reserved");
        assertNotNull(originalMsg);
        assertNotNull(generatedMsg);

        // Verify reserved ranges using toProto()
        var originalProto2 = originalMsg.toProto();
        var generatedProto2 = generatedMsg.toProto();
        assertEquals(originalProto2.getReservedRangeCount(), generatedProto2.getReservedRangeCount());
        assertEquals(originalProto2.getReservedNameCount(), generatedProto2.getReservedNameCount());
    }
}
