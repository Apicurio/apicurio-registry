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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.serde.SerdeConfigKeys;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
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

        createArtifact(topicName, artifactId, ArtifactType.AVRO, avroSchema.generateSchemaStream());

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
            .withProducerProperty(SerdeConfigKeys.AUTO_REGISTER_ARTIFACT, "true")
            .withAfterProduceValidator(() -> {
                return TestUtils.retry(() -> registryClient.getArtifactMetaData(topicName, artifactId) != null);
            })
            .build()
            .test();


        ArtifactMetaData meta = registryClient.getArtifactMetaData(topicName, artifactId);
        byte[] rawSchema = IoUtil.toBytes(registryClient.getContentByGlobalId(meta.getGlobalId()));

        assertEquals(new String(avroSchema.generateSchemaBytes()), new String(rawSchema));

    }

    @Test
    void testAvroSerDesFail() throws Exception {
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

}
