package io.apicurio.registry.rules.compatibility.protobuf;

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
}