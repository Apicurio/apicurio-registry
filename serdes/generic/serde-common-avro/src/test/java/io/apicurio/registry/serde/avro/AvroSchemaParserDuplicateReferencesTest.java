package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.data.Metadata;
import io.apicurio.registry.resolver.data.Record;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test to verify that AvroSchemaParser correctly handles schemas with duplicate references.
 * This reproduces the issue where Debezium sends schemas with multiple fields referencing
 * the same nested schema (e.g., multiple PostGIS Point geometry fields).
 */
public class AvroSchemaParserDuplicateReferencesTest {

    private AvroSchemaParser<GenericRecord> parser;
    private DefaultAvroDatumProvider<GenericRecord> datumProvider;

    @BeforeEach
    public void setup() {
        datumProvider = new DefaultAvroDatumProvider<>();
        datumProvider.configure(new AvroSerdeConfig(java.util.Collections.emptyMap()));
        parser = new AvroSchemaParser<>(datumProvider,
                (int) AvroSerdeConfig.AVRO_SCHEMA_CACHE_SIZE_DEFAULT);
    }

    /**
     * Test that verifies duplicate references are properly deduplicated when extracting
     * schema from data. This simulates the Debezium PostGIS Point geometry use case where
     * a table has multiple columns of the same nested type.
     */
    @Test
    public void testDuplicateReferencesAreDeduplicated() {
        // Create a nested Point schema (similar to io.debezium.data.geometry.Point)
        String pointSchemaJson = """
            {
              "type": "record",
              "name": "Point",
              "namespace": "io.debezium.data.geometry",
              "fields": [
                {"name": "x", "type": "double"},
                {"name": "y", "type": "double"}
              ]
            }
            """;

        // Use a single Schema.Parser instance to resolve references
        Schema.Parser schemaParser = new Schema.Parser();
        Schema pointSchema = schemaParser.parse(pointSchemaJson);

        // Create a main schema with MULTIPLE fields referencing the same Point schema
        // This is what causes duplicate references
        String mainSchemaJson = """
            {
              "type": "record",
              "name": "TableValue",
              "namespace": "io.example",
              "fields": [
                {"name": "id", "type": "int"},
                {"name": "start_point", "type": "io.debezium.data.geometry.Point"},
                {"name": "end_point", "type": "io.debezium.data.geometry.Point"}
              ]
            }
            """;
        Schema mainSchema = schemaParser.parse(mainSchemaJson);

        // Create sample data
        GenericRecord pointRecord1 = new GenericData.Record(pointSchema);
        pointRecord1.put("x", 1.0);
        pointRecord1.put("y", 2.0);

        GenericRecord pointRecord2 = new GenericData.Record(pointSchema);
        pointRecord2.put("x", 3.0);
        pointRecord2.put("y", 4.0);

        GenericRecord mainRecord = new GenericData.Record(mainSchema);
        mainRecord.put("id", 123);
        mainRecord.put("start_point", pointRecord1);
        mainRecord.put("end_point", pointRecord2);

        // Extract schema from data using the parser
        Record<GenericRecord> record = new Record<GenericRecord>() {
            @Override
            public GenericRecord payload() {
                return mainRecord;
            }

            @Override
            public Metadata metadata() {
                return null;
            }
        };

        ParsedSchema<Schema> parsedSchema = parser.getSchemaFromData(record);

        // Verify the schema was parsed correctly
        Assertions.assertNotNull(parsedSchema);
        Assertions.assertEquals("io.example.TableValue", parsedSchema.getParsedSchema().getFullName());

        // Get the schema references
        List<ParsedSchema<Schema>> references = parsedSchema.getSchemaReferences();

        // THIS IS THE KEY ASSERTION:
        // Even though the main schema has TWO fields referencing Point,
        // we should only have ONE reference in the list (deduplicated)
        Assertions.assertNotNull(references);

        // Collect the unique reference names
        Set<String> uniqueReferenceNames = references.stream()
            .map(ref -> ref.getParsedSchema().getFullName())
            .collect(Collectors.toSet());

        // Should have exactly 1 unique reference (Point)
        Assertions.assertEquals(1, uniqueReferenceNames.size(),
            "Expected exactly 1 unique reference (Point), but found duplicates: " +
            references.stream()
                .map(ref -> ref.getParsedSchema().getFullName())
                .collect(Collectors.toList()));

        // Verify it's the Point schema
        Assertions.assertTrue(uniqueReferenceNames.contains("io.debezium.data.geometry.Point"),
            "Expected to find Point schema reference");

        // ADDITIONAL ASSERTION: The actual list size should also be 1 (no duplicates)
        Assertions.assertEquals(1, references.size(),
            "Expected references list to contain exactly 1 item (deduplicated), but found " +
            references.size() + " items. This indicates duplicate references were not removed.");
    }

