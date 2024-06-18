package io.apicurio.tests.converters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractKafkaSerDe;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.DefaultAvroDatumProvider;
import io.apicurio.registry.serde.avro.strategy.TopicRecordIdStrategy;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.converter.AvroConverter;
import io.apicurio.registry.utils.converter.ExtJsonConverter;
import io.apicurio.registry.utils.converter.SerdeBasedConverter;
import io.apicurio.registry.utils.converter.avro.AvroData;
import io.apicurio.registry.utils.converter.json.CompactFormatStrategy;
import io.apicurio.registry.utils.converter.json.FormatStrategy;
import io.apicurio.registry.utils.converter.json.PrettyFormatStrategy;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.AvroGenericRecordSchemaFactory;
import io.apicurio.tests.utils.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData.Record;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Tag(Constants.SERDES)
@Tag(Constants.ACCEPTANCE)
@QuarkusIntegrationTest
public class RegistryConverterIT extends ApicurioRegistryBaseIT {

    @Override
    public void cleanArtifacts() throws Exception {
        // Don't clean up
    }

    @Test
    public void testConfiguration() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String topic = TestUtils.generateArtifactId();
        String recordName = "myrecord4";
        AvroGenericRecordSchemaFactory schemaFactory = new AvroGenericRecordSchemaFactory(groupId, recordName,
                List.of("bar"));
        Schema schema = schemaFactory.generateSchema();

        createArtifact(groupId, topic + "-" + recordName, ArtifactType.AVRO, schema.toString(),
                ContentTypes.APPLICATION_JSON, null, null);

        Record record = new Record(schema);
        record.put("bar", "somebar");

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
        config.put(SerdeBasedConverter.REGISTRY_CONVERTER_SERIALIZER_PARAM,
                AvroKafkaSerializer.class.getName());
        config.put(SerdeBasedConverter.REGISTRY_CONVERTER_DESERIALIZER_PARAM,
                AvroKafkaDeserializer.class.getName());
        config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        config.put(AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        SerdeBasedConverter<Void, Record> converter = new SerdeBasedConverter<>();

        byte[] bytes;
        try {
            converter.configure(config, true);
            bytes = converter.fromConnectData(topic, null, record);
            record = (Record) converter.toConnectData(topic, bytes).value();
            Assertions.assertEquals("somebar", record.get("bar").toString());
        } finally {
            converter.close();
        }

    }

