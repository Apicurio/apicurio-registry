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

package io.apicurio.registry;

import static io.apicurio.registry.utils.tests.TestUtils.retry;
import static io.apicurio.registry.utils.tests.TestUtils.waitForSchema;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.support.TestCmmn;
import io.apicurio.registry.support.Tester;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.serde.AvroEncoding;
import io.apicurio.registry.utils.serde.AvroKafkaDeserializer;
import io.apicurio.registry.utils.serde.AvroKafkaSerializer;
import io.apicurio.registry.utils.serde.ProtobufKafkaDeserializer;
import io.apicurio.registry.utils.serde.ProtobufKafkaSerializer;
import io.apicurio.registry.utils.serde.SerdeConfig;
import io.apicurio.registry.utils.serde.SerdeHeaders;
import io.apicurio.registry.utils.serde.avro.AvroDatumProvider;
import io.apicurio.registry.utils.serde.avro.DefaultAvroDatumProvider;
import io.apicurio.registry.utils.serde.avro.ReflectAvroDatumProvider;
import io.apicurio.registry.utils.serde.strategy.AutoRegisterIdStrategy;
import io.apicurio.registry.utils.serde.strategy.CachedSchemaIdStrategy;
import io.apicurio.registry.utils.serde.strategy.FindBySchemaIdStrategy;
import io.apicurio.registry.utils.serde.strategy.FindLatestIdStrategy;
import io.apicurio.registry.utils.serde.strategy.GetOrCreateIdStrategy;
import io.apicurio.registry.utils.serde.strategy.GlobalIdStrategy;
import io.apicurio.registry.utils.serde.strategy.TopicRecordIdStrategy;
import io.apicurio.registry.utils.tests.RegistryRestClientTest;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class RegistrySerdeTest extends AbstractResourceTestBase {

    @RegistryRestClientTest
    public void testFindBySchema(RegistryRestClient restClient) throws Exception {
        String artifactId = generateArtifactId();
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        ArtifactMetaData amd = restClient.createArtifact(artifactId, ArtifactType.AVRO, new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8)));

        this.waitForGlobalId(amd.getGlobalId());

        Assertions.assertNotNull(restClient.getArtifactMetaDataByGlobalId(amd.getGlobalId()));
        GlobalIdStrategy<Schema> idStrategy = new FindBySchemaIdStrategy<>();
        Assertions.assertEquals(amd.getGlobalId(), idStrategy.findId(client, artifactId, ArtifactType.AVRO, schema));
    }

    @RegistryRestClientTest
    public void testGetOrCreate(RegistryRestClient restClient) throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        String artifactId = generateArtifactId();
        ArtifactMetaData amd = restClient.createArtifact(artifactId, ArtifactType.AVRO, new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8)));

        this.waitForGlobalId(amd.getGlobalId());

        Assertions.assertNotNull(restClient.getArtifactMetaDataByGlobalId(amd.getGlobalId()));
        GlobalIdStrategy<Schema> idStrategy = new GetOrCreateIdStrategy<>();
        Assertions.assertEquals(amd.getGlobalId(), idStrategy.findId(client, artifactId, ArtifactType.AVRO, schema));

        artifactId = generateArtifactId(); // new
        long id = idStrategy.findId(client, artifactId, ArtifactType.AVRO, schema);

        this.waitForGlobalId(id);

        Assertions.assertEquals(id, idStrategy.findId(client, artifactId, ArtifactType.AVRO, schema));
    }

    @RegistryRestClientTest
    public void testCachedSchema(RegistryRestClient restClient) throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord5x\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        String artifactId = generateArtifactId();

        GlobalIdStrategy<Schema> idStrategy = new CachedSchemaIdStrategy<>();
        long id = idStrategy.findId(restClient, artifactId, ArtifactType.AVRO, schema);

        retry(() -> restClient.getArtifactMetaDataByGlobalId(id));

        Assertions.assertEquals(id, idStrategy.findId(restClient, artifactId, ArtifactType.AVRO, schema));
    }

    @SuppressWarnings("unchecked")
    @RegistryRestClientTest
    public void testConfiguration(RegistryRestClient restClient) throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");

        String artifactId = generateArtifactId();

        ArtifactMetaData amd = restClient.createArtifact(
            artifactId + "-myrecord3",
            ArtifactType.AVRO,
            new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8))
        );
        // wait for global id store to populate (in case of Kafka / Streams)
        ArtifactMetaData amdById = retry(() -> restClient.getArtifactMetaDataByGlobalId(amd.getGlobalId()));
        Assertions.assertNotNull(amdById);

        GenericData.Record record = new GenericData.Record(schema);
        record.put("bar", "somebar");

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, "http://localhost:8081/api");
        config.put(SerdeConfig.ARTIFACT_ID_STRATEGY, new TopicRecordIdStrategy());
        config.put(SerdeConfig.GLOBAL_ID_STRATEGY, new FindLatestIdStrategy<>());
        config.put(AvroDatumProvider.REGISTRY_AVRO_DATUM_PROVIDER_CONFIG_PARAM, new DefaultAvroDatumProvider<>());
        Serializer<GenericData.Record> serializer = (Serializer<GenericData.Record>) getClass().getClassLoader()
                                                                                               .loadClass(AvroKafkaSerializer.class.getName())
                                                                                               .newInstance();
        serializer.configure(config, true);
        byte[] bytes = serializer.serialize(artifactId, record);

        Deserializer<GenericData.Record> deserializer = (Deserializer<GenericData.Record>) getClass().getClassLoader()
                                                                                                     .loadClass(AvroKafkaDeserializer.class.getName())
                                                                                                     .newInstance();
        deserializer.configure(config, true);

        record = deserializer.deserialize(artifactId, bytes);
        Assertions.assertEquals("somebar", record.get("bar").toString());

        config.put(SerdeConfig.ARTIFACT_ID_STRATEGY, TopicRecordIdStrategy.class);
        config.put(SerdeConfig.GLOBAL_ID_STRATEGY, FindLatestIdStrategy.class);
        config.put(AvroDatumProvider.REGISTRY_AVRO_DATUM_PROVIDER_CONFIG_PARAM, DefaultAvroDatumProvider.class);
        serializer.configure(config, true);
        bytes = serializer.serialize(artifactId, record);
        deserializer.configure(config, true);
        record = deserializer.deserialize(artifactId, bytes);
        Assertions.assertEquals("somebar", record.get("bar").toString());

        config.put(SerdeConfig.ARTIFACT_ID_STRATEGY, TopicRecordIdStrategy.class.getName());
        config.put(SerdeConfig.GLOBAL_ID_STRATEGY, FindLatestIdStrategy.class.getName());
        config.put(AvroDatumProvider.REGISTRY_AVRO_DATUM_PROVIDER_CONFIG_PARAM, DefaultAvroDatumProvider.class.getName());
        serializer.configure(config, true);
        bytes = serializer.serialize(artifactId, record);
        deserializer.configure(config, true);
        record = deserializer.deserialize(artifactId, bytes);
        Assertions.assertEquals("somebar", record.get("bar").toString());

        serializer.close();
        deserializer.close();
    }

    @RegistryRestClientTest
    public void testAvro(RegistryRestClient restClient) throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (AvroKafkaSerializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(restClient);
             Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(restClient)) {

            serializer.setGlobalIdStrategy(new AutoRegisterIdStrategy<>());
            
            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");

            String subject = generateArtifactId();

            byte[] bytes = serializer.serialize(subject, record);

            // some impl details ...
            waitForSchema(restClient, bytes);

            GenericData.Record ir = deserializer.deserialize(subject, bytes);

            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    @RegistryRestClientTest
    public void testAvroJSON(RegistryRestClient restClient) throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (AvroKafkaSerializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(restClient);
             Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(restClient)) {
            HashMap<String, String> config = new HashMap<>();
            config.put(SerdeConfig.AVRO_ENCODING, AvroEncoding.AVRO_JSON);
            serializer.configure(config,false);
            deserializer.configure(config, false);

            serializer.setGlobalIdStrategy(new AutoRegisterIdStrategy<>());

            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");

            String subject = generateArtifactId();

            byte[] bytes = serializer.serialize(subject, record);

            // Test msg is stored as json, take 1st 9 bytes off (magic byte and long)
            JSONObject msgAsJson = new JSONObject(new String(Arrays.copyOfRange(bytes, 9, bytes.length)));
            Assertions.assertEquals("somebar", msgAsJson.getString("bar"));
            
            // some impl details ...
            waitForSchema(restClient, bytes);

            GenericData.Record ir = deserializer.deserialize(subject, bytes);

            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    @RegistryRestClientTest
    public void testAvroUsingHeaders(RegistryRestClient restClient) throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (AvroKafkaSerializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(restClient);
             Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(restClient)) {

            serializer.setGlobalIdStrategy(new AutoRegisterIdStrategy<>());
            HashMap<String, String> config = new HashMap<>();
            config.put(SerdeConfig.USE_HEADERS, "true");
            serializer.configure(config,false);
            deserializer.configure(config, false);

            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");

            String subject = generateArtifactId();
            Headers headers = new RecordHeaders();
            byte[] bytes = serializer.serialize(subject, headers, record);
            Assertions.assertNotNull(headers.lastHeader(SerdeHeaders.HEADER_VALUE_GLOBAL_ID));
            Header globalId =  headers.lastHeader(SerdeHeaders.HEADER_VALUE_GLOBAL_ID);
            long id = ByteBuffer.wrap(globalId.value()).getLong();

            waitForGlobalId(id);

            GenericData.Record ir = deserializer.deserialize(subject, headers, bytes);

            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    @RegistryRestClientTest
    public void testAvroReflect(RegistryRestClient restClient) throws Exception {
        try (AvroKafkaSerializer<Tester> serializer = new AvroKafkaSerializer<Tester>(restClient);
             AvroKafkaDeserializer<Tester> deserializer = new AvroKafkaDeserializer<Tester>(restClient)) {

            serializer.setGlobalIdStrategy(new AutoRegisterIdStrategy<>());
            serializer.setAvroDatumProvider(new ReflectAvroDatumProvider<>());
            deserializer.setAvroDatumProvider(new ReflectAvroDatumProvider<>());
            
            String artifactId = generateArtifactId();

            Tester tester = new Tester("Apicurio");
            byte[] bytes = serializer.serialize(artifactId, tester);

            waitForSchema(restClient, bytes);

            tester = deserializer.deserialize(artifactId, bytes);

            Assertions.assertEquals("Apicurio", tester.getName());
        }
    }

    @RegistryRestClientTest
    public void testProto(RegistryRestClient restClient) throws Exception {
        try (ProtobufKafkaSerializer<TestCmmn.UUID> serializer = new ProtobufKafkaSerializer<TestCmmn.UUID>(restClient);
             Deserializer<DynamicMessage> deserializer = new ProtobufKafkaDeserializer(restClient)) {

            serializer.setGlobalIdStrategy(new AutoRegisterIdStrategy<>());

            TestCmmn.UUID record = TestCmmn.UUID.newBuilder().setLsb(2).setMsb(1).build();

            String subject = generateArtifactId();

            byte[] bytes = serializer.serialize(subject, record);

            waitForSchema(restClient, bytes);

            DynamicMessage dm = deserializer.deserialize(subject, bytes);
            Descriptors.Descriptor descriptor = dm.getDescriptorForType();

            Descriptors.FieldDescriptor lsb = descriptor.findFieldByName("lsb");
            Assertions.assertNotNull(lsb);
            Assertions.assertEquals(2L, dm.getField(lsb));

            Descriptors.FieldDescriptor msb = descriptor.findFieldByName("msb");
            Assertions.assertNotNull(msb);
            Assertions.assertEquals(1L, dm.getField(msb));
        }
    }
}
