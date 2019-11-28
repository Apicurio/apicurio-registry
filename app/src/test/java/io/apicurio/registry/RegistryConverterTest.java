/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry;

import io.apicurio.registry.client.RegistryClient;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.converter.AbstractConverter;
import io.apicurio.registry.utils.converter.AvroConverter;
import io.apicurio.registry.utils.serde.AbstractKafkaSerDe;
import io.apicurio.registry.utils.serde.AbstractKafkaSerializer;
import io.apicurio.registry.utils.serde.AvroKafkaDeserializer;
import io.apicurio.registry.utils.serde.AvroKafkaSerializer;
import io.apicurio.registry.utils.serde.avro.AvroDatumProvider;
import io.apicurio.registry.utils.serde.avro.DefaultAvroDatumProvider;
import io.apicurio.registry.utils.serde.strategy.AutoRegisterIdStrategy;
import io.apicurio.registry.utils.serde.strategy.TopicRecordIdStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.connect.storage.Converter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class RegistryConverterTest extends AbstractResourceTestBase {

    @Test
    public void testConfiguration() throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord4\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");

        try (RegistryService service = RegistryClient.create("http://localhost:8081")) {
            CompletionStage<ArtifactMetaData> csa = service.createArtifact(
                ArtifactType.AVRO,
                "test-myrecord4",
                new ByteArrayInputStream(schema.toString().getBytes())
            );
            ArtifactMetaData amd = ConcurrentUtil.result(csa);
            // wait for global id store to populate (in case of Kafka / Streams)
            ArtifactMetaData amdById = retry(() -> service.getArtifactMetaDataByGlobalId(amd.getGlobalId()));
            Assertions.assertNotNull(amdById);
        }

        GenericData.Record record = new GenericData.Record(schema);
        record.put("bar", "somebar");

        Map<String, Object> config = new HashMap<>();
        config.put(AbstractKafkaSerDe.REGISTRY_URL_CONFIG_PARAM, "http://localhost:8081");
        config.put(AbstractConverter.REGISTRY_CONVERTER_SERIALIZER_PARAM, AvroKafkaSerializer.class.getName());
        config.put(AbstractConverter.REGISTRY_CONVERTER_DESERIALIZER_PARAM, AvroKafkaDeserializer.class.getName());
        config.put(AbstractKafkaSerializer.REGISTRY_ARTIFACT_ID_STRATEGY_CONFIG_PARAM, new TopicRecordIdStrategy());
        config.put(AvroDatumProvider.REGISTRY_AVRO_DATUM_PROVIDER_CONFIG_PARAM, new DefaultAvroDatumProvider<>());
        AbstractConverter<GenericData.Record> converter = new AbstractConverter<>();

        byte[] bytes;
        try {
            converter.configure(config, true);
            bytes = converter.fromConnectData("test", null, record);
            record = (GenericData.Record) converter.toConnectData("test", bytes).value();
            Assertions.assertEquals("somebar", record.get("bar").toString());
        } finally {
            converter.close();
        }

        config.put(AbstractConverter.REGISTRY_CONVERTER_SERIALIZER_PARAM, AvroKafkaSerializer.class);
        config.put(AbstractConverter.REGISTRY_CONVERTER_DESERIALIZER_PARAM, AvroKafkaDeserializer.class);

        converter = new AbstractConverter<>();
        try {
            converter.configure(config, true);
            bytes = converter.fromConnectData("test", null, record);
            record = (GenericData.Record) converter.toConnectData("test", bytes).value();
            Assertions.assertEquals("somebar", record.get("bar").toString());
        } finally {
            converter.close();
        }

        config.put(AbstractConverter.REGISTRY_CONVERTER_SERIALIZER_PARAM, new AvroKafkaSerializer<>());
        config.put(AbstractConverter.REGISTRY_CONVERTER_DESERIALIZER_PARAM, new AvroKafkaDeserializer<>());

        converter = new AbstractConverter<>();
        try {
            converter.configure(config, true);
            bytes = converter.fromConnectData("test", null, record);
            record = (GenericData.Record) converter.toConnectData("test", bytes).value();
            Assertions.assertEquals("somebar", record.get("bar").toString());
        } finally {
            converter.close();
        }
    }

    @Test
    public void testAvro() throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord4\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (RegistryService service = RegistryClient.cached("http://localhost:8081")) {
            try (AvroKafkaSerializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(service).setGlobalIdStrategy(new AutoRegisterIdStrategy<>());
                 AvroKafkaDeserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(service)) {

                Converter converter = new AvroConverter<>(serializer, deserializer);

                GenericData.Record record = new GenericData.Record(schema);
                record.put("bar", "somebar");

                byte[] bytes = converter.fromConnectData("foo", null, record);

                // some impl details ...
                waitForSchema(service, bytes);

                GenericData.Record ir = (GenericData.Record) converter.toConnectData("foo", bytes).value();
                Assertions.assertEquals("somebar", ir.get("bar").toString());
            }
        }
    }
}