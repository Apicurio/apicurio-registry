package io.apicurio.tests.debezium;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.function.Predicate;

/**
 * Utility class providing helper methods for Debezium integration tests including:
 * - Schema builders for Debezium envelope structures
 * - CDC event validators
 * - Common predicates for event filtering
 */
public class DebeziumTestUtils {

    /**
     * Creates a simple value schema for testing
     */
    public static Schema createValueSchema(String name) {
        return SchemaBuilder.record(name)
            .namespace("io.apicurio.test")
            .fields()
            .requiredLong("id")
            .optionalString("name")
            .optionalString("email")
            .endRecord();
    }

    /**
     * Creates a Debezium envelope schema wrapping a value schema
     */
    public static Schema createDebeziumEnvelopeSchema(String name, Schema valueSchema) {
        return SchemaBuilder.record(name + ".Envelope")
            .namespace("io.apicurio.test")
            .fields()
            .name("before").type().optional().type(valueSchema).noDefault()
            .name("after").type().optional().type(valueSchema).noDefault()
            .requiredString("op")
            .optionalLong("ts_ms")
            .name("source").type().optional().record("Source")
                .fields()
                .optionalString("version")
                .optionalString("connector")
                .optionalString("name")
                .optionalLong("ts_ms")
                .optionalString("db")
                .optionalString("schema")
                .optionalString("table")
                .endRecord().noDefault()
            .endRecord();
    }

    /**
     * Creates a Debezium key schema for a table
     */
    public static Schema createKeySchema(String tableName) {
        return SchemaBuilder.record(tableName + ".Key")
            .namespace("io.apicurio.test")
            .fields()
            .requiredLong("id")
            .endRecord();
    }

    /**
     * Builds a Debezium CREATE (INSERT) event
     */
    public static GenericRecord buildCreateEvent(Schema envelopeSchema, GenericRecord afterValue) {
        GenericRecord event = new GenericData.Record(envelopeSchema);
        event.put("before", null);
        event.put("after", afterValue);
        event.put("op", "c");
        event.put("ts_ms", System.currentTimeMillis());
        return event;
    }

    /**
     * Builds a Debezium UPDATE event
     */
    public static GenericRecord buildUpdateEvent(Schema envelopeSchema, GenericRecord beforeValue, GenericRecord afterValue) {
        GenericRecord event = new GenericData.Record(envelopeSchema);
        event.put("before", beforeValue);
        event.put("after", afterValue);
        event.put("op", "u");
        event.put("ts_ms", System.currentTimeMillis());
        return event;
    }

    /**
     * Builds a Debezium DELETE event
     */
    public static GenericRecord buildDeleteEvent(Schema envelopeSchema, GenericRecord beforeValue) {
        GenericRecord event = new GenericData.Record(envelopeSchema);
        event.put("before", beforeValue);
        event.put("after", null);
        event.put("op", "d");
        event.put("ts_ms", System.currentTimeMillis());
        return event;
    }

    /**
     * Builds a Debezium READ (snapshot) event
     */
    public static GenericRecord buildReadEvent(Schema envelopeSchema, GenericRecord afterValue) {
        GenericRecord event = new GenericData.Record(envelopeSchema);
        event.put("before", null);
        event.put("after", afterValue);
        event.put("op", "r");
        event.put("ts_ms", System.currentTimeMillis());
        return event;
    }

    /**
     * Creates a predicate to match events by operation type
     */
    public static Predicate<ConsumerRecord<String, GenericRecord>> isOperation(String opType) {
        return record -> {
            GenericRecord value = record.value();
            if (value == null) return false;
            Object op = value.get("op");
            return op != null && op.toString().equals(opType);
        };
    }

    /**
     * Creates a predicate to match CREATE events
     */
    public static Predicate<ConsumerRecord<String, GenericRecord>> isCreate() {
        return isOperation("c");
    }

    /**
     * Creates a predicate to match UPDATE events
     */
    public static Predicate<ConsumerRecord<String, GenericRecord>> isUpdate() {
        return isOperation("u");
    }

