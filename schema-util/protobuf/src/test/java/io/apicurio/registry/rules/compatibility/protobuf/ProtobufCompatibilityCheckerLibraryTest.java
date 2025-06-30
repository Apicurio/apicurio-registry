package io.apicurio.registry.rules.compatibility.protobuf;

import io.apicurio.registry.protobuf.ProtobufDifference;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ProtobufCompatibilityCheckerLibrary.
 */
public class ProtobufCompatibilityCheckerLibraryTest {

    @Test
    public void testMapStringStringCompatibility() {
        // Define the old schema with a map<string, string>
        String oldSchema = """
            syntax = \"proto3\";

            message TestMap {
                map<string, string> plainData = 1;
            }
        """;

        // Define the new schema with the same map<string, string>
        String newSchema = """
            syntax = \"proto3\";

            message TestMap {
                map<string, string> plainData = 1;
            }
        """;

        // Parse the schemas
        ProtobufFile oldFile = new ProtobufFile(oldSchema);
        ProtobufFile newFile = new ProtobufFile(newSchema);

        // Perform compatibility check
        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(oldFile, newFile);
        List<?> differences = checker.findDifferences();

        // Assert no differences
        assertTrue(differences.isEmpty(), "Expected no differences for compatible map<string, string> schemas.");
    }

    @Test
    public void testAddingNewFieldIsForwardCompatible() {
        // Mock ProtobufFile instances for before and after schema versions
        ProtobufFile fileBefore = new ProtobufFile("syntax = \"proto3\";\n" + //
                        "\n" + //
                        "package com.example;\n" + //
                        "\n" + //
                        "message Person {\n" + //
                        "    int32 id = 1;\n" + //
                        "    string firstName = 2;\n" + //
                        "    string lastName = 3;\n" + //
                        "}");
        ProtobufFile fileAfter = new ProtobufFile("syntax = \"proto3\";\n" + //
                        "\n" + //
                        "package com.example;\n" + //
                        "\n" + //
                        "message Person {\n" + //
                        "    int32 id = 1;\n" + //
                        "    string firstName = 2;\n" + //
                        "    string lastName = 3;\n" + //
                        "    int32 addressId = 4;\n" + //
                        "}");

         ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore, fileAfter);

        List<ProtobufDifference> entries = checker.findDifferences();
        System.out.println(entries);

        assertTrue(checker.validate());
    }
}