    @Test
    public void testAvroIntDefaultValue() throws Exception {
        String expectedSchema = "{\n" + "  \"type\" : \"record\",\n" + "  \"name\" : \"ConnectDefault\",\n"
                + "  \"namespace\" : \"io.confluent.connect.avro\",\n" + "  \"fields\" : [ {\n"
                + "    \"name\" : \"int16Test\",\n" + "    \"type\" : [ {\n" + "      \"type\" : \"int\",\n"
                + "      \"connect.doc\" : \"int16test field\",\n" + "      \"connect.default\" : 2,\n"
                + "      \"connect.type\" : \"int16\"\n" + "    }, \"null\" ],\n" + "    \"default\" : 2\n"
                + "  } ]\n" + "}";

        try (AvroConverter<Record> converter = new AvroConverter<>()) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            converter.configure(config, false);

            org.apache.kafka.connect.data.Schema sc = SchemaBuilder.struct().field("int16Test",
                    SchemaBuilder.int16().optional().defaultValue((short) 2).doc("int16test field").build());
            Struct struct = new Struct(sc);
            struct.put("int16Test", (short) 3);

            String subject = TestUtils.generateArtifactId();

            byte[] bytes = converter.fromConnectData(subject, sc, struct);

            // some impl details ...
            TestUtils.waitForSchema(globalId -> {
                try {
                    return registryClient.ids().globalIds().byGlobalId(globalId).get()
                            .readAllBytes().length > 0;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, bytes);

            Struct ir = (Struct) converter.toConnectData(subject, bytes).value();
            Assertions.assertEquals((short) 3, ir.get("int16Test"));

            AvroData avroData = new AvroData(10);
            Assertions.assertEquals(expectedSchema, avroData.fromConnectSchema(ir.schema()).toString(true));
        }
    }

    @Test
    public void testAvroBytesDefaultValue() throws Exception {
        String expectedSchema = "{\n" + "  \"type\" : \"record\",\n" + "  \"name\" : \"ConnectDefault\",\n"
                + "  \"namespace\" : \"io.confluent.connect.avro\",\n" + "  \"fields\" : [ {\n"
                + "    \"name\" : \"bytesTest\",\n" + "    \"type\" : [ {\n" + "      \"type\" : \"bytes\",\n"
                + "      \"connect.parameters\" : {\n" + "        \"lenght\" : \"10\"\n" + "      },\n"
                + "      \"connect.default\" : \"test\"\n" + "    }, \"null\" ],\n"
                + "    \"default\" : \"test\"\n" + "  } ]\n" + "}";

        try (AvroConverter<Record> converter = new AvroConverter<>()) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            converter.configure(config, false);

            org.apache.kafka.connect.data.Schema sc = SchemaBuilder.struct().field("bytesTest",
                    SchemaBuilder.bytes().optional().parameters(Map.of("lenght", "10"))
                            .defaultValue("test".getBytes()).build());
            Struct struct = new Struct(sc);

            struct.put("bytesTest", "testingBytes".getBytes());

            String subject = TestUtils.generateArtifactId();

            byte[] bytes = converter.fromConnectData(subject, sc, struct);

            // some impl details ...
            TestUtils.waitForSchema(globalId -> {
                try {
                    return registryClient.ids().globalIds().byGlobalId(globalId).get()
                            .readAllBytes().length > 0;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, bytes);
            Struct ir = (Struct) converter.toConnectData(subject, bytes).value();
            AvroData avroData = new AvroData(10);
            Assertions.assertEquals(expectedSchema, avroData.fromConnectSchema(ir.schema()).toString(true));
        }

    }

    @Test
    public void testAvro() throws Exception {
        try (AvroConverter<Record> converter = new AvroConverter<>()) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            converter.configure(config, false);

            org.apache.kafka.connect.data.Schema sc = SchemaBuilder.struct()
                    .field("bar", org.apache.kafka.connect.data.Schema.STRING_SCHEMA).build();
            Struct struct = new Struct(sc);
            struct.put("bar", "somebar");

            String subject = TestUtils.generateArtifactId();

            byte[] bytes = converter.fromConnectData(subject, sc, struct);

            // some impl details ...
            TestUtils.waitForSchema(globalId -> {
                try {
                    return registryClient.ids().globalIds().byGlobalId(globalId).get()
                            .readAllBytes().length > 0;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, bytes);

            Struct ir = (Struct) converter.toConnectData(subject, bytes).value();
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    @Test
    public void testPrettyJson() throws Exception {
        testJson(createRegistryClient(), new PrettyFormatStrategy(), input -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(input);
                return root.get("schemaId").asLong();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test
    public void testConnectStruct() throws Exception {
        try (ExtJsonConverter converter = new ExtJsonConverter()) {

            converter.setFormatStrategy(new CompactFormatStrategy());
            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            converter.configure(config, false);

            org.apache.kafka.connect.data.Schema envelopeSchema = buildEnvelopeSchema();

            // Create a Struct object for the Envelope
            Struct envelopeStruct = new Struct(envelopeSchema);

            // Set values for the fields in the Envelope
            envelopeStruct.put("before", buildValueStruct());
            envelopeStruct.put("after", buildValueStruct());
            envelopeStruct.put("source", buildSourceStruct());
            envelopeStruct.put("op", "insert");
            envelopeStruct.put("ts_ms", 1638362438000L); // Replace with the actual timestamp
            envelopeStruct.put("transaction", buildTransactionStruct());

            String subject = TestUtils.generateArtifactId();

            byte[] bytes = converter.fromConnectData(subject, envelopeSchema, envelopeStruct);

            // some impl details ...
            TestUtils.waitForSchema(globalId -> {
                try {
                    return registryClient.ids().globalIds().byGlobalId(globalId).get()
                            .readAllBytes().length > 0;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, bytes);

            Struct ir = (Struct) converter.toConnectData(subject, bytes).value();
            Assertions.assertEquals(envelopeStruct, ir);
        }
    }

    private static org.apache.kafka.connect.data.Schema buildEnvelopeSchema() {
        // Define the Envelope schema
        return SchemaBuilder.struct().name("dbserver1.public.aviation.Envelope").version(1)
                .field("before", buildValueSchema()).field("after", buildValueSchema())
                .field("source", buildSourceSchema()).field("op", SchemaBuilder.STRING_SCHEMA)
                .field("ts_ms", SchemaBuilder.OPTIONAL_INT64_SCHEMA)
                .field("transaction", buildTransactionSchema()).build();
    }

    private static org.apache.kafka.connect.data.Schema buildValueSchema() {
        // Define the Value schema
        return SchemaBuilder.struct().name("dbserver1.public.aviation.Value").version(1)
                .field("id", SchemaBuilder.INT32_SCHEMA).build();
    }

    private static Struct buildValueStruct() {
        // Create a Struct object for the Value
        Struct valueStruct = new Struct(buildValueSchema());

        // Set value for the "id" field
        valueStruct.put("id", 123); // Replace with the actual ID value

        return valueStruct;
    }

    private static org.apache.kafka.connect.data.Schema buildSourceSchema() {
        // Define the Source schema
        return SchemaBuilder.struct().name("io.debezium.connector.postgresql.Source").version(1)
                .field("id", SchemaBuilder.STRING_SCHEMA).field("version", SchemaBuilder.STRING_SCHEMA)
                .build();
    }

    private static Struct buildSourceStruct() {
        // Create a Struct object for the Source
        Struct sourceStruct = new Struct(buildSourceSchema());

        // Set values for the fields in the Source
        sourceStruct.put("id", "source_id");
        sourceStruct.put("version", "1.0");

        return sourceStruct;
    }

    private static org.apache.kafka.connect.data.Schema buildTransactionSchema() {
        // Define the Transaction schema
        return SchemaBuilder.struct().name("event.block").version(1).field("id", SchemaBuilder.STRING_SCHEMA)
                .build();
    }

    private static Struct buildTransactionStruct() {
        // Create a Struct object for the Transaction
        Struct transactionStruct = new Struct(buildTransactionSchema());

        // Set value for the "id" field in Transaction
        transactionStruct.put("id", "transaction_id");

        return transactionStruct;
    }

    @Test
    public void testCompactJson() throws Exception {
        testJson(createRegistryClient(), new CompactFormatStrategy(), input -> {
            ByteBuffer buffer = AbstractKafkaSerDe.getByteBuffer(input);
            return buffer.getLong();
        });
    }

    private void testJson(RegistryClient restClient, FormatStrategy formatStrategy, Function<byte[], Long> fn)
            throws Exception {
        try (ExtJsonConverter converter = new ExtJsonConverter(restClient)) {
            converter.setFormatStrategy(formatStrategy);
            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            converter.configure(config, false);

            org.apache.kafka.connect.data.Schema sc = SchemaBuilder.struct()
                    .field("bar", org.apache.kafka.connect.data.Schema.STRING_SCHEMA).build();
            Struct struct = new Struct(sc);
            struct.put("bar", "somebar");

            byte[] bytes = converter.fromConnectData("extjson", sc, struct);

            // some impl details ...
            TestUtils.waitForSchemaCustom(globalId -> {
                try {
                    return restClient.ids().globalIds().byGlobalId(globalId).get().readAllBytes().length > 0;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, bytes, fn);

            // noinspection rawtypes
            Struct ir = (Struct) converter.toConnectData("extjson", bytes).value();
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }
}
