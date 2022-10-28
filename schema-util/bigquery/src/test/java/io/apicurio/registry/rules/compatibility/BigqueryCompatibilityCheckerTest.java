package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

class BigqueryCompatibilityCheckerTest {

    private final BigqueryCompatibilityChecker bigqueryCompatibilityChecker = new BigqueryCompatibilityChecker();

    @Test
    void testBackwardCompatibility() throws IOException {
        try (InputStream proposed = getClass().getClassLoader().getResourceAsStream("extendedschema.json");
                InputStream existing = getClass().getClassLoader().getResourceAsStream("validschema.json")) {
            CompatibilityExecutionResult compatibilityExecutionResult = bigqueryCompatibilityChecker
                    .testCompatibility(CompatibilityLevel.BACKWARD,
                    List.of(ContentHandle.create(existing)), ContentHandle.create(proposed));
            assertTrue(compatibilityExecutionResult.isCompatible());
        }

    }

    @Test
    void testBackwardCompatibilityFalse() throws IOException {
        try (InputStream proposed = getClass().getClassLoader().getResourceAsStream("reorderedschema.json");
                InputStream existing = getClass().getClassLoader().getResourceAsStream("validschema.json")) {
            CompatibilityExecutionResult compatibilityExecutionResult = bigqueryCompatibilityChecker
                    .testCompatibility(CompatibilityLevel.BACKWARD,
                    List.of(ContentHandle.create(existing)), ContentHandle.create(proposed));
            assertFalse(compatibilityExecutionResult.isCompatible());
            assertTrue(compatibilityExecutionResult.getIncompatibleDifferences().iterator().next()
                    .asRuleViolation().getDescription().contains("does not match name"));
        }

    }

    @Test
    void testForwardCompatibility() throws IOException {
        try (InputStream proposed = getClass().getClassLoader().getResourceAsStream("validschema.json");
             InputStream existing = getClass().getClassLoader().getResourceAsStream("shortenedschema.json")) {
            CompatibilityExecutionResult compatibilityExecutionResult = bigqueryCompatibilityChecker
                    .testCompatibility(CompatibilityLevel.BACKWARD,
                            List.of(ContentHandle.create(existing)), ContentHandle.create(proposed));
            assertTrue(compatibilityExecutionResult.isCompatible());
        }

    }

    @Test
    void testForwardCompatibilityFalse() throws IOException {
        try (InputStream proposed = getClass().getClassLoader().getResourceAsStream("shortenedschema.json");
             InputStream existing = getClass().getClassLoader().getResourceAsStream("validschema.json")) {
            CompatibilityExecutionResult compatibilityExecutionResult = bigqueryCompatibilityChecker
                    .testCompatibility(CompatibilityLevel.BACKWARD,
                            List.of(ContentHandle.create(existing)), ContentHandle.create(proposed));
            assertFalse(compatibilityExecutionResult.isCompatible());
            assertEquals("Not compatible with a schema which has more elements.",
                    compatibilityExecutionResult.getIncompatibleDifferences().iterator().next()
                            .asRuleViolation().getDescription());
        }

    }

    @Test
    void testForwardCompatibilityFalseType() throws IOException {
        try (InputStream proposed = getClass().getClassLoader().getResourceAsStream("typemodifiedschema.json");
             InputStream existing = getClass().getClassLoader().getResourceAsStream("validschema.json")) {
            CompatibilityExecutionResult compatibilityExecutionResult = bigqueryCompatibilityChecker
                    .testCompatibility(CompatibilityLevel.BACKWARD,
                            List.of(ContentHandle.create(existing)), ContentHandle.create(proposed));
            assertFalse(compatibilityExecutionResult.isCompatible());
            assertEquals("Type INTEGER of field numberOfYears does not match type STRING in other schema.",
                    compatibilityExecutionResult.getIncompatibleDifferences().iterator().next()
                            .asRuleViolation().getDescription());
        }

    }

}