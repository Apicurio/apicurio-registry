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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericData.Record;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
import io.apicurio.registry.utils.converter.json.CompactFormatStrategy;
import io.apicurio.registry.utils.converter.json.FormatStrategy;
import io.apicurio.registry.utils.converter.json.PrettyFormatStrategy;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioV2BaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.serdes.apicurio.AvroGenericRecordSchemaFactory;

/**
 * @author Ales Justin
 */
@Tag(Constants.SERDES)
@Tag(Constants.ACCEPTANCE)
public class RegistryConverterIT extends ApicurioV2BaseIT {

    @Test
    public void testConfiguration() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String topic = TestUtils.generateArtifactId();
        String recordName = "myrecord4";
        AvroGenericRecordSchemaFactory schemaFactory = new AvroGenericRecordSchemaFactory(groupId, recordName, List.of("bar"));
        Schema schema = schemaFactory.generateSchema();

        createArtifact(groupId, topic + "-" + recordName, ArtifactType.AVRO, new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8)));

        GenericData.Record record = new GenericData.Record(schema);
        record.put("bar", "somebar");

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV2ApiUrl());
        config.put(SerdeBasedConverter.REGISTRY_CONVERTER_SERIALIZER_PARAM, AvroKafkaSerializer.class.getName());
        config.put(SerdeBasedConverter.REGISTRY_CONVERTER_DESERIALIZER_PARAM, AvroKafkaDeserializer.class.getName());
        config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        config.put(AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        SerdeBasedConverter<Void, GenericData.Record> converter = new SerdeBasedConverter<>();

        byte[] bytes;
        try {
            converter.configure(config, true);
            bytes = converter.fromConnectData(topic, null, record);
            record = (GenericData.Record) converter.toConnectData(topic, bytes).value();
            Assertions.assertEquals("somebar", record.get("bar").toString());
        } finally {
            converter.close();
        }

    }

    @Test
    public void testAvro() throws Exception {
        try (AvroConverter<Record> converter = new AvroConverter<>()) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV2ApiUrl());
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
