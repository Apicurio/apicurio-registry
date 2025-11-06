package io.apicurio.registry.rules.compatibility.protobuf;

import io.apicurio.registry.protobuf.ProtobufDifference;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    // ========== Reserved Fields Tests ==========

    /**
     * Tests that using a previously reserved field name is detected as incompatible.
     */
    @Test
    public void testUsingReservedFieldName() {
        String schemaBefore = """
            syntax = "proto3";
            package test;

            message MyMessage {
                reserved "old_field";
                string current_field = 2;
            }
        """;

        String schemaAfter = """
            syntax = "proto3";
            package test;

            message MyMessage {
                string old_field = 3;  // Using previously reserved name
                string current_field = 2;
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertFalse(differences.isEmpty(), "Using a reserved field name should be incompatible");
        assertTrue(differences.stream().anyMatch(d -> d.getMessage().contains("reserved")),
                "Expected error about reserved fields. Found: " + differences);
    }

    /**
     * Tests that using a previously reserved field ID is detected as incompatible.
     */
    @Test
    public void testUsingReservedFieldId() {
        String schemaBefore = """
            syntax = "proto3";
            package test;

            message MyMessage {
                reserved 1, 5 to 10;
                string current_field = 2;
            }
        """;

        String schemaAfter = """
            syntax = "proto3";
            package test;

            message MyMessage {
                reserved 5 to 10;
                string new_field = 1;  // Using previously reserved ID
                string current_field = 2;
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertFalse(differences.isEmpty(), "Using a reserved field ID should be incompatible");
        assertTrue(differences.stream().anyMatch(d -> d.getMessage().contains("reserved")),
                "Expected error about reserved fields. Found: " + differences);
    }

    /**
     * Tests that removing a field without proper reservation is detected as incompatible.
     */
    @Test
    public void testRemovingFieldWithoutReservation() {
        String schemaBefore = """
            syntax = "proto3";
            package test;

            message MyMessage {
                string old_field = 1;
                string kept_field = 2;
            }
        """;

        String schemaAfter = """
            syntax = "proto3";
            package test;

            message MyMessage {
                string kept_field = 2;
                // old_field removed but not reserved!
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertFalse(differences.isEmpty(), "Removing field without reservation should be incompatible");
        assertTrue(differences.stream().anyMatch(d -> d.getMessage().contains("removed without reservation")),
                "Expected error about field removed without reservation. Found: " + differences);
    }

    /**
     * Tests that properly removing a field with reservation is allowed.
     */
    @Test
    public void testRemovingFieldWithReservation() {
        String schemaBefore = """
            syntax = "proto3";
            package test;

            message MyMessage {
                string old_field = 1;
                string kept_field = 2;
            }
        """;

        String schemaAfter = """
            syntax = "proto3";
            package test;

            message MyMessage {
                reserved 1, "old_field";
                string kept_field = 2;
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertTrue(differences.isEmpty(),
                "Properly reserving a removed field should be compatible. Found: " + differences);
    }

    // ========== Field ID/Tag Changes Tests ==========

    /**
     * Tests that changing a field's ID is detected as incompatible.
     */
    @Test
    public void testChangingFieldIdIsIncompatible() {
        String schemaBefore = """
            syntax = "proto3";
            package test;

            message MyMessage {
                string name = 1;
                int32 age = 2;
            }
        """;

        String schemaAfter = """
            syntax = "proto3";
            package test;

            message MyMessage {
                string name = 3;  // Changed ID from 1 to 3
                int32 age = 2;
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertFalse(differences.isEmpty(), "Changing field ID should be incompatible");
        assertTrue(differences.stream().anyMatch(d -> d.getMessage().contains("field id changed")),
                "Expected error about field id changed. Found: " + differences);
    }

    /**
     * Tests that changing an enum constant's tag is detected as incompatible.
     */
    @Test
    public void testChangingEnumConstantTag() {
        String schemaBefore = """
            syntax = "proto3";
            package test;

            enum Status {
                UNKNOWN = 0;
                ACTIVE = 1;
                INACTIVE = 2;
            }
        """;

        String schemaAfter = """
            syntax = "proto3";
            package test;

            enum Status {
                UNKNOWN = 0;
                ACTIVE = 5;  // Changed tag from 1 to 5
                INACTIVE = 2;
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertFalse(differences.isEmpty(), "Changing enum constant tag should be incompatible");
        assertTrue(differences.stream().anyMatch(d -> d.getMessage().contains("field id changed")),
                "Expected error about field id changed. Found: " + differences);
    }

    // ========== Field Type Changes Tests ==========

    /**
     * Tests that changing a field's type is detected as incompatible.
     */
    @Test
    public void testIncompatibleTypeChange() {
        String schemaBefore = """
            syntax = "proto3";
            package test;

            message MyMessage {
                string name = 1;
                int32 count = 2;
            }
        """;

        String schemaAfter = """
            syntax = "proto3";
            package test;

            message MyMessage {
                string name = 1;
                string count = 2;  // Changed from int32 to string
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertFalse(differences.isEmpty(), "Changing field type should be incompatible");
        assertTrue(differences.stream().anyMatch(d -> d.getMessage().contains("Field type changed")),
                "Expected error about field type changed. Found: " + differences);
    }

    /**
     * Tests that changing a field's label is detected as incompatible.
     */
    @Test
    public void testFieldLabelChange() {
        String schemaBefore = """
            syntax = "proto3";
            package test;

            message MyMessage {
                string name = 1;
                repeated string tags = 2;
            }
        """;

        String schemaAfter = """
            syntax = "proto3";
            package test;

            message MyMessage {
                string name = 1;
                string tags = 2;  // Changed from repeated to singular
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertFalse(differences.isEmpty(), "Changing field label should be incompatible");
        assertTrue(differences.stream().anyMatch(d -> d.getMessage().contains("Field label changed")),
                "Expected error about field label changed. Found: " + differences);
    }

    // ========== Field Name Changes Tests ==========

    /**
     * Tests that renaming a field (with same ID) is detected as incompatible.
     */
    @Test
    public void testRenamingField() {
        String schemaBefore = """
            syntax = "proto3";
            package test;

            message MyMessage {
                string old_name = 1;
                int32 count = 2;
            }
        """;

        String schemaAfter = """
            syntax = "proto3";
            package test;

            message MyMessage {
                string new_name = 1;  // Renamed from old_name
                int32 count = 2;
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertFalse(differences.isEmpty(), "Renaming a field should be incompatible");
        assertTrue(differences.stream().anyMatch(d -> d.getMessage().contains("Field name changed")),
                "Expected error about field name changed. Found: " + differences);
    }

    /**
     * Tests that renaming an enum constant is detected as incompatible.
     */
    @Test
    public void testRenamingEnumConstant() {
        String schemaBefore = """
            syntax = "proto3";
            package test;

            enum Status {
                UNKNOWN = 0;
                OLD_STATUS = 1;
            }
        """;

        String schemaAfter = """
            syntax = "proto3";
            package test;

            enum Status {
                UNKNOWN = 0;
                NEW_STATUS = 1;  // Renamed from OLD_STATUS
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertFalse(differences.isEmpty(), "Renaming an enum constant should be incompatible");
        assertTrue(differences.stream().anyMatch(d -> d.getMessage().contains("Field name changed")),
                "Expected error about field name changed. Found: " + differences);
    }

    // ========== Service RPC Tests ==========

    /**
     * Tests that removing an RPC method is detected as incompatible.
     */
    @Test
    public void testRemovingRpcMethod() {
        String schemaBefore = """
            syntax = "proto3";
            package test;

            message Request {}
            message Response {}

            service MyService {
                rpc GetData(Request) returns (Response);
                rpc DeleteData(Request) returns (Response);
            }
        """;

        String schemaAfter = """
            syntax = "proto3";
            package test;

            message Request {}
            message Response {}

            service MyService {
                rpc GetData(Request) returns (Response);
                // DeleteData removed
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertFalse(differences.isEmpty(), "Removing an RPC method should be incompatible");
        assertTrue(differences.stream().anyMatch(d -> d.getMessage().contains("rpc services removed")),
                "Expected error about RPC services removed. Found: " + differences);
    }

    /**
     * Tests that changing an RPC signature is detected as incompatible.
     */
    @Test
    public void testChangingRpcSignature() {
        String schemaBefore = """
            syntax = "proto3";
            package test;

            message Request {}
            message Response {}
            message NewRequest {}

            service MyService {
                rpc GetData(Request) returns (Response);
            }
        """;

        String schemaAfter = """
            syntax = "proto3";
            package test;

            message Request {}
            message Response {}
            message NewRequest {}

            service MyService {
                rpc GetData(NewRequest) returns (Response);  // Changed request type
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertFalse(differences.isEmpty(), "Changing RPC signature should be incompatible");
        assertTrue(differences.stream().anyMatch(d -> d.getMessage().contains("rpc service signature changed")),
                "Expected error about RPC signature changed. Found: " + differences);
    }

    /**
     * Tests that changing streaming mode is detected as incompatible.
     */
    @Test
    public void testChangingStreamingMode() {
        String schemaBefore = """
            syntax = "proto3";
            package test;

            message Request {}
            message Response {}

            service MyService {
                rpc StreamData(Request) returns (Response);
            }
        """;

        String schemaAfter = """
            syntax = "proto3";
            package test;

            message Request {}
            message Response {}

            service MyService {
                rpc StreamData(Request) returns (stream Response);  // Added streaming
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertFalse(differences.isEmpty(), "Changing streaming mode should be incompatible");
        assertTrue(differences.stream().anyMatch(d -> d.getMessage().contains("rpc service signature changed")),
                "Expected error about RPC signature changed. Found: " + differences);
    }

    // ========== Proto2 Required Fields Tests ==========

    /**
     * Tests that adding a required field in proto2 is detected as incompatible.
     */
    @Test
    public void testAddingRequiredFieldProto2() {
        String schemaBefore = """
            syntax = "proto2";
            package test;

            message MyMessage {
                optional string name = 1;
            }
        """;

        String schemaAfter = """
            syntax = "proto2";
            package test;

            message MyMessage {
                optional string name = 1;
                required string email = 2;  // Adding required field
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertFalse(differences.isEmpty(), "Adding a required field in proto2 should be incompatible");
        assertTrue(differences.stream().anyMatch(d -> d.getMessage().contains("required field added")),
                "Expected error about required field added. Found: " + differences);
    }

    /**
     * Tests that adding an optional field in proto2 is compatible.
     */
    @Test
    public void testAddingOptionalFieldProto2() {
        String schemaBefore = """
            syntax = "proto2";
            package test;

            message MyMessage {
                optional string name = 1;
            }
        """;

        String schemaAfter = """
            syntax = "proto2";
            package test;

            message MyMessage {
                optional string name = 1;
                optional string email = 2;  // Adding optional field
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertTrue(differences.isEmpty(),
                "Adding an optional field in proto2 should be compatible. Found: " + differences);
    }

    // ========== Nested Type Edge Cases Tests ==========

    /**
     * Tests parent scope resolution for nested types as per Protobuf scoping rules.
     * According to Protobuf name resolution, when inside ParentTwo, a reference to
     * NestedType should search parent scopes: ParentOne.ParentTwo.NestedType, then
     * ParentOne.NestedType (which exists).
     *
     * This test is based on the scenario from issue #6835.
     */
    @Test
    public void testParentScopeResolution() {
        String schema1 = """
            syntax = "proto3";
            package test;

            message ParentOne {
                message NestedType {
                    uint64 some_value = 1;
                }
                message ParentTwo {
                    NestedType nested = 1;  // Should resolve to ParentOne.NestedType
                }
            }
        """;

        String schema2 = """
            syntax = "proto3";
            package test;

            message ParentOne {
                message NestedType {
                    uint64 some_value = 1;
                }
                message ParentTwo {
                    .test.ParentOne.NestedType nested = 1;  // Fully qualified
                }
            }
        """;

        ProtobufFile file1 = new ProtobufFile(schema1);
        ProtobufFile file2 = new ProtobufFile(schema2);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(file1,
                file2);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertTrue(differences.isEmpty(),
                "Nested type should resolve to parent scope (ParentOne.NestedType) per Protobuf scoping rules. Found: "
                        + differences);
    }

    /**
     * Tests deeply nested types (3+ levels) with qualified and unqualified references.
     */
    @Test
    public void testDeeplyNestedTypes() {
        String schema1 = """
            syntax = "proto3";
            package test;

            message Level1 {
                message Level2 {
                    message Level3 {
                        string value = 1;
                    }
                    Level3 field = 1;
                }
                Level2 field = 1;
            }
        """;

        String schema2 = """
            syntax = "proto3";
            package test;

            message Level1 {
                message Level2 {
                    message Level3 {
                        string value = 1;
                    }
                    .test.Level1.Level2.Level3 field = 1;  // Fully qualified
                }
                .test.Level1.Level2 field = 1;  // Fully qualified
            }
        """;

        ProtobufFile file1 = new ProtobufFile(schema1);
        ProtobufFile file2 = new ProtobufFile(schema2);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(file1,
                file2);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertTrue(differences.isEmpty(),
                "Deeply nested types with different qualifications should be compatible. Found: " + differences);
    }

    /**
     * Tests schemas without package declarations handle type normalization correctly.
     */
    @Test
    public void testNestedTypeWithoutPackage() {
        String schema1 = """
            syntax = "proto3";

            message Root {
                message Nested {
                    string value = 1;
                }
                Nested field = 1;
            }
        """;

        String schema2 = """
            syntax = "proto3";

            message Root {
                message Nested {
                    string value = 1;
                }
                .Root.Nested field = 1;  // Fully qualified without package
            }
        """;

        ProtobufFile file1 = new ProtobufFile(schema1);
        ProtobufFile file2 = new ProtobufFile(schema2);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(file1,
                file2);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertTrue(differences.isEmpty(),
                "Schemas without package should handle qualified types correctly. Found: " + differences);
    }

    /**
     * Tests referencing top-level types with unqualified and qualified forms.
     */
    @Test
    public void testTopLevelTypeReferences() {
        String schema1 = """
            syntax = "proto3";
            package test;

            message Address {
                string street = 1;
            }

            message Person {
                Address address = 1;  // Unqualified reference to top-level type
            }
        """;

        String schema2 = """
            syntax = "proto3";
            package test;

            message Address {
                string street = 1;
            }

            message Person {
                .test.Address address = 1;  // Fully qualified reference
            }
        """;

        ProtobufFile file1 = new ProtobufFile(schema1);
        ProtobufFile file2 = new ProtobufFile(schema2);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(file1,
                file2);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertTrue(differences.isEmpty(),
                "Top-level type references should be compatible regardless of qualification. Found: "
                        + differences);
    }

    // ========== Enum Compatibility Tests ==========

    /**
     * Tests that adding an enum value is forward compatible.
     */
    @Test
    public void testAddingEnumValue() {
        String schemaBefore = """
            syntax = "proto3";
            package test;

            enum Status {
                UNKNOWN = 0;
                ACTIVE = 1;
            }
        """;

        String schemaAfter = """
            syntax = "proto3";
            package test;

            enum Status {
                UNKNOWN = 0;
                ACTIVE = 1;
                PENDING = 2;  // New enum value
            }
        """;

        ProtobufFile fileBefore = new ProtobufFile(schemaBefore);
        ProtobufFile fileAfter = new ProtobufFile(schemaAfter);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertTrue(differences.isEmpty(), "Adding an enum value should be compatible. Found: " + differences);
    }

    /**
     * Tests referencing a nested enum with qualified and unqualified forms.
     */
    @Test
    public void testNestedEnumReferences() {
        String schema1 = """
            syntax = "proto3";
            package test;

            message MyMessage {
                enum Status {
                    UNKNOWN = 0;
                    ACTIVE = 1;
                }
                Status status = 1;  // Unqualified
            }
        """;

        String schema2 = """
            syntax = "proto3";
            package test;

            message MyMessage {
                enum Status {
                    UNKNOWN = 0;
                    ACTIVE = 1;
                }
                .test.MyMessage.Status status = 1;  // Fully qualified
            }
        """;

        ProtobufFile file1 = new ProtobufFile(schema1);
        ProtobufFile file2 = new ProtobufFile(schema2);

        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(file1,
                file2);
        List<ProtobufDifference> differences = checker.findDifferences();

        assertTrue(differences.isEmpty(),
                "Nested enum references should be compatible regardless of qualification. Found: "
                        + differences);
    }
}