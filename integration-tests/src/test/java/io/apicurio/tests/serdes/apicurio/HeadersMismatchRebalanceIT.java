package io.apicurio.tests.serdes.apicurio;

import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.error.LoggingDeserializerErrorHandler;
import io.apicurio.registry.serde.kafka.config.KafkaSerdeConfig;
import io.apicurio.registry.serde.strategy.TopicIdStrategy;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.KafkaFacade;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.deployment.Constants.SERDES;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the failure mechanism behind
 * <a href="https://github.com/Apicurio/apicurio-registry/issues/3662">#3662</a> ("endless rebalance
 * loop"): a consumer configured with {@code apicurio.registry.headers.enabled} mismatched relative
 * to the producer receives a record it can never deserialize. Because the deserializer offers no
 * poison-pill handling, the same record is redelivered on every poll with no forward progress. Once
 * a realistic per-record backoff is layered on top (as most consumer applications do around a
 * failing record), the cumulative retry time exceeds {@code max.poll.interval.ms} and the consumer
 * is repeatedly evicted from its group and forced to rejoin.
 */
@Tag(SERDES)
@QuarkusIntegrationTest
public class HeadersMismatchRebalanceIT extends ApicurioRegistryBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeadersMismatchRebalanceIT.class);

    private final KafkaFacade kafkaCluster = KafkaFacade.getInstance();

    @BeforeAll
    void setupEnvironment() {
        kafkaCluster.startIfNeeded();
    }

    @AfterAll
    void teardownEnvironment() throws Exception {
        kafkaCluster.stopIfPossible();
    }

    @Test
    void headersMismatchCausesEndlessRebalance() throws Exception {
        String topicName = TestUtils.generateTopic();
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("poisonPillRecord",
                List.of("key1" + System.currentTimeMillis()));

        KafkaSerdesTester<String, GenericRecord, GenericRecord> tester = new KafkaSerdesTester<>();

        // Producer: headers enabled, matching the buggy 2.4.3-2.6.x default (id carried in headers,
        // payload has no magic-byte/id prefix).
        Properties producerProps = new Properties();
        producerProps.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
        producerProps.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
        Producer<String, GenericRecord> producer = tester.createProducer(producerProps,
                StringSerializer.class, AvroKafkaSerializer.class, topicName, TopicIdStrategy.class);
        tester.produceMessages(producer, topicName, avroSchema::generateRecord, 1, false);

        // Consumer: headers disabled, matching a client that only understands the payload-embedded id
        // (e.g. a pre-2.4.3 client, or any client that never opted into headers mode).
        Properties consumerProps = new Properties();
        consumerProps.put(KafkaSerdeConfig.ENABLE_HEADERS, "false");
        // Deliberately shorter than the per-record retry backoff below (2000ms) so that a realistic
        // app-level retry cadence around a poison-pill record reliably exceeds it, the same way a much
        // larger real-world max.poll.interval.ms (default 300000ms) would eventually be exceeded by a
        // slow/retried resolution call or a longer application backoff.
        consumerProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "1000");
        consumerProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "6000");
        consumerProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "1000");
        consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        Consumer<String, GenericRecord> consumer = tester.createConsumer(consumerProps,
                StringDeserializer.class, AvroKafkaDeserializer.class, topicName);

        // NOTE: onPartitionsRevoked also fires during a clean consumer.close(), so counting revocations
        // alone can't distinguish an involuntary broker-side eviction from a graceful shutdown. What
        // *can't* happen during a graceful shutdown (we only close once, after the loop below exits) is
        // a second onPartitionsAssigned callback -- that only happens if the group coordinator forced
        // this consumer out and it had to rejoin. So assignedCount >= 2, observed strictly *before* we
        // ever call close(), is the real signal of an involuntary rebalance.
        AtomicInteger assignedCount = new AtomicInteger();
        consumer.subscribe(Collections.singletonList(topicName), new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                LOGGER.info("Partitions revoked: {}", partitions);
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                if (!partitions.isEmpty()) {
                    int n = assignedCount.incrementAndGet();
                    LOGGER.info("Partitions assigned (assignment #{}): {}", n, partitions);
                }
            }
        });

        int failureCount = 0;
        long start = System.currentTimeMillis();
        long deadline = start + 60_000;
        while (assignedCount.get() < 2 && System.currentTimeMillis() < deadline) {
            try {
                consumer.poll(Duration.ofSeconds(2));
                LOGGER.info("poll() returned without error (unexpected for the poison-pill record)");
            } catch (Exception e) {
                failureCount++;
                LOGGER.info("poll() failed as expected ({}), t+{}ms: {}", failureCount,
                        System.currentTimeMillis() - start, e.getClass().getSimpleName());
                // Model a realistic consumer application: don't skip the offending record, just
                // back off briefly and retry. This backoff is what tips the cumulative retry time
                // over max.poll.interval.ms in real deployments.
                Thread.sleep(2000);
            }
        }
        int assignedBeforeClose = assignedCount.get();
        consumer.close(Duration.ofSeconds(5));

        assertTrue(failureCount > 0,
                "Expected the mismatched consumer to fail deserializing the poison-pill record at least once");
        assertTrue(assignedBeforeClose >= 2, "Expected the consumer to be involuntarily evicted from its "
                + "group and forced to rejoin (endless rebalance) before we ever closed it, but only "
                + "observed " + assignedBeforeClose + " assignment(s)");
    }

    /**
     * Same scenario as {@link #headersMismatchCausesEndlessRebalance()}, but with
     * {@link SerdeConfig#DESERIALIZER_ERROR_HANDLER} configured to skip unresolvable records. Proves
     * the fix: the consumer never fails to poll and is never evicted from its group.
     */
    @Test
    void configuredErrorHandlerPreventsEndlessRebalance() throws Exception {
        String topicName = TestUtils.generateTopic();
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("poisonPillRecord",
                List.of("key1" + System.currentTimeMillis()));

        KafkaSerdesTester<String, GenericRecord, GenericRecord> tester = new KafkaSerdesTester<>();

        Properties producerProps = new Properties();
        producerProps.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
        producerProps.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
        Producer<String, GenericRecord> producer = tester.createProducer(producerProps,
                StringSerializer.class, AvroKafkaSerializer.class, topicName, TopicIdStrategy.class);
        tester.produceMessages(producer, topicName, avroSchema::generateRecord, 1, false);

        Properties consumerProps = new Properties();
        consumerProps.put(KafkaSerdeConfig.ENABLE_HEADERS, "false");
        consumerProps.put(SerdeConfig.DESERIALIZER_ERROR_HANDLER, LoggingDeserializerErrorHandler.class.getName());
        consumerProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "1000");
        consumerProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "6000");
        consumerProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "1000");
        consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        Consumer<String, GenericRecord> consumer = tester.createConsumer(consumerProps,
                StringDeserializer.class, AvroKafkaDeserializer.class, topicName);

        AtomicInteger assignedCount = new AtomicInteger();
        consumer.subscribe(Collections.singletonList(topicName), new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                LOGGER.info("Partitions revoked: {}", partitions);
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                if (!partitions.isEmpty()) {
                    int n = assignedCount.incrementAndGet();
                    LOGGER.info("Partitions assigned (assignment #{}): {}", n, partitions);
                }
            }
        });

        int failureCount = 0;
        long start = System.currentTimeMillis();
        // Long enough to comfortably cover several multiples of the endless-rebalance cadence
        // observed in headersMismatchCausesEndlessRebalance() with the same max.poll.interval.ms.
        long deadline = start + 20_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                consumer.poll(Duration.ofSeconds(2));
            } catch (Exception e) {
                failureCount++;
                LOGGER.info("Unexpected poll() failure ({}): {}", failureCount, e.getClass().getSimpleName());
            }
        }
        int assignedBeforeClose = assignedCount.get();
        consumer.close(Duration.ofSeconds(5));

        assertEquals(0, failureCount,
                "Expected poll() to never fail once the poison-pill record is skipped by the configured handler");
        assertEquals(1, assignedBeforeClose,
                "Expected exactly the initial assignment and no involuntary rebalance once the poison-pill "
                        + "record is skipped by the configured handler");
    }
}