    /**
     * Test a more complex scenario with nested references at multiple levels.
     * This ensures deduplication works correctly with deep nesting.
     */
    @Test
    public void testNestedDuplicateReferencesAreDeduplicated() {
        // Use a single Schema.Parser instance to resolve references
        Schema.Parser schemaParser = new Schema.Parser();

        // Create a Coordinate schema
        String coordinateSchemaJson = """
            {
              "type": "record",
              "name": "Coordinate",
              "namespace": "io.example.geo",
              "fields": [
                {"name": "lat", "type": "double"},
                {"name": "lon", "type": "double"}
              ]
            }
            """;
        Schema coordinateSchema = schemaParser.parse(coordinateSchemaJson);

        // Create a Location schema that references Coordinate
        String locationSchemaJson = """
            {
              "type": "record",
              "name": "Location",
              "namespace": "io.example.geo",
              "fields": [
                {"name": "name", "type": "string"},
                {"name": "position", "type": "io.example.geo.Coordinate"}
              ]
            }
            """;
        Schema locationSchema = schemaParser.parse(locationSchemaJson);

        // Create a Journey schema with multiple Location fields (which themselves reference Coordinate)
        String journeySchemaJson = """
            {
              "type": "record",
              "name": "Journey",
              "namespace": "io.example",
              "fields": [
                {"name": "id", "type": "int"},
                {"name": "start", "type": "io.example.geo.Location"},
                {"name": "end", "type": "io.example.geo.Location"}
              ]
            }
            """;
        Schema journeySchema = schemaParser.parse(journeySchemaJson);

        // Create sample data
        GenericRecord coord1 = new GenericData.Record(coordinateSchema);
        coord1.put("lat", 40.7128);
        coord1.put("lon", -74.0060);

        GenericRecord location1 = new GenericData.Record(locationSchema);
        location1.put("name", "New York");
        location1.put("position", coord1);

        GenericRecord coord2 = new GenericData.Record(coordinateSchema);
        coord2.put("lat", 34.0522);
        coord2.put("lon", -118.2437);

        GenericRecord location2 = new GenericData.Record(locationSchema);
        location2.put("name", "Los Angeles");
        location2.put("position", coord2);

        GenericRecord journey = new GenericData.Record(journeySchema);
        journey.put("id", 1);
        journey.put("start", location1);
        journey.put("end", location2);

        Record<GenericRecord> record = new Record<GenericRecord>() {
            @Override
            public GenericRecord payload() {
                return journey;
            }

            @Override
            public Metadata metadata() {
                return null;
            }
        };

        ParsedSchema<Schema> parsedSchema = parser.getSchemaFromData(record);

        // Collect all references at all levels
        List<ParsedSchema<Schema>> allReferences = parsedSchema.getSchemaReferences();
        Set<String> uniqueReferenceNames = allReferences.stream()
            .map(ref -> ref.getParsedSchema().getFullName())
            .collect(Collectors.toSet());

        // We should have exactly 1 unique reference to Location
        // (even though Journey has 2 Location fields)
        Assertions.assertTrue(uniqueReferenceNames.contains("io.example.geo.Location"),
            "Expected to find Location schema reference");

        // Count how many Location references we have
        long locationCount = allReferences.stream()
            .filter(ref -> ref.getParsedSchema().getFullName().equals("io.example.geo.Location"))
            .count();

        Assertions.assertEquals(1, locationCount,
            "Expected exactly 1 Location reference (deduplicated), but found " + locationCount);
    }