    /**
     * Creates a predicate to match DELETE events
     */
    public static Predicate<ConsumerRecord<String, GenericRecord>> isDelete() {
        return isOperation("d");
    }

    /**
     * Creates a predicate to match READ (snapshot) events
     */
    public static Predicate<ConsumerRecord<String, GenericRecord>> isRead() {
        return isOperation("r");
    }

    /**
     * Creates a predicate to match events where the 'after' field contains a specific value
     */
    public static Predicate<ConsumerRecord<String, GenericRecord>> afterFieldEquals(String fieldName, Object value) {
        return record -> {
            GenericRecord eventValue = record.value();
            if (eventValue == null) return false;
            GenericRecord after = (GenericRecord) eventValue.get("after");
            if (after == null) return false;
            Object fieldValue = after.get(fieldName);
            return value.equals(fieldValue);
        };
    }

    /**
     * Creates a predicate to match events where the 'before' field contains a specific value
     */
    public static Predicate<ConsumerRecord<String, GenericRecord>> beforeFieldEquals(String fieldName, Object value) {
        return record -> {
            GenericRecord eventValue = record.value();
            if (eventValue == null) return false;
            GenericRecord before = (GenericRecord) eventValue.get("before");
            if (before == null) return false;
            Object fieldValue = before.get(fieldName);
            return value.equals(fieldValue);
        };
    }

    /**
     * Creates a predicate to match events from a specific table
     */
    public static Predicate<ConsumerRecord<String, GenericRecord>> fromTable(String tableName) {
        return record -> {
            GenericRecord eventValue = record.value();
            if (eventValue == null) return false;
            GenericRecord source = (GenericRecord) eventValue.get("source");
            if (source == null) return false;
            Object table = source.get("table");
            return table != null && table.toString().equals(tableName);
        };
    }

    /**
     * Validates that a CDC event has the expected structure
     */
    public static boolean hasValidDebeziumStructure(GenericRecord event) {
        if (event == null) return false;

        // Check required fields exist
        if (event.getSchema().getField("op") == null) return false;

        // Check envelope fields
        boolean hasBefore = event.getSchema().getField("before") != null;
        boolean hasAfter = event.getSchema().getField("after") != null;

        return hasBefore && hasAfter;
    }

    /**
     * Validates that an event is a valid CREATE operation
     */
    public static boolean isValidCreateEvent(GenericRecord event) {
        if (!hasValidDebeziumStructure(event)) return false;

        Object op = event.get("op");
        Object before = event.get("before");
        Object after = event.get("after");

        return "c".equals(op != null ? op.toString() : null) &&
               before == null &&
               after != null;
    }

    /**
     * Validates that an event is a valid UPDATE operation
     */
    public static boolean isValidUpdateEvent(GenericRecord event) {
        if (!hasValidDebeziumStructure(event)) return false;

        Object op = event.get("op");
        Object before = event.get("before");
        Object after = event.get("after");

        return "u".equals(op != null ? op.toString() : null) &&
               before != null &&
               after != null;
    }

    /**
     * Validates that an event is a valid DELETE operation
     */
    public static boolean isValidDeleteEvent(GenericRecord event) {
        if (!hasValidDebeziumStructure(event)) return false;

        Object op = event.get("op");
        Object before = event.get("before");
        Object after = event.get("after");

        return "d".equals(op != null ? op.toString() : null) &&
               before != null &&
               after == null;
    }

    /**
     * Extracts the table name from a CDC event's source field
     */
    public static String getTableName(GenericRecord event) {
        if (event == null) return null;
        GenericRecord source = (GenericRecord) event.get("source");
        if (source == null) return null;
        Object table = source.get("table");
        return table != null ? table.toString() : null;
    }

    /**
     * Extracts the database name from a CDC event's source field
     */
    public static String getDatabaseName(GenericRecord event) {
        if (event == null) return null;
        GenericRecord source = (GenericRecord) event.get("source");
        if (source == null) return null;
        Object db = source.get("db");
        return db != null ? db.toString() : null;
    }
}
