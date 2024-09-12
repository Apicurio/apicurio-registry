package io.apicurio.registry.content.canon;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvroCompatibilityTest {
    private final ContentCanonicalizer avroCanonicalizer = new EnhancedAvroContentCanonicalizer();
    private final String schemaString1 = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":"
            + "[{\"type\":\"string\",\"name\":\"f1\"}]}";
    private final Schema schema1 = new Schema.Parser().parse(avroCanonicalizer.canonicalize(
            TypedContent.create(ContentHandle.create(schemaString1), ContentTypes.APPLICATION_JSON),
            new HashMap<>()).getContent().content());

    private final String schemaString2 = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":"
            + "[{\"type\":\"string\",\"name\":\"f1\"},"
            + " {\"type\":\"string\",\"name\":\"f2\", \"default\": \"foo\"}]}";
    private final Schema schema2 = new Schema.Parser().parse(avroCanonicalizer.canonicalize(
            TypedContent.create(ContentHandle.create(schemaString2), ContentTypes.APPLICATION_JSON),
            new HashMap<>()).getContent().content());

    private final String schemaString3 = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":"
            + "[{\"type\":\"string\",\"name\":\"f1\"}," + " {\"type\":\"string\",\"name\":\"f2\"}]}";
    private final Schema schema3 = new Schema.Parser().parse(avroCanonicalizer.canonicalize(
            TypedContent.create(ContentHandle.create(schemaString3), ContentTypes.APPLICATION_JSON),
            new HashMap<>()).getContent().content());

    private final String schemaString4 = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":"
            + "[{\"type\":\"string\",\"name\":\"f1_new\", \"aliases\": [\"f1\"]}]}";
    private final Schema schema4 = new Schema.Parser().parse(avroCanonicalizer.canonicalize(
            TypedContent.create(ContentHandle.create(schemaString4), ContentTypes.APPLICATION_JSON),
            new HashMap<>()).getContent().content());

    private final String schemaString6 = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":"
            + "[{\"type\":[\"null\", \"string\"],\"name\":\"f1\"," + " \"doc\":\"doc of f1\"}]}";
    private final Schema schema6 = new Schema.Parser().parse(avroCanonicalizer.canonicalize(
            TypedContent.create(ContentHandle.create(schemaString6), ContentTypes.APPLICATION_JSON),
            new HashMap<>()).getContent().content());

    private final String schemaString7 = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":"
            + "[{\"type\":[\"null\", \"string\", \"int\"],\"name\":\"f1\"," + " \"doc\":\"doc of f1\"}]}";
    private final Schema schema7 = new Schema.Parser().parse(avroCanonicalizer.canonicalize(
            TypedContent.create(ContentHandle.create(schemaString7), ContentTypes.APPLICATION_JSON),
            new HashMap<>()).getContent().content());

    private final String schemaString8 = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":"
            + "[{\"type\":\"string\",\"name\":\"f1\"},"
            + " {\"type\":\"string\",\"name\":\"f2\", \"default\": \"foo\"},"
            + " {\"type\":\"string\",\"name\":\"f3\", \"default\": \"bar\"}]}";
    private final Schema schema8 = new Schema.Parser().parse(avroCanonicalizer.canonicalize(
            TypedContent.create(ContentHandle.create(schemaString8), ContentTypes.APPLICATION_JSON),
            new HashMap<>()).getContent().content());

    /*
     * Backward compatibility: A new schema is backward compatible if it can be used to read the data written
     * in the previous schema.
     */
    @Test
    void testBasicBackwardsCompatibility() {
        AvroCompatibilityChecker checker = AvroCompatibilityChecker.BACKWARD_CHECKER;
        assertTrue(checker.isCompatible(schema2, Collections.singletonList(schema1)),
                "adding a field with default is a backward compatible change");
        assertFalse(checker.isCompatible(schema3, Collections.singletonList(schema1)),
                "adding a field w/o default is not a backward compatible change");
        assertFalse(checker.isCompatible(schema4, Collections.singletonList(schema1)),
                "changing field name is not a backward compatible change");
        assertTrue(checker.isCompatible(schema6, Collections.singletonList(schema1)),
                "evolving a field type to a union is a backward compatible change");
        assertFalse(checker.isCompatible(schema1, Collections.singletonList(schema6)),
                "removing a type from a union is not a backward compatible change");
        assertTrue(checker.isCompatible(schema7, Collections.singletonList(schema6)),
                "adding a new type in union is a backward compatible change");
        assertFalse(checker.isCompatible(schema6, Collections.singletonList(schema7)),
                "removing a type from a union is not a backward compatible change");

        // Only schema 2 is checked
        assertTrue(checker.isCompatible(schema3, Arrays.asList(schema1, schema2)),
                "removing a default is not a transitively compatible change");
    }

    /*
     * Backward transitive compatibility: A new schema is backward compatible if it can be used to read the
     * data written in all previous schemas.
     */
    @Test
    void testBasicBackwardsTransitiveCompatibility() {
        AvroCompatibilityChecker checker = AvroCompatibilityChecker.BACKWARD_TRANSITIVE_CHECKER;
        // All compatible
        assertTrue(checker.isCompatible(schema8, Arrays.asList(schema1, schema2)),
                "iteratively adding fields with defaults is a compatible change");

        // 1 == 2, 2 == 3, 3 != 1
        assertTrue(checker.isCompatible(schema2, Collections.singletonList(schema1)),
                "adding a field with default is a backward compatible change");
        assertTrue(checker.isCompatible(schema3, Collections.singletonList(schema2)),
                "removing a default is a compatible change, but not transitively");
        assertFalse(checker.isCompatible(schema3, Arrays.asList(schema2, schema1)),
                "removing a default is not a transitively compatible change");
    }

    /*
     * Forward compatibility: A new schema is forward compatible if the previous schema can read data written
     * in this schema.
     */
    @Test
    void testBasicForwardsCompatibility() {
        AvroCompatibilityChecker checker = AvroCompatibilityChecker.FORWARD_CHECKER;
        assertTrue(checker.isCompatible(schema2, Collections.singletonList(schema1)),
                "adding a field is a forward compatible change");
        assertTrue(checker.isCompatible(schema3, Collections.singletonList(schema1)),
                "adding a field is a forward compatible change");
        assertTrue(checker.isCompatible(schema3, Collections.singletonList(schema2)),
                "adding a field is a forward compatible change");
        assertTrue(checker.isCompatible(schema2, Collections.singletonList(schema3)),
                "adding a field is a forward compatible change");

        // Only schema 2 is checked
        assertTrue(checker.isCompatible(schema1, Arrays.asList(schema3, schema2)),
                "removing a default is not a transitively compatible change");
    }

    /*
     * Forward transitive compatibility: A new schema is forward compatible if all previous schemas can read
     * data written in this schema.
     */
    @Test
    void testBasicForwardsTransitiveCompatibility() {
        AvroCompatibilityChecker checker = AvroCompatibilityChecker.FORWARD_TRANSITIVE_CHECKER;
        // All compatible
        assertTrue(checker.isCompatible(schema1, Arrays.asList(schema8, schema2)),
                "iteratively removing fields with defaults is a compatible change");

        // 1 == 2, 2 == 3, 3 != 1
        assertTrue(checker.isCompatible(schema2, Collections.singletonList(schema3)),
                "adding default to a field is a compatible change");
        assertTrue(checker.isCompatible(schema1, Collections.singletonList(schema2)),
                "removing a field with a default is a compatible change");
        assertFalse(checker.isCompatible(schema1, Arrays.asList(schema2, schema3)),
                "removing a default is not a transitively compatible change");
    }

    /*
     * Full compatibility: A new schema is fully compatible if it’s both backward and forward compatible.
     */
    @Test
    void testBasicFullCompatibility() {
        AvroCompatibilityChecker checker = AvroCompatibilityChecker.FULL_CHECKER;
        assertTrue(checker.isCompatible(schema2, Collections.singletonList(schema1)),
                "adding a field with default is a backward and a forward compatible change");

        // Only schema 2 is checked!
        assertTrue(checker.isCompatible(schema3, Arrays.asList(schema1, schema2)),
                "transitively adding a field without a default is not a compatible change");
        // Only schema 2 is checked!
        assertTrue(checker.isCompatible(schema1, Arrays.asList(schema3, schema2)),
                "transitively removing a field without a default is not a compatible change");
    }

    /*
     * Full transitive compatibility: A new schema is fully compatible if it’s both transitively backward and
     * transitively forward compatible with the entire schema history.
     */
    @Test
    void testBasicFullTransitiveCompatibility() {
        AvroCompatibilityChecker checker = AvroCompatibilityChecker.FULL_TRANSITIVE_CHECKER;

        // Simple check
        assertTrue(checker.isCompatible(schema8, Arrays.asList(schema1, schema2)),
                "iteratively adding fields with defaults is a compatible change");
        assertTrue(checker.isCompatible(schema1, Arrays.asList(schema8, schema2)),
                "iteratively removing fields with defaults is a compatible change");

        assertTrue(checker.isCompatible(schema2, Collections.singletonList(schema3)),
                "adding default to a field is a compatible change");
        assertTrue(checker.isCompatible(schema1, Collections.singletonList(schema2)),
                "adding default to a field is a compatible change");

        assertTrue(checker.isCompatible(schema2, Collections.singletonList(schema1)),
                "adding a field with default is a compatible change");
        assertTrue(checker.isCompatible(schema3, Collections.singletonList(schema2)),
                "removing a default from a field compatible change");

        assertFalse(checker.isCompatible(schema3, Arrays.asList(schema2, schema1)),
                "transitively adding a field without a default is not a compatible change");
        assertFalse(checker.isCompatible(schema1, Arrays.asList(schema2, schema3)),
                "transitively removing a field without a default is not a compatible change");
    }

}
