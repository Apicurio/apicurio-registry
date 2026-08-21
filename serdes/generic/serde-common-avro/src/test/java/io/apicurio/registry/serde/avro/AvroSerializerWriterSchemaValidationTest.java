package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.reflect.ReflectData;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the writer schema validation added for issue #8510. When the schema resolved from the
 * registry (e.g. the latest artifact version with find-latest enabled) differs structurally from
 * the schema of the record being serialized, the Avro datum writer extracts record values by field
 * position of the resolved schema and silently writes values under the wrong fields. Nulls from
 * drifted optional fields land in unrelated fields without any error.
 */
public class AvroSerializerWriterSchemaValidationTest {

    private static final String TOPIC = "orders";

    // Schema of the records the producer actually emits.
    private static final Schema RECORD_SCHEMA = new Schema.Parser().parse(
            "{\"type\":\"record\",\"name\":\"Value\",\"namespace\":\"test\",\"fields\":["
                    + "{\"name\":\"order_number\",\"type\":\"int\"},"
                    + "{\"name\":\"legacy_code\",\"type\":[\"null\",\"int\"],\"default\":null},"
                    + "{\"name\":\"customer_id\",\"type\":\"int\"}]}");

    // Registry latest: legacy_code was replaced by an annotated Debezium date field.
    private static final Schema DRIFTED_SCHEMA = new Schema.Parser().parse(
            "{\"type\":\"record\",\"name\":\"Value\",\"namespace\":\"test\",\"fields\":["
                    + "{\"name\":\"order_number\",\"type\":\"int\"},"
                    + "{\"name\":\"order_date\",\"type\":[\"null\",{\"type\":\"int\","
                    + "\"connect.version\":1,\"connect.name\":\"io.debezium.time.Date\"}],"
                    + "\"default\":null},"
                    + "{\"name\":\"customer_id\",\"type\":\"int\"}]}");

    // Same structure as RECORD_SCHEMA, differing only in connect.* properties on a field type.
    private static final Schema PROPS_ONLY_SCHEMA = new Schema.Parser().parse(
            "{\"type\":\"record\",\"name\":\"Value\",\"namespace\":\"test\",\"fields\":["
                    + "{\"name\":\"order_number\",\"type\":{\"type\":\"int\","
                    + "\"connect.version\":1,\"connect.name\":\"io.debezium.time.Date\"}},"
                    + "{\"name\":\"legacy_code\",\"type\":[\"null\",\"int\"],\"default\":null},"
                    + "{\"name\":\"customer_id\",\"type\":\"int\"}]}");

    private static GenericRecord record() {
        return new GenericRecordBuilder(RECORD_SCHEMA)
                .set("order_number", 10001)
                .set("legacy_code", null)
                .set("customer_id", 42)
                .build();
    }

    private static AvroSerializer<GenericRecord> serializer(Schema resolvedSchema,
            Map<String, Object> configOverrides) {
        AvroSerializer<GenericRecord> serializer = new AvroSerializer<>(
                new FixedSchemaResolver<GenericRecord>(resolvedSchema));
        Map<String, Object> config = new HashMap<>(configOverrides);
        serializer.configure(new AvroSerdeConfig(config), false);
        return serializer;
    }