    /**
     * Test that non-duplicate references are preserved correctly.
     */
    @Test
    public void testDistinctReferencesArePreserved() {
        // Use a single Schema.Parser instance to resolve references
        Schema.Parser schemaParser = new Schema.Parser();

        // Create two different nested schemas
        String pointSchemaJson = """
            {
              "type": "record",
              "name": "Point",
              "namespace": "io.example",
              "fields": [
                {"name": "x", "type": "double"},
                {"name": "y", "type": "double"}
              ]
            }
            """;

        String addressSchemaJson = """
            {
              "type": "record",
              "name": "Address",
              "namespace": "io.example",
              "fields": [
                {"name": "street", "type": "string"},
                {"name": "city", "type": "string"}
              ]
            }
            """;

        Schema pointSchema = schemaParser.parse(pointSchemaJson);
        Schema addressSchema = schemaParser.parse(addressSchemaJson);

        // Create main schema referencing both different schemas
        String mainSchemaJson = """
            {
              "type": "record",
              "name": "Location",
              "namespace": "io.example",
              "fields": [
                {"name": "id", "type": "int"},
                {"name": "position", "type": "io.example.Point"},
                {"name": "address", "type": "io.example.Address"}
              ]
            }
            """;
        Schema mainSchema = schemaParser.parse(mainSchemaJson);

        // Create sample data
        GenericRecord point = new GenericData.Record(pointSchema);
        point.put("x", 1.0);
        point.put("y", 2.0);

        GenericRecord address = new GenericData.Record(addressSchema);
        address.put("street", "Main St");
        address.put("city", "Boston");

        GenericRecord location = new GenericData.Record(mainSchema);
        location.put("id", 1);
        location.put("position", point);
        location.put("address", address);

        Record<GenericRecord> record = new Record<GenericRecord>() {
            @Override
            public GenericRecord payload() {
                return location;
            }

            @Override
            public Metadata metadata() {
                return null;
            }
        };

        ParsedSchema<Schema> parsedSchema = parser.getSchemaFromData(record);
        List<ParsedSchema<Schema>> references = parsedSchema.getSchemaReferences();

        Set<String> uniqueReferenceNames = references.stream()
            .map(ref -> ref.getParsedSchema().getFullName())
            .collect(Collectors.toSet());

        // Should have exactly 2 distinct references
        Assertions.assertEquals(2, uniqueReferenceNames.size(),
            "Expected 2 distinct references (Point and Address)");
        Assertions.assertTrue(uniqueReferenceNames.contains("io.example.Point"));
        Assertions.assertTrue(uniqueReferenceNames.contains("io.example.Address"));
    }

    /**
     * Test that calling getSchemaFromData twice with the same schema returns the same cached
     * ParsedSchema instance, avoiding redundant serialization work.
     */
    @Test
    public void testGetSchemaFromDataReturnsCachedInstance() {
        Schema.Parser schemaParser = new Schema.Parser();
        String schemaJson = """
            {
              "type": "record",
              "name": "Simple",
              "namespace": "io.example",
              "fields": [
                {"name": "id", "type": "int"},
                {"name": "name", "type": "string"}
              ]
            }
            """;
        Schema schema = schemaParser.parse(schemaJson);

        GenericRecord record1 = new GenericData.Record(schema);
        record1.put("id", 1);
        record1.put("name", "first");

        GenericRecord record2 = new GenericData.Record(schema);
        record2.put("id", 2);
        record2.put("name", "second");

        Record<GenericRecord> wrappedRecord1 = createRecord(record1);
        Record<GenericRecord> wrappedRecord2 = createRecord(record2);

        ParsedSchema<Schema> result1 = parser.getSchemaFromData(wrappedRecord1);
        ParsedSchema<Schema> result2 = parser.getSchemaFromData(wrappedRecord2);

        // Same schema type should return the exact same cached instance
        Assertions.assertSame(result1, result2,
                "Expected the same ParsedSchema instance for the same schema type (cached)");
    }

