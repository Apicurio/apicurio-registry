package io.apicurio.registry.flink.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SchemaCompatibilityChecker.
 */
class SchemaCompatibilityCheckerTest {

    private static final String BASE_SCHEMA = "{"
            + "\"type\":\"record\","
            + "\"name\":\"TestRecord\","
            + "\"fields\":["
            + "{\"name\":\"id\",\"type\":\"long\"},"
            + "{\"name\":\"name\",\"type\":\"string\"}"
            + "]}";

    private static final String ADDED_OPTIONAL = "{"
            + "\"type\":\"record\","
            + "\"name\":\"TestRecord\","
            + "\"fields\":["
            + "{\"name\":\"id\",\"type\":\"long\"},"
            + "{\"name\":\"name\",\"type\":\"string\"},"
            + "{\"name\":\"email\",\"type\":[\"null\",\"string\"],"
            + "\"default\":null}"
            + "]}";

    private static final String REMOVED_FIELD = "{"
            + "\"type\":\"record\","
            + "\"name\":\"TestRecord\","
            + "\"fields\":["
            + "{\"name\":\"id\",\"type\":\"long\"}"
            + "]}";

    private static final String CHANGED_TYPE = "{"
            + "\"type\":\"record\","
            + "\"name\":\"TestRecord\","
            + "\"fields\":["
            + "{\"name\":\"id\",\"type\":\"string\"},"
            + "{\"name\":\"name\",\"type\":\"string\"}"
            + "]}";

    @Test
    void testBackwardCompatibleAddOptionalField() {
        final SchemaCompatibilityChecker checker = SchemaCompatibilityChecker.backward();

        final CompatibilityResult result = checker.checkAvroCompatibility(BASE_SCHEMA, ADDED_OPTIONAL);

        assertTrue(result.isCompatible());
        assertEquals(
                CompatibilityResult.Status.COMPATIBLE,
                result.getStatus());
    }

    @Test
    void testBackwardCompatibleRemovedField() {
        // BACKWARD: new reader can read old data.
        // Removing field is OK - reader ignores extra fields in old data.
        final SchemaCompatibilityChecker checker = SchemaCompatibilityChecker.backward();

        final CompatibilityResult result = checker.checkAvroCompatibility(BASE_SCHEMA, REMOVED_FIELD);

        assertTrue(result.isCompatible());
    }

    @Test
    void testBackwardIncompatibleChangedType() {
        final SchemaCompatibilityChecker checker = SchemaCompatibilityChecker.backward();

        final CompatibilityResult result = checker.checkAvroCompatibility(BASE_SCHEMA, CHANGED_TYPE);

        assertFalse(result.isCompatible());
    }

    @Test
    void testForwardIncompatibleRemovedField() {
        // FORWARD: old reader can read new data.
        // Removing field is NOT OK - old reader expects field.
        final SchemaCompatibilityChecker checker = SchemaCompatibilityChecker.forward();

        final CompatibilityResult result = checker.checkAvroCompatibility(BASE_SCHEMA, REMOVED_FIELD);

        assertFalse(result.isCompatible());
    }

    @Test
    void testFullCompatibilityRequiresBoth() {
        final SchemaCompatibilityChecker checker = SchemaCompatibilityChecker.full();

        // Adding optional field is fully compatible
        final CompatibilityResult addResult = checker.checkAvroCompatibility(BASE_SCHEMA, ADDED_OPTIONAL);
        assertTrue(addResult.isCompatible());

        // Removing field fails FORWARD check
        final CompatibilityResult removeResult = checker.checkAvroCompatibility(BASE_SCHEMA, REMOVED_FIELD);
        assertFalse(removeResult.isCompatible());
    }

    @Test
    void testNoneModeAlwaysCompatible() {
        final SchemaCompatibilityChecker checker = new SchemaCompatibilityChecker(CompatibilityMode.NONE);

        final CompatibilityResult result = checker.checkAvroCompatibility(BASE_SCHEMA, CHANGED_TYPE);

        assertTrue(result.isCompatible());
    }

    @Test
    void testCompatibilityResultDetails() {
        // Type change is incompatible for BACKWARD
        final SchemaCompatibilityChecker checker = SchemaCompatibilityChecker.backward();

        final CompatibilityResult result = checker.checkAvroCompatibility(BASE_SCHEMA, CHANGED_TYPE);

        assertFalse(result.isCompatible());
        assertFalse(result.getDifferences().isEmpty());
    }
}
