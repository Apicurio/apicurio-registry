/*
 * Copyright 2021 Red Hat
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

package io.apicurio.tests.serdes.apicurio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.SerdeHeaders;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.ReflectAvroDatumProvider;
import io.apicurio.registry.serde.avro.strategy.RecordIdStrategy;
import io.apicurio.registry.serde.avro.strategy.TopicRecordIdStrategy;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.serde.strategy.TopicIdStrategy;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioV2BaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.KafkaFacade;
import io.apicurio.tests.common.serdes.TestObject;

/**
 * @author Fabian Martinez
 */
@Tag(Constants.SERDES)
public class AvroSerdeIT extends ApicurioV2BaseIT {

    private KafkaFacade kafkaCluster = KafkaFacade.getInstance();

    private Class<AvroKafkaSerializer> serializer = AvroKafkaSerializer.class;
    private Class<AvroKafkaDeserializer> deserializer = AvroKafkaDeserializer.class;

    @BeforeAll
    void setupEnvironment() {
        kafkaCluster.startIfNeeded();
    }

    @AfterAll
    void teardownEnvironment() throws Exception {
        kafkaCluster.stopIfPossible();
    }

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testTopicIdStrategyFindLatest() throws Exception {
        String topicName = TestUtils.generateTopic();
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordapicurio1", List.of("key1"));

        createArtifact(null, artifactId, ArtifactType.AVRO, avroSchema.generateSchemaStream());

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withDeserializer(deserializer)
            .withStrategy(TopicIdStrategy.class)
            .withDataGenerator(avroSchema::generateRecord)
            .withDataValidator(avroSchema::validateRecord)
            .build()
            .test();
    }

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testSimpleTopicIdStrategyFindLatest() throws Exception {
        String topicName = TestUtils.generateTopic();
        String artifactId = topicName;
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordapicurio1", List.of("key1"));

        createArtifact(topicName, artifactId, ArtifactType.AVRO, avroSchema.generateSchemaStream());

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withDeserializer(deserializer)
            .withStrategy(SimpleTopicIdStrategy.class)
            .withDataGenerator(avroSchema::generateRecord)
            .withDataValidator(avroSchema::validateRecord)
            .withProducerProperty(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, topicName)
            .build()
            .test();
    }

    @Test
    void testRecordIdStrategydFindLatest() throws Exception {
        String topicName = TestUtils.generateTopic();
        kafkaCluster.createTopic(topicName, 1, 1);

        String groupId = TestUtils.generateSubject();
        String artifactId = TestUtils.generateSubject();
        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(groupId, artifactId, List.of("key1"));

        createArtifact(groupId, artifactId, ArtifactType.AVRO, avroSchema.generateSchemaStream());

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withDeserializer(deserializer)
            .withStrategy(RecordIdStrategy.class)
            .withDataGenerator(avroSchema::generateRecord)
            .withDataValidator(avroSchema::validateRecord)
            .build()
            .test();
    }

    @Test
    void testTopicRecordIdStrategydFindLatest() throws Exception {
        String topicName = TestUtils.generateTopic();
        kafkaCluster.createTopic(topicName, 1, 1);

        String groupId = TestUtils.generateSubject();
        String recordName = TestUtils.generateSubject();
        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(groupId, recordName, List.of("key1"));

        String artifactId = topicName + "-" + recordName;
        createArtifact(groupId, artifactId, ArtifactType.AVRO, avroSchema.generateSchemaStream());

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withDeserializer(deserializer)
            .withStrategy(TopicRecordIdStrategy.class)
            .withDataGenerator(avroSchema::generateRecord)
            .withDataValidator(avroSchema::validateRecord)
            .build()
            .test();
    }

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testTopicIdStrategyAutoRegister() throws Exception {
        String topicName = TestUtils.generateTopic();
        //because of using TopicIdStrategy
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordapicurio1", List.of("key1"));

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withDeserializer(deserializer)
            .withStrategy(TopicIdStrategy.class)
            .withDataGenerator(avroSchema::generateRecord)
            .withDataValidator(avroSchema::validateRecord)
            .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
            .withAfterProduceValidator(() -> {
                return TestUtils.retry(() -> registryClient.getArtifactMetaData(null, artifactId) != null);
            })
            .build()
            .test();


        ArtifactMetaData meta = registryClient.getArtifactMetaData(null, artifactId);
        byte[] rawSchema = IoUtil.toBytes(registryClient.getContentByGlobalId(meta.getGlobalId()));

        assertEquals(new String(avroSchema.generateSchemaBytes()), new String(rawSchema));

    }