    /**
     * Test that calling getSchemaFromData with different schemas returns different ParsedSchema
     * instances.
     */
    @Test
    public void testGetSchemaFromDataReturnsDifferentInstancesForDifferentSchemas() {
        Schema.Parser schemaParser = new Schema.Parser();
        String schemaJsonA = """
            {
              "type": "record",
              "name": "TypeA",
              "namespace": "io.example",
              "fields": [{"name": "id", "type": "int"}]
            }
            """;
        String schemaJsonB = """
            {
              "type": "record",
              "name": "TypeB",
              "namespace": "io.example",
              "fields": [{"name": "value", "type": "string"}]
            }
            """;
        Schema schemaA = schemaParser.parse(schemaJsonA);
        Schema schemaB = schemaParser.parse(schemaJsonB);

        GenericRecord recordA = new GenericData.Record(schemaA);
        recordA.put("id", 1);

        GenericRecord recordB = new GenericData.Record(schemaB);
        recordB.put("value", "hello");

        ParsedSchema<Schema> resultA = parser.getSchemaFromData(createRecord(recordA));
        ParsedSchema<Schema> resultB = parser.getSchemaFromData(createRecord(recordB));

        Assertions.assertNotSame(resultA, resultB,
                "Expected different ParsedSchema instances for different schema types");
        Assertions.assertEquals("io.example.TypeA", resultA.getParsedSchema().getFullName());
        Assertions.assertEquals("io.example.TypeB", resultB.getParsedSchema().getFullName());
    }

    /**
     * Test that calling getSchemaFromData with dereference=true and dereference=false returns
     * different results, each independently cached.
     */
    @Test
    public void testGetSchemaFromDataDereferenceProducesDifferentCachedResults() {
        Schema.Parser schemaParser = new Schema.Parser();
        String nestedJson = """
            {
              "type": "record",
              "name": "Nested",
              "namespace": "io.example",
              "fields": [{"name": "value", "type": "int"}]
            }
            """;
        schemaParser.parse(nestedJson);

        String parentJson = """
            {
              "type": "record",
              "name": "Parent",
              "namespace": "io.example",
              "fields": [
                {"name": "id", "type": "int"},
                {"name": "child", "type": "io.example.Nested"}
              ]
            }
            """;
        Schema parentSchema = schemaParser.parse(parentJson);

        GenericRecord nested = new GenericData.Record(
                parentSchema.getField("child").schema());
        nested.put("value", 42);

        GenericRecord parent = new GenericData.Record(parentSchema);
        parent.put("id", 1);
        parent.put("child", nested);

        Record<GenericRecord> wrappedRecord = createRecord(parent);

        ParsedSchema<Schema> nonDeref = parser.getSchemaFromData(wrappedRecord, false);
        ParsedSchema<Schema> deref = parser.getSchemaFromData(wrappedRecord, true);

        // Dereferenced and non-dereferenced should be different objects
        Assertions.assertNotSame(nonDeref, deref,
                "Expected different ParsedSchema instances for dereferenced vs non-dereferenced");

        // Non-dereferenced should have references
        Assertions.assertFalse(nonDeref.getSchemaReferences().isEmpty(),
                "Non-dereferenced result should have schema references");

        // Dereferenced should have no references (full schema inlined)
        Assertions.assertTrue(deref.getSchemaReferences() == null || deref.getSchemaReferences().isEmpty(),
                "Dereferenced result should have no schema references");

        // Verify caching: calling again should return the same instances
        ParsedSchema<Schema> nonDeref2 = parser.getSchemaFromData(wrappedRecord, false);
        ParsedSchema<Schema> deref2 = parser.getSchemaFromData(wrappedRecord, true);
        Assertions.assertSame(nonDeref, nonDeref2,
                "Expected cached instance for non-dereferenced call");
        Assertions.assertSame(deref, deref2,
                "Expected cached instance for dereferenced call");
    }

    private Record<GenericRecord> createRecord(GenericRecord genericRecord) {
        return new Record<GenericRecord>() {
            @Override
            public GenericRecord payload() {
                return genericRecord;
            }

            @Override
            public Metadata metadata() {
                return null;
            }
        };
    }
}