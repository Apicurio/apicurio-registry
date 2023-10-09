/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Carles Arnal
 */
@Tag(Constants.SERDES)
@Tag(Constants.ACCEPTANCE)
@QuarkusIntegrationTest
public class RegistryConverterIT extends ApicurioRegistryBaseIT {

    @Override
    public void cleanArtifacts() throws Exception {
        //Don't clean up
    }

    @Test
    public void testConfiguration() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String topic = TestUtils.generateArtifactId();
        String recordName = "myrecord4";
        AvroGenericRecordSchemaFactory schemaFactory = new AvroGenericRecordSchemaFactory(groupId, recordName, List.of("bar"));
        Schema schema = schemaFactory.generateSchema();

        createArtifact(groupId, topic + "-" + recordName, ArtifactType.AVRO, new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8)));

        Record record = new Record(schema);
        record.put("bar", "somebar");

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, getRegistryV2ApiUrl());
        config.put(SerdeBasedConverter.REGISTRY_CONVERTER_SERIALIZER_PARAM, AvroKafkaSerializer.class.getName());
        config.put(SerdeBasedConverter.REGISTRY_CONVERTER_DESERIALIZER_PARAM, AvroKafkaDeserializer.class.getName());
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
        String expectedSchema = "{\n" +
                "  \"type\" : \"record\",\n" +
                "  \"name\" : \"ConnectDefault\",\n" +
                "  \"namespace\" : \"io.confluent.connect.avro\",\n" +
                "  \"fields\" : [ {\n" +
                "    \"name\" : \"int16Test\",\n" +
                "    \"type\" : [ {\n" +
                "      \"type\" : \"int\",\n" +
                "      \"connect.doc\" : \"int16test field\",\n" +
                "      \"connect.default\" : 2,\n" +
                "      \"connect.type\" : \"int16\"\n" +
                "    }, \"null\" ],\n" +
                "    \"default\" : 2\n" +
                "  } ]\n" +
                "}";

        try (AvroConverter<Record> converter = new AvroConverter<>()) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.REGISTRY_URL, getRegistryV2ApiUrl());
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            converter.configure(config, false);

            org.apache.kafka.connect.data.Schema sc = SchemaBuilder.struct()
                    .field("int16Test", SchemaBuilder.int16().optional().defaultValue((short) 2).doc("int16test field")
                            .build());
            Struct struct = new Struct(sc);
            struct.put("int16Test", (short) 3);

            String subject = TestUtils.generateArtifactId();

            byte[] bytes = converter.fromConnectData(subject, sc, struct);

            // some impl details ...
            TestUtils.waitForSchema(globalId -> registryClient.getContentByGlobalId(globalId) != null, bytes);

            Struct ir = (Struct) converter.toConnectData(subject, bytes).value();
            Assertions.assertEquals((short) 3, ir.get("int16Test"));

            AvroData avroData = new AvroData(10);
            Assertions.assertEquals(expectedSchema, avroData.fromConnectSchema(ir.schema()).toString(true));
        }
    }

    @Test
    public void testAvroBytesDefaultValue() throws Exception {
        String expectedSchema = "{\n" +
                "  \"type\" : \"record\",\n" +
                "  \"name\" : \"ConnectDefault\",\n" +
                "  \"namespace\" : \"io.confluent.connect.avro\",\n" +
                "  \"fields\" : [ {\n" +
                "    \"name\" : \"bytesTest\",\n" +
                "    \"type\" : [ {\n" +
                "      \"type\" : \"bytes\",\n" +
                "      \"connect.parameters\" : {\n" +
                "        \"lenght\" : \"10\"\n" +
                "      },\n" +
                "      \"connect.default\" : \"test\"\n" +
                "    }, \"null\" ],\n" +
                "    \"default\" : \"test\"\n" +
                "  } ]\n" +
                "}";

        try (AvroConverter<Record> converter = new AvroConverter<>()) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.REGISTRY_URL, getRegistryV2ApiUrl());
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            converter.configure(config, false);

            org.apache.kafka.connect.data.Schema sc = SchemaBuilder.struct()
                    .field("bytesTest", SchemaBuilder.bytes().optional().parameters(Map.of("lenght", "10")).defaultValue("test".getBytes())
                            .build());
            Struct struct = new Struct(sc);

            struct.put("bytesTest", "testingBytes".getBytes());

            String subject = TestUtils.generateArtifactId();

            byte[] bytes = converter.fromConnectData(subject, sc, struct);

            // some impl details ...
            TestUtils.waitForSchema(globalId -> registryClient.getContentByGlobalId(globalId) != null, bytes);
            Struct ir = (Struct) converter.toConnectData(subject, bytes).value();
            AvroData avroData = new AvroData(10);
            Assertions.assertEquals(expectedSchema, avroData.fromConnectSchema(ir.schema()).toString(true));
        }

    }

    @Test
    public void testAvro() throws Exception {
        try (AvroConverter<Record> converter = new AvroConverter<>()) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.REGISTRY_URL, getRegistryV2ApiUrl());
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            converter.configure(config, false);

            org.apache.kafka.connect.data.Schema sc = SchemaBuilder.struct()
                    .field("bar", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                    .build();
            Struct struct = new Struct(sc);
            struct.put("bar", "somebar");

            String subject = TestUtils.generateArtifactId();

            byte[] bytes = converter.fromConnectData(subject, sc, struct);

            // some impl details ...
            TestUtils.waitForSchema(globalId -> registryClient.getContentByGlobalId(globalId) != null, bytes);

            Struct ir = (Struct) converter.toConnectData(subject, bytes).value();
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    @Test
    public void testPrettyJson() throws Exception {
        testJson(
                createRegistryClient(),
                new PrettyFormatStrategy(),
                input -> {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode root = mapper.readTree(input);
                        return root.get("schemaId").asLong();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
        );
    }

    @Test
    public void testCompactJson() throws Exception {
        testJson(
                createRegistryClient(),
                new CompactFormatStrategy(),
                input -> {
                    ByteBuffer buffer = AbstractKafkaSerDe.getByteBuffer(input);
                    return buffer.getLong();
                }
        );
    }

    private void testJson(RegistryClient restClient, FormatStrategy formatStrategy, Function<byte[], Long> fn) throws Exception {
        try (ExtJsonConverter converter = new ExtJsonConverter(restClient)) {
            converter.setFormatStrategy(formatStrategy);
            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            converter.configure(config, false);

            org.apache.kafka.connect.data.Schema sc = SchemaBuilder.struct()
                    .field("bar", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                    .build();
            Struct struct = new Struct(sc);
            struct.put("bar", "somebar");

            byte[] bytes = converter.fromConnectData("extjson", sc, struct);

            // some impl details ...
            TestUtils.waitForSchemaCustom(globalId -> restClient.getContentByGlobalId(globalId) != null, bytes, fn);

            //noinspection rawtypes
            Map ir = (Map) converter.toConnectData("extjson", bytes).value();
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }
}