    @Test
    void testAvroSerDesFailDifferentSchemaByContent() throws Exception {
        String topicName = TestUtils.generateTopic();
        kafkaCluster.createTopic(topicName, 1, 1);

        String groupId = TestUtils.generateSubject();
        String artifactId = TestUtils.generateSubject();
        AvroGenericRecordSchemaFactory avroSchemaA = new AvroGenericRecordSchemaFactory(groupId, artifactId, List.of("keyA"));
        AvroGenericRecordSchemaFactory avroSchemaB = new AvroGenericRecordSchemaFactory(groupId, artifactId, List.of("keyB"));

        createArtifact(groupId, artifactId, ArtifactType.AVRO, avroSchemaA.generateSchemaStream());

        new WrongConfiguredSerdesTesterBuilder<GenericRecord>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withStrategy(RecordIdStrategy.class)
            //note, we use an incorrect wrong data generator in purpose
            .withDataGenerator(avroSchemaB::generateRecord)
            .build()
            .test();
    }

    @Test
    void testAvroSerDesFailDifferentSchemaByRecordName() throws Exception {
        String topicName = TestUtils.generateTopic();
        kafkaCluster.createTopic(topicName, 1, 1);

        String groupId = TestUtils.generateSubject();
        String artifactId = TestUtils.generateSubject();
        AvroGenericRecordSchemaFactory avroSchemaA = new AvroGenericRecordSchemaFactory(groupId, artifactId, List.of("keyA"));
        AvroGenericRecordSchemaFactory avroSchemaB = new AvroGenericRecordSchemaFactory(groupId, "notexistent", List.of("keyB"));

        createArtifact(groupId, artifactId, ArtifactType.AVRO, avroSchemaA.generateSchemaStream());

        new WrongConfiguredSerdesTesterBuilder<GenericRecord>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withStrategy(RecordIdStrategy.class)
            //note, we use an incorrect wrong data generator in purpose
            .withDataGenerator(avroSchemaB::generateRecord)
            .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true")
            .build()
            .test();
    }

    @Test
    void testWrongSchema() throws Exception {
        String topicName = TestUtils.generateSubject();
        kafkaCluster.createTopic(topicName, 1, 1);

        String groupId = TestUtils.generateSubject();
        String artifactId = topicName + "-value";
        AvroGenericRecordSchemaFactory avroSchemaA = new AvroGenericRecordSchemaFactory(groupId, "myrecord", List.of("keyA"));
        AvroGenericRecordSchemaFactory avroSchemaB = new AvroGenericRecordSchemaFactory(groupId, "myrecord", List.of("keyB"));

        createArtifact(groupId, artifactId, ArtifactType.AVRO, avroSchemaA.generateSchemaStream());

        new WrongConfiguredSerdesTesterBuilder<GenericRecord>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withStrategy(TopicIdStrategy.class)
            //note, we use an incorrect wrong data generator in purpose
            .withDataGenerator(avroSchemaB::generateRecord)
            .build()
            .test();
    }

    @Test
    void testArtifactNotFound() throws Exception {
        String topicName = TestUtils.generateSubject();
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("mygroup", "myrecord", List.of("keyB"));

        //note, we don't create any artifact

        new WrongConfiguredSerdesTesterBuilder<GenericRecord>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withStrategy(TopicIdStrategy.class)
            .withDataGenerator(avroSchema::generateRecord)
            .build()
            .test();
    }

    @Test
    void testEvolveAvroApicurio() throws Exception {
        evolveSchemaTest(false);
    }

