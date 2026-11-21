package io.apicurio.tests.serdes.apicurio;

import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.KafkaFacade;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KafkaSerdesTester<K, P, C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaSerdesTester.class);

    private static final int MILLIS_PER_MESSAGE = 700;
    // How long a timed out consume waits for its consumer thread to end after wakeup().
    private static final long WAKEUP_JOIN_MILLIS = 5000L;
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String MAC_OS_BOOTSTRAP_SERVERS = "docker.host.internal:9092";

    private boolean autoClose = true;

    public KafkaSerdesTester() {
        // empty
    }

    /**
     * @param autoClose the autoClose to set
     */
    public void setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
    }

    public Producer<K, P> createProducer(Class<?> keySerializer, Class<?> valueSerializer, String topicName,
            Class<?> artifactIdStrategy) {
        return createProducer(new Properties(), keySerializer, valueSerializer, topicName,
                artifactIdStrategy);
    }

    public Producer<K, P> createProducer(Properties props, Class<?> keySerializerClass,
            Class<?> valueSerializerClass, String topicName, Class<?> artifactIdStrategy) {
        connectionProperties().forEach((k, v) -> {
            props.putIfAbsent(k, v);
        });

        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + topicName);
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializerClass.getName());
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializerClass.getName());
        // Schema Registry location.
        if (valueSerializerClass.getName().contains("confluent")) {
            props.putIfAbsent(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                    ApicurioRegistryBaseIT.getRegistryApiUrl() + "/ccompat/v7");
            props.putIfAbsent(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, "false");
            props.putIfAbsent(KafkaAvroSerializerConfig.VALUE_SUBJECT_NAME_STRATEGY,
                    artifactIdStrategy.getName());
        } else {
            // props.putIfAbsent(SerdeConfig.FIND_LATEST_ARTIFACT, "true");
            props.putIfAbsent(SerdeConfig.REGISTRY_URL, ApicurioRegistryBaseIT.getRegistryV3ApiUrl());
            props.putIfAbsent(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, artifactIdStrategy.getName());
        }

        return new KafkaProducer<>(props);
    }

    public Consumer<K, C> createConsumer(Class<?> keyDeserializer, Class<?> valueDeserializer,
            String topicName) {
        return createConsumer(new Properties(), keyDeserializer, valueDeserializer, topicName);
    }

    public Consumer<K, C> createConsumer(Properties props, Class<?> keyDeserializer,
            Class<?> valueDeserializer, String topicName) {
        connectionProperties().forEach((k, v) -> {
            props.putIfAbsent(k, v);
        });

        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, "Consumer-" + topicName);
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "500");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer.getName());
        props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer.getName());
        // Schema registry location.
        if (valueDeserializer.getName().contains("confluent")) {
            props.putIfAbsent(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                    ApicurioRegistryBaseIT.getRegistryApiUrl() + "/ccompat/v7");
        } else {
            props.putIfAbsent(SerdeConfig.REGISTRY_URL, ApicurioRegistryBaseIT.getRegistryV3ApiUrl());
        }

        return new KafkaConsumer<>(props);
    }

    public void produceMessages(Producer<K, P> producer, String topicName, DataGenerator<P> dataGenerator,
            int messageCount, boolean retry) throws Exception {

        if (retry) {
            TestUtils.retry(() -> produceMessages(producer, topicName, dataGenerator, messageCount));
        } else {
            produceMessages(producer, topicName, dataGenerator, messageCount);
        }
    }

    private void produceMessages(Producer<K, P> producer, String topicName, DataGenerator<P> dataGenerator,
            int messageCount) throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Integer> resultPromise = CompletableFuture.supplyAsync(() -> {

            int producedMessages = 0;

            try {
                while (producedMessages < messageCount) {
                    P data = dataGenerator.generate(producedMessages);
                    LOGGER.info("Sending message {} to topic {}", data, topicName);
                    ProducerRecord<K, P> producedRecord = new ProducerRecord<>(topicName, data);
                    Future<RecordMetadata> fr = producer.send(producedRecord);

                    if (fr.get(MILLIS_PER_MESSAGE * 2, TimeUnit.MILLISECONDS) != null) {
                        producedMessages++;
                    }

                }
                LOGGER.info("Produced {} messages", producedMessages);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                LOGGER.info("Error with producer future, throwing exception");
                throw new RuntimeException(e);
            } finally {
                producer.flush();
                if (autoClose) {
                    producer.close();
                }
            }

            return producedMessages;
        });

        try {
            Integer messagesSent = resultPromise.get((MILLIS_PER_MESSAGE * messageCount) + 2000,
                    TimeUnit.MILLISECONDS);
            assertEquals(messageCount, messagesSent.intValue());
        } catch (Exception e) {
            throw e;
        }
    }

    public void consumeMessages(Consumer<K, C> consumer, String topicName, int messageCount,
            Predicate<C> dataValidator) throws Exception {
        // A named thread of its own, so a timeout can dump this consumer's stack and wake it up.
        FutureTask<Integer> resultPromise = new FutureTask<>(
                () -> pollUntilConsumed(consumer, topicName, messageCount, dataValidator));
        Thread consumerThread = new Thread(resultPromise, "kafka-serdes-consumer-" + topicName);
        consumerThread.setDaemon(true);
        consumerThread.start();

        try {
            Integer messagesConsumed = resultPromise.get((MILLIS_PER_MESSAGE * messageCount) + 2000,
                    TimeUnit.MILLISECONDS);
            assertEquals(messageCount, messagesConsumed.intValue());
        } catch (TimeoutException e) {
            LOGGER.error("Consumer for topic {} did not finish in time. Stack of {} ({}):\n{}", topicName,
                    consumerThread.getName(), consumerThread.getState(),
                    formatStack(consumerThread.getStackTrace()));
            stopConsumer(consumer, consumerThread, e);
            throw e;
        } catch (InterruptedException e) {
            // The exception reports the interrupt, so the flag is not set again here.
            stopConsumer(consumer, consumerThread, e);
            throw e;
        }
    }

    /**
     * Ends a consume that did not finish, so its consumer does not stay open after the test fails. Any
     * failure while doing so is added to {@code failure}, which is the one to report. An interrupt that
     * arrives during the cleanup is kept as the thread's interrupt flag.
     */
    private void stopConsumer(Consumer<K, C> consumer, Thread consumerThread, Exception failure) {
        try {
            // Safe from another thread: unlike poll() and close(), it does not call acquire() in
            // ClassicKafkaConsumer. It ends a poll() that waits on the broker, but does not interrupt a
            // deserializer call.
            consumer.wakeup();
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        // Lets the helper's close() finish, and lets poll() end before a caller's close():
        // close() calls acquire() and throws ConcurrentModificationException while another thread
        // is inside poll(). An interrupt does not cut the wait short, for the same reason.
        boolean interruptedDuringCleanup = false;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAKEUP_JOIN_MILLIS);
        long remainingNanos = TimeUnit.MILLISECONDS.toNanos(WAKEUP_JOIN_MILLIS);
        while (consumerThread.isAlive() && remainingNanos > 0) {
            try {
                TimeUnit.NANOSECONDS.timedJoin(consumerThread, remainingNanos);
            } catch (InterruptedException cleanupInterrupted) {
                failure.addSuppressed(cleanupInterrupted);
                interruptedDuringCleanup = true;
            }
            remainingNanos = deadline - System.nanoTime();
        }
        if (consumerThread.isAlive()) {
            LOGGER.error("Consumer thread {} survived wakeup(). Stack:\n{}", consumerThread.getName(),
                    formatStack(consumerThread.getStackTrace()));
        }
        if (interruptedDuringCleanup) {
            Thread.currentThread().interrupt();
        }
    }

    private int pollUntilConsumed(Consumer<K, C> consumer, String topicName, int messageCount,
            Predicate<C> dataValidator) {
        consumer.subscribe(Collections.singletonList(topicName));

        AtomicInteger consumedMessages = new AtomicInteger();

        try {
            while (consumedMessages.get() < messageCount) {

                final ConsumerRecords<K, C> records = consumer.poll(Duration.ofSeconds(1));
                if (records.count() == 0) {
                    LOGGER.info("None found");
                } else
                    records.forEach(record -> {

                        if (dataValidator != null) {
                            assertTrue(dataValidator.test(record.value()),
                                    "Consumed record validation failed");
                        }

                        consumedMessages.getAndIncrement();
                        LOGGER.info("{} {} {} {}", record.topic(), record.partition(), record.offset(),
                                record.value());
                    });
            }

            LOGGER.info("Consumed {} messages", consumedMessages.get());
        } finally {
            if (autoClose) {
                consumer.close();
            }
        }

        return consumedMessages.get();
    }

    private static String formatStack(StackTraceElement[] stack) {
        StringBuilder out = new StringBuilder();
        for (StackTraceElement frame : stack) {
            out.append("\tat ").append(frame).append('\n');
        }
        return out.toString();
    }

    @FunctionalInterface
    public static interface DataGenerator<T> {

        public T generate(int count);

    }

    @FunctionalInterface
    public static interface Validator {

        public boolean validate() throws Exception;

    }

    private static Properties connectionProperties() {
        String bootsrapServers = KafkaFacade.getInstance().bootstrapServers();
        if (bootsrapServers == null) {
            Properties props = new Properties();
            if (System.getProperty("os.name").contains("Mac OS")) {
                LOGGER.info("Picking mac os specific bootstrap servers");
                props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, MAC_OS_BOOTSTRAP_SERVERS);
            } else {
                LOGGER.info("Picking linux specific bootstrap servers");
                props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
            }
            return props;
        } else {
            return KafkaFacade.getInstance().connectionProperties();
        }
    }

}