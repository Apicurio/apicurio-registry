package io.apicurio.registry.rules.compatibility.protobuf;

import io.apicurio.registry.protobuf.ProtobufDifference;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;


/**
 * Unit tests for ProtobufCompatibilityCheckerLibrary.
 */
public class ProtobufCompatibilityCheckerLibraryTest {

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