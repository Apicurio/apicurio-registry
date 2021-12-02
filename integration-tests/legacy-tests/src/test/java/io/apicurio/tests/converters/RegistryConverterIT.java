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

import static io.apicurio.registry.utils.tests.TestUtils.retry;
import static io.apicurio.registry.utils.tests.TestUtils.waitForSchema;
import static io.apicurio.registry.utils.tests.TestUtils.waitForSchemaCustom;
import static io.apicurio.tests.common.Constants.ACCEPTANCE;
import static io.apicurio.tests.common.Constants.SERDES;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
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

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.converter.AvroConverter;
import io.apicurio.registry.utils.converter.ExtJsonConverter;
import io.apicurio.registry.utils.converter.SchemalessConverter;
import io.apicurio.registry.utils.converter.avro.AvroData;
import io.apicurio.registry.utils.converter.avro.AvroDataConfig;
import io.apicurio.registry.utils.converter.json.CompactFormatStrategy;
import io.apicurio.registry.utils.converter.json.FormatStrategy;
import io.apicurio.registry.utils.converter.json.PrettyFormatStrategy;
import io.apicurio.registry.utils.serde.AbstractKafkaSerDe;
import io.apicurio.registry.utils.serde.AbstractKafkaStrategyAwareSerDe;
import io.apicurio.registry.utils.serde.AvroKafkaDeserializer;
import io.apicurio.registry.utils.serde.AvroKafkaSerializer;
import io.apicurio.registry.utils.serde.avro.AvroDatumProvider;
import io.apicurio.registry.utils.serde.avro.DefaultAvroDatumProvider;
import io.apicurio.registry.utils.serde.strategy.AutoRegisterIdStrategy;
import io.apicurio.registry.utils.serde.strategy.TopicRecordIdStrategy;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.BaseIT;

/**
 * @author Ales Justin
 */
@Tag(SERDES)
@Tag(ACCEPTANCE)
public class RegistryConverterIT extends BaseIT {

    @Test
    public void testConfiguration() throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord4\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");

        String artifactId = generateArtifactId();

        ArtifactMetaData amd = registryClient.createArtifact(artifactId + "-myrecord4", ArtifactType.AVRO, null,
            new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8))
        );
        // wait for global id store to populate (in case of Kafka / Streams)
        ArtifactMetaData amdById = retry(() -> registryClient.getArtifactMetaDataByGlobalId(amd.getGlobalId()));
        Assertions.assertNotNull(amdById);

        GenericData.Record record = new GenericData.Record(schema);
        record.put("bar", "somebar");

        Map<String, Object> config = new HashMap<>();
        config.put(AbstractKafkaSerDe.REGISTRY_URL_CONFIG_PARAM, TestUtils.getRegistryV1ApiUrl());
        config.put(SchemalessConverter.REGISTRY_CONVERTER_SERIALIZER_PARAM, AvroKafkaSerializer.class.getName());
        config.put(SchemalessConverter.REGISTRY_CONVERTER_DESERIALIZER_PARAM, AvroKafkaDeserializer.class.getName());
        config.put(AbstractKafkaStrategyAwareSerDe.REGISTRY_ARTIFACT_ID_STRATEGY_CONFIG_PARAM, new TopicRecordIdStrategy());
        config.put(AvroDatumProvider.REGISTRY_AVRO_DATUM_PROVIDER_CONFIG_PARAM, new DefaultAvroDatumProvider<>());
        SchemalessConverter<GenericData.Record> converter = new SchemalessConverter<>();

        byte[] bytes;
        try {
            converter.configure(config, true);
            bytes = converter.fromConnectData(artifactId, null, record);
            record = (GenericData.Record) converter.toConnectData(artifactId, bytes).value();
            Assertions.assertEquals("somebar", record.get("bar").toString());
        } finally {
            converter.close();
        }

        config.put(SchemalessConverter.REGISTRY_CONVERTER_SERIALIZER_PARAM, AvroKafkaSerializer.class);
        config.put(SchemalessConverter.REGISTRY_CONVERTER_DESERIALIZER_PARAM, AvroKafkaDeserializer.class);

        converter = new SchemalessConverter<>();
        try {
            converter.configure(config, true);
            bytes = converter.fromConnectData(artifactId, null, record);
            record = (GenericData.Record) converter.toConnectData(artifactId, bytes).value();
            Assertions.assertEquals("somebar", record.get("bar").toString());
        } finally {
            converter.close();
        }

        config.put(SchemalessConverter.REGISTRY_CONVERTER_SERIALIZER_PARAM, new AvroKafkaSerializer<>());
        config.put(SchemalessConverter.REGISTRY_CONVERTER_DESERIALIZER_PARAM, new AvroKafkaDeserializer<>());

        converter = new SchemalessConverter<>();
        try {
            converter.configure(config, true);
            bytes = converter.fromConnectData(artifactId, null, record);
            record = (GenericData.Record) converter.toConnectData(artifactId, bytes).value();
            Assertions.assertEquals("somebar", record.get("bar").toString());
        } finally {
            converter.close();
        }
    }

    @Test
    public void testAvro() throws Exception {
        try (AvroKafkaSerializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(createCompatibleClient());
             AvroKafkaDeserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(createCompatibleClient())) {

            serializer.setGlobalIdStrategy(new AutoRegisterIdStrategy<>());
            AvroData avroData = new AvroData(new AvroDataConfig(Collections.emptyMap()));
            try (AvroConverter<Record> converter = new AvroConverter<>(serializer, deserializer, avroData)) {

                org.apache.kafka.connect.data.Schema sc = SchemaBuilder.struct()
                                                                       .field("bar", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                                                                       .build();
                Struct struct = new Struct(sc);
                struct.put("bar", "somebar");

                String subject = generateArtifactId();

                byte[] bytes = converter.fromConnectData(subject, sc, struct);

                // some impl details ...
                waitForSchema(globalId -> registryClient.getArtifactMetaDataByGlobalId(globalId) != null, bytes);

                Struct ir = (Struct) converter.toConnectData(subject, bytes).value();
                Assertions.assertEquals("somebar", ir.get("bar").toString());
            }
        }
    }

    @Test
    public void testPrettyJson() throws Exception {
        testJson(
            createCompatibleClient(),
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
            createCompatibleClient(),
            new CompactFormatStrategy(),
            input -> {
                ByteBuffer buffer = AbstractKafkaSerDe.getByteBuffer(input);
                return buffer.getLong();
            }
        );
    }

    private void testJson(RegistryService restClient, FormatStrategy formatStrategy, Function<byte[], Long> fn) throws Exception {
        try (ExtJsonConverter converter = new ExtJsonConverter(restClient)) {
            converter.setGlobalIdStrategy(new AutoRegisterIdStrategy<>());
            converter.setFormatStrategy(formatStrategy);
            converter.configure(Collections.emptyMap(), false);

            org.apache.kafka.connect.data.Schema sc = SchemaBuilder.struct()
                                                                   .field("bar", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                                                                   .build();
            Struct struct = new Struct(sc);
            struct.put("bar", "somebar");

            byte[] bytes = converter.fromConnectData("extjson", sc, struct);

            // some impl details ...
            waitForSchemaCustom(globalId -> restClient.getArtifactMetaDataByGlobalId(globalId) != null, bytes, fn);

            //noinspection rawtypes
            Map ir = (Map) converter.toConnectData("extjson", bytes).value();
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }
}
