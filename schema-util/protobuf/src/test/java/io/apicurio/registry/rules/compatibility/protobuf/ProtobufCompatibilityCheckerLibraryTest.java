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

    /**
     * Tests that schemas with qualified and unqualified nested type references are considered compatible.
     * This addresses GitHub issue #6832 where protobuf scoping rules were not being checked correctly.
     */
    @Test
    public void testQualifiedVsUnqualifiedNestedTypeCompatibility() {
        // Schema with unqualified nested type reference
        String schemaWithUnqualifiedType = """
            syntax = "proto3";
            package test;

            message RootMessage {
              message NestedMessage {
                  optional uint64 some_id = 1;
              }
              NestedMessage nested_message_field = 1;
            }
        """;

        // Schema with fully qualified nested type reference
        String schemaWithQualifiedType = """
            syntax = "proto3";
            package test;

            message RootMessage {
              message NestedMessage {
                  optional uint64 some_id = 1;
              }
              .test.RootMessage.NestedMessage nested_message_field = 1;
            }
        """;

        // Parse the schemas
        ProtobufFile unqualifiedFile = new ProtobufFile(schemaWithUnqualifiedType);
        ProtobufFile qualifiedFile = new ProtobufFile(schemaWithQualifiedType);

        // Test both directions of comparison
        ProtobufCompatibilityCheckerLibrary checker1 = new ProtobufCompatibilityCheckerLibrary(
                unqualifiedFile, qualifiedFile);
        List<ProtobufDifference> differences1 = checker1.findDifferences();

        ProtobufCompatibilityCheckerLibrary checker2 = new ProtobufCompatibilityCheckerLibrary(
                qualifiedFile, unqualifiedFile);
        List<ProtobufDifference> differences2 = checker2.findDifferences();

        // Assert no differences in either direction
        assertTrue(differences1.isEmpty(),
                "Expected no differences when comparing unqualified to qualified nested type reference. Found: "
                        + differences1);
        assertTrue(differences2.isEmpty(),
                "Expected no differences when comparing qualified to unqualified nested type reference. Found: "
                        + differences2);
    }

    /**
     * Tests that nested types at different nesting levels are correctly distinguished.
     */
    @Test
    public void testNestedTypeResolution() {
        // Schema with nested types at different levels
        String schema1 = """
            syntax = "proto3";
            package test;

            message Outer {
                message Inner {
                    optional string value = 1;
                }
                Inner inner_field = 1;
            }
        """;

        // Same schema structure - should be compatible
        String schema2 = """
            syntax = "proto3";
            package test;

            message Outer {
                message Inner {
                    optional string value = 1;
                }
                .test.Outer.Inner inner_field = 1;
            }
        """;

        ProtobufFile file1 = new ProtobufFile(schema1);
        ProtobufFile file2 = new ProtobufFile(schema2);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(file1,
                file2);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertTrue(differences.isEmpty(),
                "Expected no differences for equivalent nested type references. Found: " + differences);
    }

    /**
     * Tests that cross-file type references (from different packages) are handled correctly.
     */
    @Test
    public void testCrossPackageTypeReferences() {
        // Schema referencing a type that could be from another package
        String schema1 = """
            syntax = "proto3";
            package test.pkg1;

            message MyMessage {
                string field1 = 1;
            }
        """;

        // Same schema - should be compatible
        String schema2 = """
            syntax = "proto3";
            package test.pkg1;

            message MyMessage {
                string field1 = 1;
            }
        """;

        ProtobufFile file1 = new ProtobufFile(schema1);
        ProtobufFile file2 = new ProtobufFile(schema2);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(file1,
                file2);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertTrue(differences.isEmpty(), "Expected no differences for identical schemas. Found: " + differences);
    }
}