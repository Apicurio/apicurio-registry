package io.apicurio.registry.flink.state;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavepointValidatorTest {

    private static final String BASE_SCHEMA = "{"
            + "\"type\":\"record\","
            + "\"name\":\"TestState\","
            + "\"fields\":["
            + "{\"name\":\"id\",\"type\":\"long\"},"
            + "{\"name\":\"name\",\"type\":\"string\"}"
            + "]}";

    private static final String COMPATIBLE_SCHEMA = "{"
            + "\"type\":\"record\","
            + "\"name\":\"TestState\","
            + "\"fields\":["
            + "{\"name\":\"id\",\"type\":\"long\"},"
            + "{\"name\":\"name\",\"type\":\"string\"},"
            + "{\"name\":\"email\",\"type\":[\"null\",\"string\"],"
            + "\"default\":null}"
            + "]}";

    @Test
    void testValidationResultCompatibilityConstants() {
        // Verify ValidationResult API exists and works
        final ValidationResult validResult = ValidationResult.valid(
                java.util.Collections.singletonList("op1"));
        assertTrue(validResult.isValid());
        assertNotNull(validResult.getCompatibleOperators());
        assertEquals(1, validResult.getCompatibleOperators().size());
    }

    @Test
    void testValidationResultInvalid() {
        final Map<String, String> errors = new HashMap<>();
        errors.put("op1", "Schema incompatible");

        final ValidationResult invalidResult = ValidationResult.invalid(
                java.util.Collections.emptyList(), errors);
        assertFalse(invalidResult.isValid());
        assertNotNull(invalidResult.getIncompatibleOperators());
    }

    @Test
    void testSchemaCompatibilityCheckerBackward() {
        final SchemaCompatibilityChecker checker = SchemaCompatibilityChecker.backward();

        // Adding optional field is backward compatible
        final CompatibilityResult result = checker.checkAvroCompatibility(BASE_SCHEMA, COMPATIBLE_SCHEMA);

        assertTrue(result.isCompatible());
    }

    @Test
    void testSchemaCompatibilityCheckerIncompatible() {
        final SchemaCompatibilityChecker checker = SchemaCompatibilityChecker.backward();

        final String incompatible = "{"
                + "\"type\":\"record\","
                + "\"name\":\"TestState\","
                + "\"fields\":["
                + "{\"name\":\"id\",\"type\":\"string\"},"
                + "{\"name\":\"name\",\"type\":\"string\"}"
                + "]}";

        final CompatibilityResult result = checker.checkAvroCompatibility(BASE_SCHEMA, incompatible);

        assertFalse(result.isCompatible());
    }

    @Test
    void testCompatibilityModeNoneAlwaysCompatible() {
        final SchemaCompatibilityChecker checker = new SchemaCompatibilityChecker(CompatibilityMode.NONE);

        final String incompatible = "{"
                + "\"type\":\"record\","
                + "\"name\":\"TestState\","
                + "\"fields\":["
                + "{\"name\":\"id\",\"type\":\"string\"}"
                + "]}";

        final CompatibilityResult result = checker.checkAvroCompatibility(BASE_SCHEMA, incompatible);

        assertTrue(result.isCompatible());
    }

    @Test
    void testCompatibilityResultApi() {
        final CompatibilityResult compatible = CompatibilityResult.compatible();

        assertTrue(compatible.isCompatible());
        assertEquals(CompatibilityResult.Status.COMPATIBLE, compatible.getStatus());
    }
}