    @Test
    @Tag(ACCEPTANCE)
    void testEvolveAvroApicurioReusingClient() throws Exception {
        evolveSchemaTest(true);
    }

    void evolveSchemaTest(boolean reuseClients) throws Exception {
        //using TopicRecordIdStrategy

        Class<?> strategy = TopicRecordIdStrategy.class;

        String topicName = TestUtils.generateTopic();
        kafkaCluster.createTopic(topicName, 1, 1);

        String recordNamespace = TestUtils.generateGroupId();
        String recordName = TestUtils.generateSubject();
        String schemaKey = "key1";
        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(recordNamespace, recordName, List.of(schemaKey));

        String artifactId = topicName + "-" + recordName;
        createArtifact(recordNamespace, artifactId, ArtifactType.AVRO, avroSchema.generateSchemaStream());

        SerdesTester<String, GenericRecord, GenericRecord> tester = new SerdesTester<>();
        if (reuseClients) {
            tester.setAutoClose(false);
        }

        int messageCount = 10;

        Producer<String, GenericRecord> producer = tester.createProducer(StringSerializer.class, AvroKafkaSerializer.class, topicName, strategy);
        Consumer<String, GenericRecord> consumer = tester.createConsumer(StringDeserializer.class, AvroKafkaDeserializer.class, topicName);

        tester.produceMessages(producer, topicName, avroSchema::generateRecord, messageCount);
        tester.consumeMessages(consumer, topicName, messageCount, avroSchema::validateRecord);

        String schemaKey2 = "key2";
        AvroGenericRecordSchemaFactory avroSchema2 = new AvroGenericRecordSchemaFactory(recordNamespace, recordName, List.of(schemaKey, schemaKey2));
        createArtifactVersion(recordNamespace, artifactId, avroSchema2.generateSchemaStream());

        if (!reuseClients) {
            producer = tester.createProducer(StringSerializer.class, AvroKafkaSerializer.class, topicName, strategy);
        }
        tester.produceMessages(producer, topicName, avroSchema2::generateRecord, messageCount);

        if (!reuseClients) {
            producer = tester.createProducer(StringSerializer.class, AvroKafkaSerializer.class, topicName, strategy);
        }
        tester.produceMessages(producer, topicName, avroSchema::generateRecord, messageCount);

        if (!reuseClients) {
            consumer = tester.createConsumer(StringDeserializer.class, AvroKafkaDeserializer.class, topicName);
        }
        {
            AtomicInteger schema1Counter = new AtomicInteger(0);
            AtomicInteger schema2Counter = new AtomicInteger(0);
            tester.consumeMessages(consumer, topicName, messageCount * 2, record -> {
                if (avroSchema.validateRecord(record)) {
                    schema1Counter.incrementAndGet();
                    return true;
                }
                if (avroSchema2.validateRecord(record)) {
                    schema2Counter.incrementAndGet();
                    return true;
                }
                return false;
            });
            assertEquals(schema1Counter.get(), schema2Counter.get());
        }

        String schemaKey3 = "key3";
        AvroGenericRecordSchemaFactory avroSchema3 = new AvroGenericRecordSchemaFactory(recordNamespace, recordName, List.of(schemaKey, schemaKey2, schemaKey3));
        createArtifactVersion(recordNamespace, artifactId, avroSchema3.generateSchemaStream());

        if (!reuseClients) {
            producer = tester.createProducer(StringSerializer.class, AvroKafkaSerializer.class, topicName, strategy);
        }
        tester.produceMessages(producer, topicName, avroSchema3::generateRecord, messageCount);

        if (!reuseClients) {
            producer = tester.createProducer(StringSerializer.class, AvroKafkaSerializer.class, topicName, strategy);
        }
        tester.produceMessages(producer, topicName, avroSchema2::generateRecord, messageCount);

        if (!reuseClients) {
            producer = tester.createProducer(StringSerializer.class, AvroKafkaSerializer.class, topicName, strategy);
        }
        tester.produceMessages(producer, topicName, avroSchema::generateRecord, messageCount);

        if (!reuseClients) {
            consumer = tester.createConsumer(StringDeserializer.class, AvroKafkaDeserializer.class, topicName);
        }
        {
            AtomicInteger schema1Counter = new AtomicInteger(0);
            AtomicInteger schema2Counter = new AtomicInteger(0);
            AtomicInteger schema3Counter = new AtomicInteger(0);
            tester.consumeMessages(consumer, topicName, messageCount * 3, record -> {
                if (avroSchema.validateRecord(record)) {
                    schema1Counter.incrementAndGet();
                    return true;
                }
                if (avroSchema2.validateRecord(record)) {
                    schema2Counter.incrementAndGet();
                    return true;
                }
                if (avroSchema3.validateRecord(record)) {
                    schema3Counter.incrementAndGet();
                    return true;
                }
                return false;
            });
            assertEquals(schema1Counter.get(), schema2Counter.get());
            assertEquals(schema1Counter.get(), schema3Counter.get());
        }

        IoUtil.closeIgnore(producer);
        IoUtil.closeIgnore(consumer);

    }

