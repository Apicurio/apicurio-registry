/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.noprofile.serde;

import static io.apicurio.registry.utils.tests.TestUtils.waitForSchema;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.kubetrade.schema.trade.AvroSchemaA;
import com.kubetrade.schema.trade.AvroSchemaB;
import com.kubetrade.schema.trade.AvroSchemaC;
import com.kubetrade.schema.trade.AvroSchemaD;
import com.kubetrade.schema.trade.AvroSchemaE;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.SerdeHeaders;
import io.apicurio.registry.serde.config.IdOption;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.DefaultAvroDatumProvider;
import io.apicurio.registry.serde.avro.ReflectAvroDatumProvider;
import io.apicurio.registry.serde.avro.strategy.QualifiedRecordIdStrategy;
import io.apicurio.registry.serde.avro.strategy.RecordIdStrategy;
import io.apicurio.registry.serde.avro.strategy.TopicRecordIdStrategy;
import io.apicurio.registry.support.Tester;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
public class AvroSerdeTest extends AbstractResourceTestBase {

    private RegistryClient restClient;

    @BeforeEach
    public void createIsolatedClient() {
        restClient = RegistryClientFactory.create(TestUtils.getRegistryV2ApiUrl(testPort));
    }

    @Test
    public void testConfiguration() throws Exception {
        String recordName = "myrecord3";
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + recordName + "\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");

        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();

        final Integer globalId = createArtifact(groupId, topic + "-" + recordName, ArtifactType.AVRO, schema.toString());

