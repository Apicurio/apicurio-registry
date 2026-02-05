package io.apicurio.registry.rules.compatibility.protobuf;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.protobuf.rules.compatibility.ProtobufCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityDifference;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ProtobufCompatibilityChecker to verify that detailed error messages are returned to clients
 * instead of generic messages.
 */
public class ProtobufCompatibilityCheckerTest {

    private final ProtobufCompatibilityChecker checker = new ProtobufCompatibilityChecker();

    private TypedContent toTypedContent(String schema) {
        return TypedContent.create(ContentHandle.create(schema), ContentTypes.APPLICATION_PROTOBUF);
    }

    @Test
    public void testBackwardCompatibility_FieldIdChanged_ReturnsDetailedError() {
        String existingSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 1;
                    string firstName = 2;
                    string lastName = 3;
                }
                """;

        String proposedSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 5;
                    string firstName = 2;
                    string lastName = 3;
                }
                """;

        TypedContent existing = toTypedContent(existingSchema);
        TypedContent proposed = toTypedContent(proposedSchema);

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(existing), proposed, Collections.emptyMap());

        assertFalse(result.isCompatible(), "Schema should be incompatible");
        Set<CompatibilityDifference> differences = result.getIncompatibleDifferences();
        assertFalse(differences.isEmpty(), "Should have differences");

        boolean hasDetailedMessage = differences.stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("field id changed"));
        assertTrue(hasDetailedMessage,
                "Should contain detailed error about field id change. Found: " + differences);
    }

    @Test
    public void testBackwardCompatibility_FieldTypeChanged_ReturnsDetailedError() {
        String existingSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 1;
                    string name = 2;
                }
                """;

        String proposedSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 1;
                    int64 name = 2;
                }
                """;

        TypedContent existing = toTypedContent(existingSchema);
        TypedContent proposed = toTypedContent(proposedSchema);

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(existing), proposed, Collections.emptyMap());

        assertFalse(result.isCompatible(), "Schema should be incompatible");
        Set<CompatibilityDifference> differences = result.getIncompatibleDifferences();
        assertFalse(differences.isEmpty(), "Should have differences");

        boolean hasDetailedMessage = differences.stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("Field type changed"));
        assertTrue(hasDetailedMessage,
                "Should contain detailed error about field type change. Found: " + differences);
    }

    @Test
    public void testBackwardCompatibility_ContextPath_IsExtracted() {
        String existingSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 1;
                }
                """;

        String proposedSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 5;
                }
                """;

        TypedContent existing = toTypedContent(existingSchema);
        TypedContent proposed = toTypedContent(proposedSchema);

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(existing), proposed, Collections.emptyMap());

        assertFalse(result.isCompatible(), "Schema should be incompatible");
        Set<CompatibilityDifference> differences = result.getIncompatibleDifferences();

        boolean hasCorrectContext = differences.stream()
                .anyMatch(d -> d.asRuleViolation().getContext().equals("/Person"));
        assertTrue(hasCorrectContext,
                "Context should be extracted from message name. Found: " + differences);
    }

    @Test
    public void testForwardCompatibility_ReturnsDetailedError() {
        String existingSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 1;
                    string name = 2;
                }
                """;

        String proposedSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 5;
                    string name = 2;
                }
                """;

        TypedContent existing = toTypedContent(existingSchema);
        TypedContent proposed = toTypedContent(proposedSchema);

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.FORWARD,
                List.of(existing), proposed, Collections.emptyMap());

        assertFalse(result.isCompatible(), "Schema should be incompatible");
        Set<CompatibilityDifference> differences = result.getIncompatibleDifferences();
        assertFalse(differences.isEmpty(), "Should have differences");

        boolean hasDetailedMessage = differences.stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("field id changed"));
        assertTrue(hasDetailedMessage,
                "Should contain detailed error about field id change. Found: " + differences);
    }

    @Test
    public void testFullCompatibility_ReturnsDetailedError() {
        String existingSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 1;
                    string name = 2;
                }
                """;

        String proposedSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 5;
                    string name = 2;
                }
                """;

        TypedContent existing = toTypedContent(existingSchema);
        TypedContent proposed = toTypedContent(proposedSchema);

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.FULL,
                List.of(existing), proposed, Collections.emptyMap());

        assertFalse(result.isCompatible(), "Schema should be incompatible");
        Set<CompatibilityDifference> differences = result.getIncompatibleDifferences();
        assertFalse(differences.isEmpty(), "Should have differences");

        boolean hasDetailedMessage = differences.stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("field id changed"));
        assertTrue(hasDetailedMessage,
                "Should contain detailed error about field id change. Found: " + differences);
    }

    @Test
    public void testBackwardTransitive_ReturnsDetailedErrors() {
        String schema1 = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 1;
                    string name = 2;
                }
                """;

        String schema2 = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 1;
                    string name = 2;
                    string email = 3;
                }
                """;

        String proposedSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 5;
                    string name = 2;
                    string email = 3;
                }
                """;

        TypedContent existing1 = toTypedContent(schema1);
        TypedContent existing2 = toTypedContent(schema2);
        TypedContent proposed = toTypedContent(proposedSchema);

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD_TRANSITIVE,
                List.of(existing1, existing2), proposed, Collections.emptyMap());

        assertFalse(result.isCompatible(), "Schema should be incompatible");
        Set<CompatibilityDifference> differences = result.getIncompatibleDifferences();
        assertFalse(differences.isEmpty(), "Should have differences");

        boolean hasDetailedMessage = differences.stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("field id changed"));
        assertTrue(hasDetailedMessage,
                "Should contain detailed error about field id change. Found: " + differences);
    }

    @Test
    public void testCompatibleSchemas_ReturnsNoDifferences() {
        String existingSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 1;
                    string name = 2;
                }
                """;

        String proposedSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 1;
                    string name = 2;
                    string email = 3;
                }
                """;

        TypedContent existing = toTypedContent(existingSchema);
        TypedContent proposed = toTypedContent(proposedSchema);

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(existing), proposed, Collections.emptyMap());

        assertTrue(result.isCompatible(), "Schema should be compatible");
        Set<CompatibilityDifference> differences = result.getIncompatibleDifferences();
        assertTrue(differences.isEmpty(), "Should have no differences for compatible schemas");
    }

    @Test
    public void testMultipleIncompatibilities_ReturnsAllErrors() {
        String existingSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 1;
                    string name = 2;
                    string email = 3;
                }
                """;

        String proposedSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 5;
                    int64 name = 2;
                    string email = 3;
                }
                """;

        TypedContent existing = toTypedContent(existingSchema);
        TypedContent proposed = toTypedContent(proposedSchema);

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(existing), proposed, Collections.emptyMap());

        assertFalse(result.isCompatible(), "Schema should be incompatible");
        Set<CompatibilityDifference> differences = result.getIncompatibleDifferences();
        assertTrue(differences.size() >= 2,
                "Should report multiple incompatibilities. Found: " + differences.size());

        boolean hasFieldIdError = differences.stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("field id changed"));
        boolean hasFieldTypeError = differences.stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("Field type changed"));

        assertTrue(hasFieldIdError, "Should have field id change error");
        assertTrue(hasFieldTypeError, "Should have field type change error");
    }

    @Test
    public void testRpcSignatureChanged_ReturnsDetailedError() {
        String existingSchema = """
                syntax = "proto3";
                package com.example;

                message Request {}
                message Response {}
                message NewRequest {}

                service MyService {
                    rpc GetData(Request) returns (Response);
                }
                """;

        String proposedSchema = """
                syntax = "proto3";
                package com.example;

                message Request {}
                message Response {}
                message NewRequest {}

                service MyService {
                    rpc GetData(NewRequest) returns (Response);
                }
                """;

        TypedContent existing = toTypedContent(existingSchema);
        TypedContent proposed = toTypedContent(proposedSchema);

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(existing), proposed, Collections.emptyMap());

        assertFalse(result.isCompatible(), "Schema should be incompatible");
        Set<CompatibilityDifference> differences = result.getIncompatibleDifferences();
        assertFalse(differences.isEmpty(), "Should have differences");

        boolean hasRpcError = differences.stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("rpc service signature changed"));
        assertTrue(hasRpcError,
                "Should contain detailed error about RPC signature change. Found: " + differences);
    }

    @Test
    public void testFieldNameChanged_ReturnsDetailedError() {
        String existingSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 1;
                    string old_name = 2;
                }
                """;

        String proposedSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 1;
                    string new_name = 2;
                }
                """;

        TypedContent existing = toTypedContent(existingSchema);
        TypedContent proposed = toTypedContent(proposedSchema);

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(existing), proposed, Collections.emptyMap());

        assertFalse(result.isCompatible(), "Schema should be incompatible");
        Set<CompatibilityDifference> differences = result.getIncompatibleDifferences();
        assertFalse(differences.isEmpty(), "Should have differences");

        boolean hasFieldNameError = differences.stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("Field name changed"));
        assertTrue(hasFieldNameError,
                "Should contain detailed error about field name change. Found: " + differences);
    }

    @Test
    public void testEmptyExistingSchemas_ReturnsCompatible() {
        String proposedSchema = """
                syntax = "proto3";
                package com.example;

                message Person {
                    int32 id = 1;
                }
                """;

        TypedContent proposed = toTypedContent(proposedSchema);

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                Collections.emptyList(), proposed, Collections.emptyMap());

        assertTrue(result.isCompatible(),
                "Should be compatible when there are no existing schemas");
    }
}
