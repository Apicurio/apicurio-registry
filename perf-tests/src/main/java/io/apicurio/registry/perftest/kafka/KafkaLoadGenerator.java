package io.apicurio.registry.perftest.kafka;

import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone Kafka producer/consumer load generator used by the perf-main workflow to simulate a
 * real Kafka application talking to the registry through the Avro serde, rather than only
 * exercising the REST API directly (as {@code RegistryApiSimulation} does). This mirrors how the
 * registry is actually consumed in production: schema registration/lookup performed transparently
 * by the serde as part of producing/consuming Kafka records.
 *
 * <p>All configuration is via environment variables so it can run unmodified against any
 * deployment:
 *
 * <ul>
 *   <li>{@code REGISTRY_URL} - base URL of the registry REST API
 *   <li>{@code KAFKA_BOOTSTRAP_SERVERS} - Kafka bootstrap servers
 *   <li>{@code AUTH_TOKEN_ENDPOINT} / {@code AUTH_CLIENT_ID} / {@code AUTH_CLIENT_SECRET} - OAuth2
 *       client-credentials used by the serde to authenticate to the registry (same mechanism as
 *       {@code examples/simple-json})
 *   <li>{@code PERF_DURATION_SECONDS} - how long to run before shutting down (default 120)
 *   <li>{@code PERF_PRODUCE_RATE_PER_SEC} - target produce rate (default 20)
 * </ul>
 *
 * <p>Run standalone with {@code java -jar apicurio-registry-perf-tests-*-jar-with-dependencies.jar}.
 */
public class KafkaLoadGenerator {

    private static final Logger log = LoggerFactory.getLogger(KafkaLoadGenerator.class);

    private static final String REGISTRY_URL = envOrDefault("REGISTRY_URL",
            "http://localhost:8080/apis/registry/v3");
    private static final String BOOTSTRAP_SERVERS = envOrDefault("KAFKA_BOOTSTRAP_SERVERS",
            "localhost:9092");
    private static final String TOPIC_NAME = "perf-test-topic";

    private static final int DURATION_SECONDS = Integer
            .parseInt(envOrDefault("PERF_DURATION_SECONDS", "120"));
    private static final int PRODUCE_RATE_PER_SEC = Integer
            .parseInt(envOrDefault("PERF_PRODUCE_RATE_PER_SEC", "20"));

    private static final String SCHEMA = "{\"type\":\"record\",\"name\":\"PerfTestKafkaRecord\","
            + "\"namespace\":\"io.apicurio.registry.perftest\","
            + "\"fields\":[{\"name\":\"seq\",\"type\":\"long\"},{\"name\":\"payload\",\"type\":\"string\"}]}";

    private static final AtomicLong PRODUCED = new AtomicLong();
    private static final AtomicLong CONSUMED = new AtomicLong();
    private static final AtomicLong PRODUCE_FAILURES = new AtomicLong();

    private static String envOrDefault(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? def : v;
    }

    public static void main(String[] args) throws Exception {
        boolean ok = run();
        if (!ok) {
            System.exit(1);
        }
    }