    @Test
    public void testStructurallyDriftedResolvedSchemaFailsSerialization() {
        AvroSerializer<GenericRecord> serializer = serializer(DRIFTED_SCHEMA, Map.of());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> serializer.serializeData(TOPIC, record()));
        assertTrue(e.getMessage().contains("does not structurally match"),
                "Unexpected message: " + e.getMessage());
        assertTrue(e.getMessage().contains(AvroSerdeConfig.AVRO_VALIDATE_WRITER_SCHEMA),
                "Message should mention the opt-out config: " + e.getMessage());
    }

    @Test
    public void testStructurallyDriftedResolvedSchemaFailsWithJsonEncoding() {
        AvroSerializer<GenericRecord> serializer = serializer(DRIFTED_SCHEMA,
                Map.of(AvroSerdeConfig.AVRO_ENCODING, AvroSerdeConfig.AVRO_ENCODING_JSON));

        assertThrows(IllegalStateException.class, () -> serializer.serializeData(TOPIC, record()));
    }

    @Test
    public void testIdenticalSchemaInstancePasses() throws Exception {
        AvroSerializer<GenericRecord> serializer = serializer(RECORD_SCHEMA, Map.of());

        byte[] bytes = serializer.serializeData(TOPIC, record());

        GenericRecord decoded = decode(serializer, bytes, RECORD_SCHEMA);
        assertEquals(10001, decoded.get("order_number"));
        assertNull(decoded.get("legacy_code"));
        assertEquals(42, decoded.get("customer_id"));
    }

    @Test
    public void testPropertyOnlyDifferencesAreTolerated() throws Exception {
        AvroSerializer<GenericRecord> serializer = serializer(PROPS_ONLY_SCHEMA, Map.of());

        byte[] bytes = serializer.serializeData(TOPIC, record());

        GenericRecord decoded = decode(serializer, bytes, PROPS_ONLY_SCHEMA);
        assertEquals(10001, decoded.get("order_number"));
        assertNull(decoded.get("legacy_code"));
        assertEquals(42, decoded.get("customer_id"));

        // A second record with the same schema instances exercises the validated-pair cache hit.
        byte[] secondBytes = serializer.serializeData(TOPIC, record());
        GenericRecord secondDecoded = decode(serializer, secondBytes, PROPS_ONLY_SCHEMA);
        assertEquals(10001, secondDecoded.get("order_number"));
        assertEquals(42, secondDecoded.get("customer_id"));
    }

    @Test
    public void testReflectProviderIsAlsoValidated() {
        Schema reflectSchema = ReflectData.get().getSchema(OrderPojo.class);
        Schema driftedReflectSchema = new Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"" + reflectSchema.getName() + "\",\"namespace\":\""
                        + reflectSchema.getNamespace() + "\",\"fields\":["
                        + "{\"name\":\"orderNumber\",\"type\":\"int\"},"
                        + "{\"name\":\"orderDate\",\"type\":[\"null\",\"int\"],\"default\":null},"
                        + "{\"name\":\"customerId\",\"type\":\"int\"}]}");

        AvroSerializer<OrderPojo> pojoSerializer = new AvroSerializer<>(
                new FixedSchemaResolver<OrderPojo>(driftedReflectSchema));
        Map<String, Object> config = new HashMap<>();
        config.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, ReflectAvroDatumProvider.class.getName());
        pojoSerializer.configure(new AvroSerdeConfig(config), false);

        OrderPojo pojo = new OrderPojo();
        pojo.orderNumber = 10001;
        pojo.customerId = 42;

        assertThrows(IllegalStateException.class, () -> pojoSerializer.serializeData(TOPIC, pojo));
    }

    @Test
    public void testNestedDriftInArraySchemaFailsSerialization() {
        Schema recordArraySchema = Schema.createArray(RECORD_SCHEMA);
        Schema driftedArraySchema = Schema.createArray(DRIFTED_SCHEMA);
        AvroSerializer<Object> arraySerializer = new AvroSerializer<>(
                new FixedSchemaResolver<Object>(driftedArraySchema));
        arraySerializer.configure(new AvroSerdeConfig(new HashMap<>()), false);

        GenericData.Array<GenericRecord> array = new GenericData.Array<>(recordArraySchema,
                List.of(record()));

        assertThrows(IllegalStateException.class, () -> arraySerializer.serializeData(TOPIC, array));
    }

    @Test
    public void testValidationCanBeDisabled() throws Exception {
        AvroSerializer<GenericRecord> serializer = serializer(DRIFTED_SCHEMA,
                Map.of(AvroSerdeConfig.AVRO_VALIDATE_WRITER_SCHEMA, "false"));

        // Legacy behavior: serialization succeeds and the legacy_code value is silently written
        // into order_date's slot. This documents the corruption the validation prevents.
        GenericRecord input = new GenericRecordBuilder(RECORD_SCHEMA)
                .set("order_number", 10001)
                .set("legacy_code", 12345)
                .set("customer_id", 42)
                .build();
        byte[] bytes = serializer.serializeData(TOPIC, input);

        // Read with the schema the message actually references: the value shows up under the
        // wrong field name.
        GenericRecord decoded = decode(serializer, bytes, DRIFTED_SCHEMA);
        assertEquals(10001, decoded.get("order_number"));
        assertEquals(12345, decoded.get("order_date"));
        assertEquals(42, decoded.get("customer_id"));

        // Read with the producer's schema as the reader: the original value is gone.
        GenericRecord reRead = decode(serializer, bytes, DRIFTED_SCHEMA, RECORD_SCHEMA);
        assertNull(reRead.get("legacy_code"));
    }

    private static GenericRecord decode(AvroSerializer<GenericRecord> serializer, byte[] bytes,
            Schema writerSchema) throws Exception {
        return decode(serializer, bytes, writerSchema, writerSchema);
    }

    private static GenericRecord decode(AvroSerializer<GenericRecord> serializer, byte[] bytes,
            Schema writerSchema, Schema readerSchema) throws Exception {
        int offset = 1 + serializer.getSerdeConfigurer().getIdHandler().idSize();
        GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(writerSchema, readerSchema);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(bytes, offset,
                bytes.length - offset, null);
        return reader.read(null, decoder);
    }

    public static class OrderPojo {
        public int orderNumber;
        public int customerId;
    }

    /**
     * Resolver stub that always resolves the given schema, simulating a registry whose latest
     * artifact version differs from what the producer emits.
     */
    private static final class FixedSchemaResolver<D> implements SchemaResolver<Schema, D> {

        private final SchemaLookupResult<Schema> result;

        private FixedSchemaResolver(Schema resolvedSchema) {
            this.result = SchemaLookupResult.<Schema> builder()
                    .parsedSchema(new ParsedSchemaImpl<Schema>().setParsedSchema(resolvedSchema)
                            .setRawSchema(resolvedSchema.toString().getBytes(StandardCharsets.UTF_8)))
                    .globalId(1L).contentId(1L).groupId("default").artifactId(TOPIC + "-value")
                    .version("1").build();
        }

        @Override
        public void setClientFacade(RegistryClientFacade clientFacade) {
        }

        @Override
        public void setArtifactResolverStrategy(
                ArtifactReferenceResolverStrategy<Schema, D> artifactResolverStrategy) {
        }

        @Override
        public SchemaParser<Schema, D> getSchemaParser() {
            return null;
        }

        @Override
        public SchemaLookupResult<Schema> resolveSchema(Record<D> data) {
            return result;
        }

        @Override
        public SchemaLookupResult<Schema> resolveSchemaByArtifactReference(ArtifactReference reference) {
            return result;
        }

        @Override
        public void reset() {
        }

        @Override
        public void close() {
        }
    }
}
