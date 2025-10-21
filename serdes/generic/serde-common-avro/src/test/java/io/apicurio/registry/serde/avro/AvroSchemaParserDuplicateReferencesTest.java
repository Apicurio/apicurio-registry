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
        parser = new AvroSchemaParser<>(datumProvider);
    }

    /**
     * Test that verifies duplicate references are properly deduplicated when extracting
     * schema from data. This simulates the Debezium PostGIS Point geometry use case where
     * a table has multiple columns of the same nested type.
     */
    @Test
    public void testDuplicateReferencesAreDeduplicated() {
        // Create a nested Point schema (similar to io.debezium.data.geometry.Point)
        String pointSchemaJson = "{"
            + "\"type\":\"record\","
            + "\"name\":\"Point\","
            + "\"namespace\":\"io.debezium.data.geometry\","
            + "\"fields\":["
            + "  {\"name\":\"x\",\"type\":\"double\"},"
            + "  {\"name\":\"y\",\"type\":\"double\"}"
            + "]"
            + "}";

        // Use a single Schema.Parser instance to resolve references
        Schema.Parser schemaParser = new Schema.Parser();
        Schema pointSchema = schemaParser.parse(pointSchemaJson);

        // Create a main schema with MULTIPLE fields referencing the same Point schema
        // This is what causes duplicate references
        String mainSchemaJson = "{"
            + "\"type\":\"record\","
            + "\"name\":\"TableValue\","
            + "\"namespace\":\"io.example\","
            + "\"fields\":["
            + "  {\"name\":\"id\",\"type\":\"int\"},"
            + "  {\"name\":\"start_point\",\"type\":\"io.debezium.data.geometry.Point\"},"
            + "  {\"name\":\"end_point\",\"type\":\"io.debezium.data.geometry.Point\"}"
            + "]"
            + "}";
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
        String coordinateSchemaJson = "{"
            + "\"type\":\"record\","
            + "\"name\":\"Coordinate\","
            + "\"namespace\":\"io.example.geo\","
            + "\"fields\":["
            + "  {\"name\":\"lat\",\"type\":\"double\"},"
            + "  {\"name\":\"lon\",\"type\":\"double\"}"
            + "]"
            + "}";
        Schema coordinateSchema = schemaParser.parse(coordinateSchemaJson);

        // Create a Location schema that references Coordinate
        String locationSchemaJson = "{"
            + "\"type\":\"record\","
            + "\"name\":\"Location\","
            + "\"namespace\":\"io.example.geo\","
            + "\"fields\":["
            + "  {\"name\":\"name\",\"type\":\"string\"},"
            + "  {\"name\":\"position\",\"type\":\"io.example.geo.Coordinate\"}"
            + "]"
            + "}";
        Schema locationSchema = schemaParser.parse(locationSchemaJson);

        // Create a Journey schema with multiple Location fields (which themselves reference Coordinate)
        String journeySchemaJson = "{"
            + "\"type\":\"record\","
            + "\"name\":\"Journey\","
            + "\"namespace\":\"io.example\","
            + "\"fields\":["
            + "  {\"name\":\"id\",\"type\":\"int\"},"
            + "  {\"name\":\"start\",\"type\":\"io.example.geo.Location\"},"
            + "  {\"name\":\"end\",\"type\":\"io.example.geo.Location\"}"
            + "]"
            + "}";
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
        String pointSchemaJson = "{"
            + "\"type\":\"record\","
            + "\"name\":\"Point\","
            + "\"namespace\":\"io.example\","
            + "\"fields\":["
            + "  {\"name\":\"x\",\"type\":\"double\"},"
            + "  {\"name\":\"y\",\"type\":\"double\"}"
            + "]"
            + "}";

        String addressSchemaJson = "{"
            + "\"type\":\"record\","
            + "\"name\":\"Address\","
            + "\"namespace\":\"io.example\","
            + "\"fields\":["
            + "  {\"name\":\"street\",\"type\":\"string\"},"
            + "  {\"name\":\"city\",\"type\":\"string\"}"
            + "]"
            + "}";

        Schema pointSchema = schemaParser.parse(pointSchemaJson);
        Schema addressSchema = schemaParser.parse(addressSchemaJson);

        // Create main schema referencing both different schemas
        String mainSchemaJson = "{"
            + "\"type\":\"record\","
            + "\"name\":\"Location\","
            + "\"namespace\":\"io.example\","
            + "\"fields\":["
            + "  {\"name\":\"id\",\"type\":\"int\"},"
            + "  {\"name\":\"position\",\"type\":\"io.example.Point\"},"
            + "  {\"name\":\"address\",\"type\":\"io.example.Address\"}"
            + "]"
            + "}";
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
}