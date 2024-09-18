package io.apicurio.tests.serdes.apicurio.pulsar;

import io.apicurio.registry.serde.avro.AvroPulsarSerde;
import io.apicurio.registry.serde.avro.AvroPulsarSerdeSchema;
import io.apicurio.registry.serde.avro.strategy.TopicRecordIdStrategy;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.serdes.apicurio.AvroGenericRecordSchemaFactory;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.PulsarFacade;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.apache.avro.generic.GenericRecord;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Producer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag(Constants.SERDES)
@QuarkusIntegrationTest
public class AvroPulsarSerdeIT extends ApicurioRegistryBaseIT {

    private final PulsarFacade pulsarFacade = PulsarFacade.getInstance();

    @BeforeAll
    void setupEnvironment() {
        pulsarFacade.startIfNeeded();
    }

    @AfterAll
    void teardownEnvironment() throws Exception {
        pulsarFacade.stopIfPossible();
    }

    /*
     * This tests creates three different versions for the same schema, producing a few messages for each
     * version and consuming them.
     */
    @Test
    void testEvolveAvroApicurio() throws Exception {
        // Prepare the topic and the first schema
        // using TopicRecordIdStrategy
        String topicName = TestUtils.generateTopic();

        PulsarSerdesTester<GenericRecord, GenericRecord> tester = new PulsarSerdesTester<>();
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

        AvroPulsarSerde<GenericRecord> avroPulsarSerde = new AvroPulsarSerde<>(topicName);
        Map<String, Object> configs = Map.of(SerdeConfig.REGISTRY_URL,
                ApicurioRegistryBaseIT.getRegistryV3ApiUrl(), SerdeConfig.ARTIFACT_RESOLVER_STRATEGY,
                TopicRecordIdStrategy.class.getName());
        avroPulsarSerde.configure(new SerdeConfig(configs), false);

        AvroPulsarSerdeSchema<GenericRecord> avroPulsarSerdeSchema = new AvroPulsarSerdeSchema<>(
                avroPulsarSerde);

        // Create the initial producer and consumer.
        // Create a producer using the custom schema
        Producer<GenericRecord> producer = pulsarFacade.pulsarClient().newProducer(avroPulsarSerdeSchema)
                .topic(topicName).create();

        // Create a pulsar consumer
        Consumer<GenericRecord> consumer = pulsarFacade.pulsarClient().newConsumer(avroPulsarSerdeSchema)
                .subscriptionName(topicName + "_subscription").topic(topicName).subscribe();

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
        tester.produceMessages(producer, topicName, avroSchema2::generateRecord, messageCount, true);
        tester.produceMessages(producer, topicName, avroSchema::generateRecord, messageCount, true);

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

        tester.produceMessages(producer, topicName, avroSchema3::generateRecord, messageCount, true);
        tester.produceMessages(producer, topicName, avroSchema2::generateRecord, messageCount, true);
        tester.produceMessages(producer, topicName, avroSchema::generateRecord, messageCount, true);

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
}