    /**
     * Runs the produce/consume load for {@code PERF_DURATION_SECONDS} and returns whether it
     * completed within acceptable failure bounds. Safe to call from another JVM entry point (see
     * {@code PerfTestRunner}) as well as standalone via {@link #main(String[])}.
     */
    public static boolean run() throws Exception {
        log.info("Starting Kafka load generator: registry={}, bootstrap={}, duration={}s, rate={}/s",
                REGISTRY_URL, BOOTSTRAP_SERVERS, DURATION_SECONDS, PRODUCE_RATE_PER_SEC);

        CountDownLatch done = new CountDownLatch(1);
        Producer<Object, Object> producer = createProducer();
        KafkaConsumer<Object, GenericRecord> consumer = createConsumer();
        consumer.subscribe(Collections.singletonList(TOPIC_NAME));

        Schema schema = new Schema.Parser().parse(SCHEMA);

        ScheduledExecutorService producerExecutor = Executors.newSingleThreadScheduledExecutor();
        Thread consumerThread = new Thread(() -> {
            while (done.getCount() > 0) {
                ConsumerRecords<Object, GenericRecord> records = consumer.poll(Duration.ofMillis(500));
                CONSUMED.addAndGet(records.count());
            }
        }, "perf-consumer");
        consumerThread.start();

        long delayMs = Math.max(1, 1000 / PRODUCE_RATE_PER_SEC);
        producerExecutor.scheduleAtFixedRate(() -> {
            try {
                long seq = PRODUCED.incrementAndGet();
                GenericRecord record = new GenericData.Record(schema);
                record.put("seq", seq);
                record.put("payload", "perf-test-payload-" + seq);
                producer.send(new ProducerRecord<>(TOPIC_NAME, "key-" + seq, record));
            } catch (Exception e) {
                PRODUCE_FAILURES.incrementAndGet();
                log.warn("Failed to produce record", e);
            }
        }, 0, delayMs, TimeUnit.MILLISECONDS);

        Thread.sleep(Duration.ofSeconds(DURATION_SECONDS).toMillis());

        done.countDown();
        producerExecutor.shutdown();
        producerExecutor.awaitTermination(10, TimeUnit.SECONDS);
        consumerThread.join(Duration.ofSeconds(10).toMillis());

        producer.flush();
        producer.close(Duration.ofSeconds(10));
        consumer.close(Duration.ofSeconds(10));

        log.info("Done. produced={} consumed={} produceFailures={}", PRODUCED.get(), CONSUMED.get(),
                PRODUCE_FAILURES.get());

        // A meaningful fraction of produce failures, or consumption falling far behind
        // production, indicates the registry (schema lookup/registration path) was a bottleneck
        // or unavailable under load.
        long produced = PRODUCED.get();
        if (produced > 0 && PRODUCE_FAILURES.get() > produced * 0.01) {
            log.error("Produce failure rate exceeded 1%: {} / {}", PRODUCE_FAILURES.get(), produced);
            return false;
        }
        return true;
    }

    private static Producer<Object, Object> createProducer() {
        Properties props = new Properties();
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "perf-test-producer");
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        props.putIfAbsent(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
        applyOAuthIfConfigured(props);
        return new KafkaProducer<>(props);
    }

    private static KafkaConsumer<Object, GenericRecord> createConsumer() {
        Properties props = new Properties();
        props.putIfAbsent(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, "perf-test-consumer-group");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                AvroKafkaDeserializer.class.getName());
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        applyOAuthIfConfigured(props);
        return new KafkaConsumer<>(props);
    }

    /**
     * Configures OAuth2 client-credentials so the Avro serde authenticates its own calls to the
     * (Keycloak-secured) registry REST API. This is independent of Kafka broker authentication:
     * in the perf-main topology the Kafka broker itself is unauthenticated (PLAINTEXT) - only the
     * registry is behind Keycloak - so no SASL/broker-auth properties are set here.
     */
    private static void applyOAuthIfConfigured(Properties props) {
        String tokenEndpoint = System.getenv(SerdeConfig.AUTH_TOKEN_ENDPOINT);
        if (tokenEndpoint == null || tokenEndpoint.isBlank()) {
            return;
        }
        String clientId = System.getenv(SerdeConfig.AUTH_CLIENT_ID);
        String clientSecret = System.getenv(SerdeConfig.AUTH_CLIENT_SECRET);
        props.putIfAbsent(SerdeConfig.AUTH_CLIENT_SECRET, clientSecret);
        props.putIfAbsent(SerdeConfig.AUTH_CLIENT_ID, clientId);
        props.putIfAbsent(SerdeConfig.AUTH_TOKEN_ENDPOINT, tokenEndpoint);
    }
}