        this.waitForGlobalId(globalId);

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV2ApiUrl(testPort));
        config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        config.put(SerdeConfig.EXPLICIT_ARTIFACT_VERSION, "1");
        config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        config.put(AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        Serializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>();
        serializer.configure(config, true);

        Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<GenericData.Record>();

        TestUtils.retry(() -> {

            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");
            byte[] bytes = serializer.serialize(topic, record);

            Map<String, Object> deserializerConfig = new HashMap<>();
            deserializerConfig.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV2ApiUrl(testPort));
            deserializer.configure(deserializerConfig, true);

            GenericData.Record deserializedRecord = deserializer.deserialize(topic, bytes);
            Assertions.assertEquals(record, deserializedRecord);
            Assertions.assertEquals("somebar", record.get("bar").toString());


            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class);
            config.put(AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class);
            serializer.configure(config, true);
            bytes = serializer.serialize(topic, record);

            deserializer.configure(deserializerConfig, true);
            record = deserializer.deserialize(topic, bytes);
            Assertions.assertEquals("somebar", record.get("bar").toString());

            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
            config.put(AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
            serializer.configure(config, true);
            bytes = serializer.serialize(topic, record);
            deserializer.configure(deserializerConfig, true);
            record = deserializer.deserialize(topic, bytes);
            Assertions.assertEquals("somebar", record.get("bar").toString());
        });

        serializer.close();
        deserializer.close();
    }

    @Test
    public void testAvro() throws Exception {
        testAvroAutoRegisterIdInBody(RecordIdStrategy.class, () -> restClient.getArtifactMetaData("test-group-avro", "myrecord3"));

    }

    @Test
    public void testAvroQualifiedRecordIdStrategy() throws Exception {
        testAvroAutoRegisterIdInBody(QualifiedRecordIdStrategy.class, () -> restClient.getArtifactMetaData(null, "test-group-avro.myrecord3"));
    }

    private void testAvroAutoRegisterIdInBody(Class<? extends ArtifactReferenceResolverStrategy<?, ?>> strategy, Supplier<ArtifactMetaData> artifactFinder) throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"namespace\":\"test-group-avro\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (AvroKafkaSerializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(restClient);
             Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(restClient)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, strategy);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            config.put(SerdeConfig.ENABLE_HEADERS, "false");
            serializer.configure(config, false);

            config = new HashMap<>();
            deserializer.configure(config, false);

            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");

            String topic = generateArtifactId();

            byte[] bytes = serializer.serialize(topic, record);

            // some impl details ...
            waitForSchema(globalId -> {
                if (restClient.getContentByGlobalId(globalId) != null) {
                    ArtifactMetaData artifactMetadata = artifactFinder.get();
                    assertEquals(globalId, artifactMetadata.getGlobalId());
                    return true;
                }
                return false;
            }, bytes);

            GenericData.Record ir = deserializer.deserialize(topic, bytes);

            Assertions.assertEquals(record, ir);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    @Test
    public void testAvroJSON() throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (AvroKafkaSerializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(restClient);
             Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(restClient)) {

            Map<String, String> config = new HashMap<>();
            config.put(AvroKafkaSerdeConfig.AVRO_ENCODING, AvroKafkaSerdeConfig.AVRO_ENCODING_JSON);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            config.put(SerdeConfig.ENABLE_HEADERS, "false");
            serializer.configure(config, false);

            config = new HashMap<>();
            config.put(AvroKafkaSerdeConfig.AVRO_ENCODING, AvroKafkaSerdeConfig.AVRO_ENCODING_JSON);
            deserializer.configure(config, false);

            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");

            String artifactId = generateArtifactId();

            byte[] bytes = serializer.serialize(artifactId, record);

            // Test msg is stored as json, take 1st 9 bytes off (magic byte and long)
            JSONObject msgAsJson = new JSONObject(new String(Arrays.copyOfRange(bytes, 9, bytes.length)));
            Assertions.assertEquals("somebar", msgAsJson.getString("bar"));

            // some impl details ...
            waitForSchema(globalId -> restClient.getContentByGlobalId(globalId) != null, bytes);

            GenericData.Record ir = deserializer.deserialize(artifactId, bytes);

            Assertions.assertEquals(record, ir);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    @Test
    public void avroJsonWithReferences() throws Exception {
        try (AvroKafkaSerializer<AvroSchemaB> serializer = new AvroKafkaSerializer<AvroSchemaB>(restClient);
             Deserializer<AvroSchemaB> deserializer = new AvroKafkaDeserializer<>(restClient)) {

            Map<String, String> config = new HashMap<>();
            config.put(AvroKafkaSerdeConfig.AVRO_ENCODING, AvroKafkaSerdeConfig.AVRO_ENCODING_JSON);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            config.put(SerdeConfig.ENABLE_HEADERS, "false");
            serializer.configure(config, false);

            config = new HashMap<>();
            config.put(AvroKafkaSerdeConfig.AVRO_ENCODING, AvroKafkaSerdeConfig.AVRO_ENCODING_JSON);
            config.putIfAbsent(AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER, ReflectAvroDatumProvider.class.getName());
            deserializer.configure(config, false);

            AvroSchemaB avroSchemaB = new AvroSchemaB();
            AvroSchemaA avroSchemaA = AvroSchemaA.GEMINI;
            AvroSchemaA avroSchemaA2 = AvroSchemaA.GEMINI;
            AvroSchemaC avroSchemaC = new AvroSchemaC();
            AvroSchemaD avroSchemaD = new AvroSchemaD();
            AvroSchemaE avroSchemaE = new AvroSchemaE();

            avroSchemaE.setPayload("ESchema");
            avroSchemaE.setSymbol("ESymbol");

            avroSchemaD.setSchemaE(avroSchemaE);
            avroSchemaD.setSymbol("Dsymbol");

            avroSchemaC.setSymbol("CSymbol");
            avroSchemaC.setPayload("CSchema");
            avroSchemaC.setSchemaD(avroSchemaD);

            avroSchemaB.setSchemaC(avroSchemaC);
            avroSchemaB.setSchemaA(avroSchemaA);
            avroSchemaB.setSchemaA2(avroSchemaA2);
            avroSchemaB.setKey(UUID.randomUUID().toString());

            String artifactId = generateArtifactId();

            byte[] bytes = serializer.serialize(artifactId, avroSchemaB);

            // Test msg is stored as json, take 1st 9 bytes off (magic byte and long)
            JSONObject msgAsJson = new JSONObject(new String(Arrays.copyOfRange(bytes, 9, bytes.length)));
            Assertions.assertEquals("CSymbol", msgAsJson.getJSONObject("schemaC").getString("symbol"));

            // some impl details ...
            waitForSchema(globalId -> restClient.getContentByGlobalId(globalId) != null, bytes);

            AvroSchemaB ir = deserializer.deserialize(artifactId, bytes);

            Assertions.assertEquals(avroSchemaB, ir);
            Assertions.assertEquals(AvroSchemaA.GEMINI, ir.getSchemaA());
        }
    }

    @Test
    public void testAvroUsingHeaders() throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (AvroKafkaSerializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(restClient);
             Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(restClient)) {

            Map<String, String> config = new HashMap<>();
            config.put(SerdeConfig.ENABLE_HEADERS, "true");
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            serializer.configure(config, false);

            config = new HashMap<>();
            config.put(SerdeConfig.ENABLE_HEADERS, "true");
            deserializer.configure(config, false);

            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");

            String artifactId = generateArtifactId();
            Headers headers = new RecordHeaders();
            byte[] bytes = serializer.serialize(artifactId, headers, record);

            Assertions.assertNotNull(headers.lastHeader(SerdeHeaders.HEADER_VALUE_GLOBAL_ID));
            Header globalId = headers.lastHeader(SerdeHeaders.HEADER_VALUE_GLOBAL_ID);
            long id = ByteBuffer.wrap(globalId.value()).getLong();

            waitForGlobalId(id);

            GenericData.Record ir = deserializer.deserialize(artifactId, headers, bytes);

            Assertions.assertEquals(record, ir);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    @Test
    public void testAvroReflect() throws Exception {
        try (AvroKafkaSerializer<Tester> serializer = new AvroKafkaSerializer<Tester>(restClient);
             AvroKafkaDeserializer<Tester> deserializer = new AvroKafkaDeserializer<Tester>(restClient)) {

            Map<String, String> config = new HashMap<>();
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            config.put(SerdeConfig.ENABLE_HEADERS, "false");
            config.put(AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER, ReflectAvroDatumProvider.class.getName());
            serializer.configure(config, false);

            config = new HashMap<>();
            config.put(AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER, ReflectAvroDatumProvider.class.getName());
            deserializer.configure(config, false);

            String artifactId = generateArtifactId();

            Tester tester = new Tester("Apicurio");
            byte[] bytes = serializer.serialize(artifactId, tester);

            waitForSchema(globalId -> restClient.getContentByGlobalId(globalId) != null, bytes);

            Tester deserializedTester = deserializer.deserialize(artifactId, bytes);

            Assertions.assertEquals(tester, deserializedTester);
            Assertions.assertEquals("Apicurio", deserializedTester.getName());
        }
    }

    private SchemaRegistryClient buildClient() {
        return new CachedSchemaRegistryClient("http://localhost:" + testPort + "/apis/ccompat/v6", 3);
    }

    @Test
    public void testSerdeMix() throws Exception {
        SchemaRegistryClient schemaClient = buildClient();

        String subject = generateArtifactId();

        String rawSchema = "{\"type\":\"record\",\"name\":\"myrecord5\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}";
        ParsedSchema schema = new AvroSchema(rawSchema);
        schemaClient.register(subject + "-value", schema);

        GenericData.Record record = new GenericData.Record(new Schema.Parser().parse(rawSchema));
        record.put("bar", "somebar");

        try (KafkaAvroSerializer serializer1 = new KafkaAvroSerializer(schemaClient);
                AvroKafkaDeserializer<GenericData.Record> deserializer1 = new AvroKafkaDeserializer<GenericData.Record>(restClient))
        {
            byte[] bytes = serializer1.serialize(subject, record);

            TestUtils.retry(() -> TestUtils.waitForSchema(globalId -> restClient.getContentById(globalId) != null, bytes, bb -> (long) bb.getInt()));

            deserializer1.asLegacyId();
            Map<String, String> config = new HashMap<>();
            config.put(SerdeConfig.USE_ID, IdOption.contentId.name());
            deserializer1.configure(config, false);
            GenericData.Record ir = deserializer1.deserialize(subject, bytes);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }

        try (KafkaAvroDeserializer deserializer2 = new KafkaAvroDeserializer(schemaClient);
                AvroKafkaSerializer<GenericData.Record> serializer2 = new AvroKafkaSerializer<GenericData.Record>(restClient)) {

            Map<String, String> config = new HashMap<>();
            config.put(SerdeConfig.USE_ID, IdOption.contentId.name());

            serializer2.asLegacyId();
            serializer2.configure(config, false);
            byte[] bytes = serializer2.serialize(subject, record);

            GenericData.Record ir = (GenericData.Record) deserializer2.deserialize(subject, bytes);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

}