    @Test
    public void testAvroJSON() throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (AvroKafkaSerializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(registryClient);
             Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(registryClient)) {

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

            String artifactId = TestUtils.generateArtifactId();

            byte[] bytes = serializer.serialize(artifactId, record);

            // Test msg is stored as json, take 1st 9 bytes off (magic byte and long)
            JsonNode json = new ObjectMapper().readTree(Arrays.copyOfRange(bytes, 9, bytes.length));
            Assertions.assertEquals("somebar", json.get("bar").textValue());
//            JSONObject msgAsJson = new JSONObject(new String(Arrays.copyOfRange(bytes, 9, bytes.length)));
//            Assertions.assertEquals("somebar", msgAsJson.getString("bar"));

            // some impl details ...
            TestUtils.waitForSchema(globalId -> registryClient.getContentByGlobalId(globalId) != null, bytes);

            GenericData.Record ir = deserializer.deserialize(artifactId, bytes);

            Assertions.assertEquals(record, ir);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    @Test
    public void testAvroUsingHeaders() throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (AvroKafkaSerializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(registryClient);
             Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(registryClient)) {

            Map<String, String> config = new HashMap<>();
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            serializer.configure(config, false);

            config = new HashMap<>();
            deserializer.configure(config, false);

            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");

            String artifactId = TestUtils.generateArtifactId();
            Headers headers = new RecordHeaders();
            byte[] bytes = serializer.serialize(artifactId, headers, record);

            Assertions.assertNotNull(headers.lastHeader(SerdeHeaders.HEADER_VALUE_GLOBAL_ID));
            Header globalId = headers.lastHeader(SerdeHeaders.HEADER_VALUE_GLOBAL_ID);
            long id = ByteBuffer.wrap(globalId.value()).getLong();

            TestUtils.retry(() -> registryClient.getContentByGlobalId(id) != null);

            GenericData.Record ir = deserializer.deserialize(artifactId, headers, bytes);

            Assertions.assertEquals(record, ir);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    //TODO TEST avro specific record

    @Test
    @Tag(ACCEPTANCE)
    public void testReflectAutoRegister() throws Exception {
        String topicName = TestUtils.generateTopic();
        //because of using TopicIdStrategy
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        new SimpleSerdesTesterBuilder<TestObject, TestObject>()
            .withTopic(topicName)
            .withStrategy(TopicIdStrategy.class)
            .withSerializer(serializer)
            .withDeserializer(deserializer)
            .withDataGenerator(i -> new TestObject("Apicurio"))
            .withDataValidator(o -> "Apicurio".equals(o.getName()))
            .withProducerProperty(AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER, ReflectAvroDatumProvider.class.getName())
            .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
            .withConsumerProperty(AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER, ReflectAvroDatumProvider.class.getName())
            .withAfterProduceValidator(() -> {
                return TestUtils.retry(() -> registryClient.getArtifactMetaData(null, artifactId) != null);
            })
            .build()
            .test();

    }

}
