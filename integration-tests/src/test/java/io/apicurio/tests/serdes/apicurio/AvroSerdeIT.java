package io.apicurio.tests.serdes.apicurio;

import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.ReflectAvroDatumProvider;
import io.apicurio.registry.serde.avro.strategy.RecordIdStrategy;
import io.apicurio.registry.serde.avro.strategy.TopicRecordIdStrategy;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.serde.strategy.TopicIdStrategy;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.registry.utils.tests.TooManyRequestsMock;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.serdes.TestObject;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.KafkaFacade;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag(Constants.SERDES)
@QuarkusIntegrationTest
public class AvroSerdeIT extends ApicurioRegistryBaseIT {

    private final KafkaFacade kafkaCluster = KafkaFacade.getInstance();

    private final Class<AvroKafkaSerializer> serializer = AvroKafkaSerializer.class;
    private final Class<AvroKafkaDeserializer> deserializer = AvroKafkaDeserializer.class;

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

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordapicurio1",
                List.of("key1" + System.currentTimeMillis()));

        String avroSchemaString = avroSchema.generateSchema().toString();
        logger.info("Registering Avro Schema: {}", avroSchemaString);

        createArtifact("default", artifactId, ArtifactType.AVRO, avroSchemaString,
                ContentTypes.APPLICATION_JSON, null, null);

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>().withTopic(topicName)
                .withSerializer(serializer).withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true")
                .withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withDataGenerator(avroSchema::generateRecord).withDataValidator(avroSchema::validateRecord)
                .build().test();
    }

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testSimpleTopicIdStrategyFindLatest() throws Exception {
        String topicName = TestUtils.generateTopic();
        String artifactId = topicName;
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordapicurio1",
                List.of("key1" + System.currentTimeMillis()));

        String avroSchemaString = avroSchema.generateSchema().toString();
        logger.info("Registering Avro Schema: {}", avroSchemaString);

        createArtifact(topicName, artifactId, ArtifactType.AVRO, avroSchemaString,
                ContentTypes.APPLICATION_JSON, null, null);

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>().withTopic(topicName)
                .withSerializer(serializer).withDeserializer(deserializer)
                .withStrategy(SimpleTopicIdStrategy.class)
                .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true")
                .withDataGenerator(avroSchema::generateRecord).withDataValidator(avroSchema::validateRecord)
                .withProducerProperty(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, topicName).build().test();
    }

    @Test
    void testRecordIdStrategydFindLatest() throws Exception {
        String topicName = TestUtils.generateTopic();
        kafkaCluster.createTopic(topicName, 1, 1);

        String groupId = TestUtils.generateSubject();
        String artifactId = TestUtils.generateSubject();
        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(groupId, artifactId,
                List.of("key1" + System.currentTimeMillis()));

        String avroSchemaString = avroSchema.generateSchema().toString();
        logger.info("Registering Avro Schema: {}", avroSchemaString);

        createArtifact(groupId, artifactId, ArtifactType.AVRO, avroSchemaString,
                ContentTypes.APPLICATION_JSON, null, null);

        SimpleSerdesTesterBuilder<GenericRecord, GenericRecord> tester = new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>()
                .withTopic(topicName).withSerializer(serializer)
                .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true").withDeserializer(deserializer)
                .withStrategy(RecordIdStrategy.class).withDataGenerator(avroSchema::generateRecord)
                .withDataValidator(avroSchema::validateRecord);

        tester.build().test();
    }

    @Test
    void testTopicRecordIdStrategydFindLatest() throws Exception {
        String topicName = TestUtils.generateTopic();
        kafkaCluster.createTopic(topicName, 1, 1);

        String groupId = TestUtils.generateSubject();
        String recordName = TestUtils.generateSubject();
        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(groupId, recordName,
                List.of("key1" + System.currentTimeMillis()));

        String artifactId = topicName + "-" + recordName;

        String avroSchemaString = avroSchema.generateSchema().toString();
        logger.info("Registering Avro Schema: {}", avroSchemaString);

        createArtifact(groupId, artifactId, ArtifactType.AVRO, avroSchemaString,
                ContentTypes.APPLICATION_JSON, null, null);

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>().withTopic(topicName)
                .withSerializer(serializer).withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true")
                .withDeserializer(deserializer).withStrategy(TopicRecordIdStrategy.class)
                .withDataGenerator(avroSchema::generateRecord).withDataValidator(avroSchema::validateRecord)
                .build().test();
    }

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testTopicIdStrategyAutoRegister() throws Exception {
        String topicName = TestUtils.generateTopic();
        // because of using TopicIdStrategy
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordapicurio1",
                List.of("key1" + System.currentTimeMillis()));

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>().withTopic(topicName)
                .withSerializer(serializer).withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withDataGenerator(avroSchema::generateRecord).withDataValidator(avroSchema::validateRecord)
                .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
                .withAfterProduceValidator(() -> {
                    return TestUtils.retry(() -> {
                        VersionMetaData meta = registryClient.groups().byGroupId("default").artifacts()
                                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest")
                                .get();
                        registryClient.ids().globalIds().byGlobalId(meta.getGlobalId()).get();
                        return true;
                    });
                }).build().test();

        VersionMetaData meta = registryClient.groups().byGroupId("default").artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
        byte[] rawSchema = IoUtil
                .toBytes(registryClient.ids().globalIds().byGlobalId(meta.getGlobalId()).get());

        assertEquals(new String(avroSchema.generateSchemaBytes()), new String(rawSchema));

    }

    @Test
    void testAvroSerDesFailDifferentSchemaByContent() throws Exception {
        String topicName = TestUtils.generateTopic();
        kafkaCluster.createTopic(topicName, 1, 1);

        String groupId = TestUtils.generateSubject();
        String artifactId = TestUtils.generateSubject();
        AvroGenericRecordSchemaFactory avroSchemaA = new AvroGenericRecordSchemaFactory(groupId, artifactId,
                List.of("keyA"));
        AvroGenericRecordSchemaFactory avroSchemaB = new AvroGenericRecordSchemaFactory(groupId, artifactId,
                List.of("keyB"));

        createArtifact(groupId, artifactId, ArtifactType.AVRO, avroSchemaA.generateSchema().toString(),
                ContentTypes.APPLICATION_JSON, null, null);

        new WrongConfiguredSerdesTesterBuilder<GenericRecord>().withTopic(topicName)
                .withSerializer(serializer).withStrategy(RecordIdStrategy.class)
                // note, we use an incorrect wrong data generator in purpose
                .withDataGenerator(avroSchemaB::generateRecord).build().test();
    }

    @Test
    void testAvroSerDesFailDifferentSchemaByRecordName() throws Exception {
        String topicName = TestUtils.generateTopic();
        kafkaCluster.createTopic(topicName, 1, 1);

        String groupId = TestUtils.generateSubject();
        String artifactId = TestUtils.generateSubject();
        AvroGenericRecordSchemaFactory avroSchemaA = new AvroGenericRecordSchemaFactory(groupId, artifactId,
                List.of("keyA"));
        AvroGenericRecordSchemaFactory avroSchemaB = new AvroGenericRecordSchemaFactory(groupId,
                "notexistent", List.of("keyB"));

        createArtifact(groupId, artifactId, ArtifactType.AVRO, avroSchemaA.generateSchema().toString(),
                ContentTypes.APPLICATION_JSON, null, null);

        new WrongConfiguredSerdesTesterBuilder<GenericRecord>().withTopic(topicName)
                .withSerializer(serializer).withStrategy(RecordIdStrategy.class)
                // note, we use an incorrect wrong data generator in purpose
                .withDataGenerator(avroSchemaB::generateRecord)
                .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true").build().test();
    }

    @Test
    void testWrongSchema() throws Exception {
        String topicName = TestUtils.generateSubject();
        kafkaCluster.createTopic(topicName, 1, 1);

        String groupId = TestUtils.generateSubject();
        String artifactId = topicName + "-value";
        AvroGenericRecordSchemaFactory avroSchemaA = new AvroGenericRecordSchemaFactory(groupId, "myrecord",
                List.of("keyA"));
        AvroGenericRecordSchemaFactory avroSchemaB = new AvroGenericRecordSchemaFactory(groupId, "myrecord",
                List.of("keyB"));

        createArtifact(groupId, artifactId, ArtifactType.AVRO, avroSchemaA.generateSchema().toString(),
                ContentTypes.APPLICATION_JSON, null, null);

        new WrongConfiguredSerdesTesterBuilder<GenericRecord>().withTopic(topicName)
                .withSerializer(serializer).withStrategy(TopicIdStrategy.class)
                .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true")
                // note, we use an incorrect wrong data generator in purpose
                .withDataGenerator(avroSchemaB::generateRecord).build().test();
    }

    @Test
    void testArtifactNotFound() throws Exception {
        String topicName = TestUtils.generateSubject();
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("mygroup", "myrecord",
                List.of("keyB"));

        // note, we don't create any artifact

        new WrongConfiguredSerdesTesterBuilder<GenericRecord>().withTopic(topicName)
                .withSerializer(serializer).withStrategy(TopicIdStrategy.class)
                .withDataGenerator(avroSchema::generateRecord).build().test();
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

    /*
     * This tests creates three different versions for the same schema, producing a few messages for each
     * version and consuming them.
     */
    void evolveSchemaTest(boolean reuseClients) throws Exception {
        // Prepare the topic and the first schema
        // using TopicRecordIdStrategy
        Class<?> strategy = TopicRecordIdStrategy.class;
        String topicName = TestUtils.generateTopic();
        kafkaCluster.createTopic(topicName, 1, 1);

        SerdesTester<String, GenericRecord, GenericRecord> tester = new SerdesTester<>();
        int messageCount = 10;

        String recordNamespace = TestUtils.generateAvroNS();
        String recordName = TestUtils.generateSubject();
        String schemaKey = "key1" + System.currentTimeMillis();

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(recordNamespace,
                recordName, List.of(schemaKey));

        String artifactId = topicName + "-" + recordName;

        String avroSchemaString = avroSchema.generateSchema().toString();

        logger.info("Registering Avro Schema: {}", avroSchemaString);

        // First we create the initial artifact
        createArtifact(recordNamespace, artifactId, ArtifactType.AVRO, avroSchemaString,
                ContentTypes.APPLICATION_JSON, null, null);

        if (reuseClients) {
            tester.setAutoClose(false);
        }

        // Create the initial producer and consumer.
        Producer<String, GenericRecord> producer = tester.createProducer(StringSerializer.class,
                AvroKafkaSerializer.class, topicName, strategy);
        Consumer<String, GenericRecord> consumer = tester.createConsumer(StringDeserializer.class,
                AvroKafkaDeserializer.class, topicName);

        // Produce and consume messages for the first version of the schema.
        tester.produceMessages(producer, topicName, avroSchema::generateRecord, messageCount, true);
        tester.consumeMessages(consumer, topicName, messageCount, avroSchema::validateRecord);

        // Prepare the second version of the schema, for it to be different, a new field is added.
        String schemaKey2 = "key2" + System.currentTimeMillis();
        AvroGenericRecordSchemaFactory avroSchema2 = new AvroGenericRecordSchemaFactory(recordNamespace,
                recordName, List.of(schemaKey, schemaKey2));

        String avroSchemaString2 = avroSchema2.generateSchema().toString();
        logger.info("Registering Avro Schema 2: {}", avroSchemaString2);

        createArtifactVersion(recordNamespace, artifactId, avroSchemaString2, ContentTypes.APPLICATION_JSON,
                null);

        // We produce messages for both, the old schema version, and the new one.
        produceForSchema(reuseClients, producer, tester, strategy, topicName, messageCount, avroSchema2);
        produceForSchema(reuseClients, producer, tester, strategy, topicName, messageCount, avroSchema);

        if (!reuseClients) {
            consumer = tester.createConsumer(StringDeserializer.class, AvroKafkaDeserializer.class,
                    topicName);
        }
        // Messages for both schemas are consumed, expecting the same number.
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

        String schemaKey3 = "key3" + System.currentTimeMillis();
        AvroGenericRecordSchemaFactory avroSchema3 = new AvroGenericRecordSchemaFactory(recordNamespace,
                recordName, List.of(schemaKey, schemaKey2, schemaKey3));

        String avroSchemaString3 = avroSchema3.generateSchema().toString();
        logger.info("Registering Avro Schema 3: {}", avroSchemaString3);

        createArtifactVersion(recordNamespace, artifactId, avroSchemaString3, ContentTypes.APPLICATION_JSON,
                null);

        produceForSchema(reuseClients, producer, tester, strategy, topicName, messageCount, avroSchema3);
        produceForSchema(reuseClients, producer, tester, strategy, topicName, messageCount, avroSchema2);
        produceForSchema(reuseClients, producer, tester, strategy, topicName, messageCount, avroSchema);

        if (!reuseClients) {
            consumer = tester.createConsumer(StringDeserializer.class, AvroKafkaDeserializer.class,
                    topicName);
        }

        // Consume messages from the topic, we must have the sam number of messages for each separate artifact
        // version
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

    /**
     * @param reuseClients whether to reuse the producer
     * @param producer the producer to be used
     * @param tester the producer and consumer building
     * @param strategy the class strategy to be used for resolving the schemas
     * @param topicName the name of the topic to be used
     * @param messageCount how many messages have to be created
     * @param avroSchema the avro schema to use to serialize the messages
     * @return
     * @throws Exception
     */
    private Producer<String, GenericRecord> produceForSchema(boolean reuseClients,
            Producer<String, GenericRecord> producer,
            SerdesTester<String, GenericRecord, GenericRecord> tester, Class<?> strategy, String topicName,
            int messageCount, AvroGenericRecordSchemaFactory avroSchema) throws Exception {
        if (!reuseClients) {
            producer = tester.createProducer(StringSerializer.class, AvroKafkaSerializer.class, topicName,
                    strategy);
        }
        tester.produceMessages(producer, topicName, avroSchema::generateRecord, messageCount, true);

        return producer;
    }

    @Test
    void testAvroConfluentForMultipleTopics() throws Exception {
        Class<?> strategy = RecordIdStrategy.class;

        String topicName1 = TestUtils.generateTopic();
        String topicName2 = TestUtils.generateTopic();
        String topicName3 = TestUtils.generateTopic();
        String subjectName = "myrecordconfluent";
        String schemaKey = "key1" + System.currentTimeMillis();

        kafkaCluster.createTopic(topicName1, 1, 1);
        kafkaCluster.createTopic(topicName2, 1, 1);
        kafkaCluster.createTopic(topicName3, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(subjectName,
                List.of(schemaKey));

        String avroSchemaString = avroSchema.generateSchema().toString();

        logger.info("Registering Avro Schema: {}", avroSchemaString);

        createArtifact("default", subjectName, ArtifactType.AVRO, avroSchemaString,
                ContentTypes.APPLICATION_JSON, null, null);

        SerdesTester<String, GenericRecord, GenericRecord> tester = new SerdesTester<>();

        int messageCount = 10;

        Producer<String, GenericRecord> producer1 = tester.createProducer(StringSerializer.class,
                AvroKafkaSerializer.class, topicName1, strategy);
        Producer<String, GenericRecord> producer2 = tester.createProducer(StringSerializer.class,
                AvroKafkaSerializer.class, topicName2, strategy);
        Producer<String, GenericRecord> producer3 = tester.createProducer(StringSerializer.class,
                AvroKafkaSerializer.class, topicName3, strategy);
        Consumer<String, GenericRecord> consumer1 = tester.createConsumer(StringDeserializer.class,
                AvroKafkaDeserializer.class, topicName1);
        Consumer<String, GenericRecord> consumer2 = tester.createConsumer(StringDeserializer.class,
                AvroKafkaDeserializer.class, topicName2);
        Consumer<String, GenericRecord> consumer3 = tester.createConsumer(StringDeserializer.class,
                AvroKafkaDeserializer.class, topicName3);

        tester.produceMessages(producer1, topicName1, avroSchema::generateRecord, messageCount, true);
        tester.produceMessages(producer2, topicName2, avroSchema::generateRecord, messageCount, true);
        tester.produceMessages(producer3, topicName3, avroSchema::generateRecord, messageCount, true);

        tester.consumeMessages(consumer1, topicName1, messageCount, avroSchema::validateRecord);
        tester.consumeMessages(consumer2, topicName2, messageCount, avroSchema::validateRecord);
        tester.consumeMessages(consumer3, topicName3, messageCount, avroSchema::validateRecord);

    }

    @Test
    public void testAvroJSON() throws Exception {
        String topicName = TestUtils.generateTopic();
        // because of using TopicIdStrategy
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecord3",
                List.of("bar"));

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>().withTopic(topicName)
                .withSerializer(serializer).withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withDataGenerator(avroSchema::generateRecord).withDataValidator(avroSchema::validateRecord)
                .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
                .withProducerProperty(AvroKafkaSerdeConfig.AVRO_ENCODING,
                        AvroKafkaSerdeConfig.AVRO_ENCODING_JSON)
                .withConsumerProperty(AvroKafkaSerdeConfig.AVRO_ENCODING,
                        AvroKafkaSerdeConfig.AVRO_ENCODING_JSON)
                .withAfterProduceValidator(() -> {
                    return TestUtils.retry(() -> {
                        VersionMetaData meta = registryClient.groups().byGroupId("default").artifacts()
                                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest")
                                .get();
                        registryClient.ids().globalIds().byGlobalId(meta.getGlobalId()).get();
                        return true;
                    });
                }).build().test();
    }

    // TODO TEST avro specific record

    @Test
    @Tag(ACCEPTANCE)
    public void testReflectAutoRegister() throws Exception {
        String topicName = TestUtils.generateTopic();
        // because of using TopicIdStrategy
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        new SimpleSerdesTesterBuilder<TestObject, TestObject>().withTopic(topicName)
                .withStrategy(TopicIdStrategy.class).withSerializer(serializer).withDeserializer(deserializer)
                .withDataGenerator(i -> new TestObject("Apicurio"))
                .withDataValidator(o -> "Apicurio".equals(o.getName()))
                .withProducerProperty(AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER,
                        ReflectAvroDatumProvider.class.getName())
                .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
                .withConsumerProperty(AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER,
                        ReflectAvroDatumProvider.class.getName())
                .withAfterProduceValidator(() -> {
                    return TestUtils.retry(() -> {
                        VersionMetaData meta = registryClient.groups().byGroupId("default").artifacts()
                                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest")
                                .get();
                        registryClient.ids().globalIds().byGlobalId(meta.getGlobalId()).get();
                        return true;
                    });
                }).build().test();

    }

    // test use globalId headers
    @Test
    void testGlobalIdInHeaders() throws Exception {
        String topicName = TestUtils.generateTopic();
        // because of using TopicIdStrategy
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordapicurio1",
                List.of("key1" + System.currentTimeMillis()));

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>().withTopic(topicName)
                .withSerializer(serializer).withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withDataGenerator(avroSchema::generateRecord).withDataValidator(avroSchema::validateRecord)
                .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
                .withProducerProperty(SerdeConfig.USE_ID, IdOption.globalId.name())
                .withConsumerProperty(SerdeConfig.USE_ID, IdOption.globalId.name())
                .withAfterProduceValidator(() -> {
                    return TestUtils.retry(() -> {
                        VersionMetaData meta = registryClient.groups().byGroupId("default").artifacts()
                                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest")
                                .get();
                        registryClient.ids().contentIds().byContentId(meta.getContentId()).get();
                        return true;
                    });
                }).build().test();

    }

    // test use globalId magic byte
    @Test
    void testGlobalIdInBody() throws Exception {
        String topicName = TestUtils.generateTopic();
        // because of using TopicIdStrategy
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordapicurio1",
                List.of("key1" + System.currentTimeMillis()));

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>().withTopic(topicName)
                .withSerializer(serializer).withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withDataGenerator(avroSchema::generateRecord).withDataValidator(avroSchema::validateRecord)
                .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
                .withProducerProperty(SerdeConfig.USE_ID, IdOption.globalId.name())
                .withConsumerProperty(SerdeConfig.USE_ID, IdOption.globalId.name())
                .withAfterProduceValidator(() -> {
                    return TestUtils.retry(() -> {
                        VersionMetaData meta = registryClient.groups().byGroupId("default").artifacts()
                                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest")
                                .get();
                        registryClient.ids().globalIds().byGlobalId(meta.getGlobalId()).get();
                        return true;
                    });
                }).build().test();

    }

    // disabled because the setup process to have an artifact with different globalId/contentId is not
    // reliable
    @Disabled
    // test producer use globalId, consumer default
    @Test
    void testProducerUsesGlobalIdConsumerUsesDefault() throws Exception {
        String topicName = TestUtils.generateTopic();
        // because of using TopicIdStrategy
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        // create several artifacts before to ensure the globalId and contentId are not the same
        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordapicurioz",
                List.of("keyz"));
        // create a duplicated artifact beforehand with the same content to force the contentId and globalId
        // sequences to return different ids

        String avroSchemaString = avroSchema.generateSchema().toString();

        logger.info("Registering Avro Schema: {}", avroSchemaString);

        createArtifact("default", TestUtils.generateArtifactId(), ArtifactType.AVRO, avroSchemaString,
                ContentTypes.APPLICATION_JSON, null, null);

        new WrongConfiguredConsumerTesterBuilder<GenericRecord, GenericRecord>().withTopic(topicName)
                .withSerializer(serializer).withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withDataGenerator(avroSchema::generateRecord).withDataValidator(avroSchema::validateRecord)
                .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
                .withProducerProperty(SerdeConfig.USE_ID, IdOption.globalId.name())
                .withAfterProduceValidator(() -> {
                    return TestUtils.retry(() -> {
                        VersionMetaData meta = registryClient.groups().byGroupId("default").artifacts()
                                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest")
                                .get();
                        registryClient.ids().contentIds().byContentId(meta.getContentId()).get();
                        return true;
                    });
                }).build().test();

    }

    // disabled because the setup process to have an artifact with different globalId/contentId is not
    // reliable
    @Disabled
    // test producer use default, consumer use globalId
    @Test
    void testProducerUsesDefaultConsumerUsesGlobalId() throws Exception {
        String topicName = TestUtils.generateTopic();
        // because of using TopicIdStrategy
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        // create artifact before to ensure the globalId and contentId are not the same
        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordapicurioz",
                List.of("keyz"));
        // create a duplicated artifact beforehand with the same content to force the contentId and globalId
        // sequences to return different ids

        String avroSchemaString = avroSchema.generateSchema().toString();
        logger.info("Registering Avro Schema: {}", avroSchemaString);

        createArtifact("default", TestUtils.generateArtifactId(), ArtifactType.AVRO, avroSchemaString,
                ContentTypes.APPLICATION_JSON, null, null);

        new WrongConfiguredConsumerTesterBuilder<GenericRecord, GenericRecord>().withTopic(topicName)
                .withSerializer(serializer).withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withDataGenerator(avroSchema::generateRecord).withDataValidator(avroSchema::validateRecord)
                .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
                .withConsumerProperty(SerdeConfig.USE_ID, IdOption.globalId.name())
                .withAfterProduceValidator(() -> {
                    return TestUtils.retry(() -> {
                        VersionMetaData meta = registryClient.groups().byGroupId("default").artifacts()
                                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest")
                                .get();
                        registryClient.ids().contentIds().byContentId(meta.getContentId()).get();
                        return true;
                    });
                }).build().test();

    }

    /**
     * From issue https://github.com/Apicurio/apicurio-registry/issues/1479
     *
     * @throws Exception
     */
    @Test
    void testFirstEmptyFieldConfusedAsMagicByte() throws Exception {

        String s = "{\n" + "    \"type\": \"record\",\n" + "    \"name\": \"userInfo\",\n"
                + "    \"namespace\": \"my.example\",\n" + "    \"fields\": [\n" + "        {\n"
                + "            \"name\": \"username\",\n"
                + "            \"type\": [\"null\", { \"type\": \"string\"} ]\n" + "        }" + "    ]\n"
                + "} ";

        String topicName = TestUtils.generateTopic();
        String artifactId = topicName;
        kafkaCluster.createTopic(topicName, 1, 1);

        Schema schema = new Schema.Parser().parse(s);

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>().withTopic(topicName)
                .withSerializer(serializer).withDeserializer(deserializer)
                .withStrategy(SimpleTopicIdStrategy.class).withDataGenerator((c) -> {
                    GenericRecord record = new GenericData.Record(schema);
                    if (c != 0 && (c % 2) == 0) {
                        record.put("username", "value-" + c);
                    }
                    return record;
                }).withDataValidator((record) -> {
                    return schema.equals(record.getSchema());
                }).withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
                .withAfterProduceValidator(() -> {
                    return TestUtils.retry(() -> {
                        VersionMetaData meta = registryClient.groups().byGroupId("default").artifacts()
                                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest")
                                .get();
                        registryClient.ids().globalIds().byGlobalId(meta.getGlobalId()).get();
                        return true;
                    });
                }).build().test();

    }

    @Test
    void testFirstRequestFailsRateLimited() throws Exception {

        TooManyRequestsMock mock = new TooManyRequestsMock();

        mock.start();
        try {
            String topicName = TestUtils.generateTopic();
            kafkaCluster.createTopic(topicName, 1, 1);

            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("mygroup",
                    "myrecord", List.of("keyB"));

            new WrongConfiguredSerdesTesterBuilder<GenericRecord>().withTopic(topicName)

                    // mock url that will return 429 status always
                    .withProducerProperty(SerdeConfig.REGISTRY_URL, mock.getMockUrl())
                    .withSerializer(AvroKafkaSerializer.class).withStrategy(TopicIdStrategy.class)
                    .withDataGenerator(avroSchema::generateRecord).build().test();
        } finally {
            mock.stop();
        }

    }

}
