package io.apicurio.registry.kafka.utils;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * A single-threaded Kafka Consumer container that is able to {@link #submit(Function)} actions
 * from other threads to be executed asynchronously in the single consumer thread.
 *
 * @param <K> the type of keys in consumed messages
 * @param <V> the type of values in consumed messages
 */
public class ConsumerContainer<K, V> implements ConsumerActions<K, V> {

    private static final Logger log = LoggerFactory.getLogger(ConsumerContainer.class);

    public static final long DEFAULT_CONSUMER_POLL_TIMEOUT = TimeUnit.SECONDS.toMillis(1);
    private static final long MIN_RETRY_DELAY = 100L;
    private static final long MAX_RETRY_DELAY = 10000L;

    private static final AtomicInteger containerCount = new AtomicInteger();
    private final Object lock = new Object();

    private final Properties consumerProperties;
    private final Deserializer<K> keyDeserializer;
    private final Deserializer<V> valueDeserializer;
    // how much to wait for new records in one poll
    private final Duration consumerPollTimeout;
    // only one of following two is set
    private final java.util.function.Consumer<? super ConsumerRecord<K, V>> recordConsumer;
    private final java.util.function.Consumer<? super ConsumerRecords<K, V>> recordsConsumer;
    // if there are no records consumed for a particular TopicPartition in idlePingTimeout
    // millis, the idlePingConsumer is notified about that...
    private final long idlePingTimeout;
    private final java.util.function.Consumer<? super TopicPartition> idlePingConsumer;

    private final Thread thread;
    private final BlockingQueue<CompletableFuture<Consumer<K, V>>> tasks = new LinkedTransferQueue<>();

    public ConsumerContainer(
        Properties consumerProperties,
        Deserializer<K> keyDeserializer,
        Deserializer<V> valueDeserializer,
        Oneof2<
            java.util.function.Consumer<? super ConsumerRecord<K, V>>,
            java.util.function.Consumer<? super ConsumerRecords<K, V>>
            > recordOrRecordsConsumer
    ) {
        this(
            consumerProperties,
            keyDeserializer,
            valueDeserializer,
            DEFAULT_CONSUMER_POLL_TIMEOUT,
            recordOrRecordsConsumer,
            0L, null
        );
    }

    public ConsumerContainer(
        Properties consumerProperties,
        Deserializer<K> keyDeserializer,
        Deserializer<V> valueDeserializer,
        long consumerPollTimeout,
        Oneof2<
            java.util.function.Consumer<? super ConsumerRecord<K, V>>,
            java.util.function.Consumer<? super ConsumerRecords<K, V>>
            > recordOrRecordsConsumer,
        long idlePingTimeout,
        java.util.function.Consumer<? super TopicPartition> idlePingConsumer
    ) {
        this.consumerProperties = Objects.requireNonNull(consumerProperties);
        this.keyDeserializer = Objects.requireNonNull(keyDeserializer);
        this.valueDeserializer = Objects.requireNonNull(valueDeserializer);
        this.consumerPollTimeout = Duration.ofMillis(consumerPollTimeout);
        this.recordConsumer = recordOrRecordsConsumer.isFirst() ? recordOrRecordsConsumer.getFirst() : null;
        this.recordsConsumer = recordOrRecordsConsumer.isSecond() ? recordOrRecordsConsumer.getSecond() : null;
        this.idlePingTimeout = idlePingTimeout;
        this.idlePingConsumer = /* optional */ idlePingConsumer;
        this.thread = new Thread(this::consumerLoop,
                                 "kafka-consumer-container-" + containerCount.incrementAndGet());
    }

    @Override
    public final <R> CompletableFuture<R> submit(Function<? super Consumer<K, V>, ? extends R> consumerAction) {
        Objects.requireNonNull(consumerAction);
        if (Thread.currentThread() == thread) {
            throw new IllegalStateException("Don't submit actions from consumer thread");
        } else {
            synchronized (lock) {
                CompletableFuture<Consumer<K, V>> consumerTask = new CompletableFuture<>();
                CompletableFuture<R> actionTask = consumerTask.thenApply(consumerAction);
                if (stopping) {
                    consumerTask.completeExceptionally(new IllegalStateException("Already stopping or stopped"));
                } else {
                    tasks.add(consumerTask);
                }
                return actionTask;
            }
        }
    }

    private void consumerLoop() {
        boolean waitingForSubscriptionOrAssignment = false;
        Map<TopicPartition, Long> activeTopics = idlePingConsumer == null ? null : new HashMap<>();
        try (KafkaConsumer<K, V> consumer = new KafkaConsumer<>(consumerProperties, keyDeserializer, valueDeserializer)) {
            while (!stopping) {
                CompletableFuture<Consumer<K, V>> task = waitingForSubscriptionOrAssignment
                                                         ? tasks.take()
                                                         : tasks.poll();
                if (task != null) {
                    task.complete(consumer);
                    if (waitingForSubscriptionOrAssignment) {
                        waitingForSubscriptionOrAssignment = consumer.subscription().isEmpty() &&
                                                             consumer.assignment().isEmpty();
                    }
                } else {
                    assert !waitingForSubscriptionOrAssignment;
                    ConsumerRecords<K, V> records = null;
                    try {
                        records = consumer.poll(consumerPollTimeout);
                    } catch (IllegalStateException e) { // thrown when there's no subscription or assignment
                        log.info("{} - will wait", e.getMessage());
                        waitingForSubscriptionOrAssignment = true;
                    }

                    Long time = System.currentTimeMillis();

                    if (records != null) {
                        if (activeTopics != null) {
                            for (TopicPartition partition : records.partitions()) {
                                activeTopics.put(partition, time);
                            }
                        }
                        consume(records, consumer);
                    }

                    if (activeTopics != null) {
                        Collection<TopicPartition> idlePartitions = null;
                        for (Map.Entry<TopicPartition, Long> e : activeTopics.entrySet()) {
                            if (time - e.getValue() >= idlePingTimeout) {
                                if (idlePartitions == null)
                                    idlePartitions = new ArrayList<>(4);
                                idlePartitions.add(e.getKey());
                                e.setValue(time);
                            }
                        }
                        if (idlePartitions != null) {
                            idlePartitions.forEach(idlePingConsumer);
                            activeTopics.keySet().retainAll(consumer.assignment());
                        }
                    }
                }
            }
        } catch (Throwable e) {
            log.warn("Exception caught in consumer polling thread", e);
        } finally {
            log.info("Consumer thread exiting");
            running = false;
        }
    }

    private void consume(ConsumerRecords<K, V> records, Consumer<K, V> consumer) {
        if (recordsConsumer != null) {
            consumeRetryable(records, recordsConsumer, consumer);
        } else {
            assert recordConsumer != null;
            for (ConsumerRecord<K, V> record : records) {
                consumeRetryable(record, recordConsumer, consumer);
            }
        }
    }

    /**
     * Consume record by passing it to {@link #recordConsumer} and retrying that until successful (possibly ad infinitum)
     *
     * @param record the record to consume
     */
    private <T> void consumeRetryable(T record, java.util.function.Consumer<? super T> recordConsumer, Consumer<K, V> consumer) {
        long delay = MIN_RETRY_DELAY;
        boolean interrupted = false;
        // retry loop
        while (!stopping) try {
            recordConsumer.accept(record);
            // everything alright
            break;
        } catch (Exception e) {
            log.warn("Exception caught while processing {} - retrying in {} ms", formatRecord(record), delay, e);
            CompletableFuture<Consumer<K, V>> task;
            try {
                while (!stopping && (task = tasks.poll(delay, TimeUnit.MILLISECONDS)) != null) {
                    task.complete(consumer);
                }
            } catch (InterruptedException ie) {
                log.info("Interrupted - keeping retrying");
                interrupted = true;
            }
            delay = Math.min(delay * 2, MAX_RETRY_DELAY);
        } catch (Throwable e) {
            // in case we get Error (OutOfMemoryError or similar) there's no point in continue-ing with retries, but we can't
            // ignore the error either. So what we're left with is bailing-out and hoping that Kubernetes gives us a fresh start...
            try {
                // enclose this in try/finally in case there is another OutOfMemoryError thrown as a consequence of logging the message...
                log.error("Error caught while processing {} - exiting JVM", formatRecord(record), e);
            } finally {
                System.exit(-1);
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String formatRecord(Object record) {
        if (record instanceof ConsumerRecord<?, ?>) {
            ConsumerRecord<?, ?> cr = (ConsumerRecord<?, ?>) record;
            return String.format("message from topic-partition %s-%s, offset %d, timestamp %tc",
                                 cr.topic(), cr.partition(), cr.offset(), cr.timestamp());
        } else if (record instanceof ConsumerRecords<?, ?>) {
            ConsumerRecords<?, ?> crs = (ConsumerRecords<?, ?>) record;
            return String.format("%d messages from topic-partitions %s",
                                 crs.count(), crs.partitions());
        } else {
            return String.valueOf(record);
        }
    }

    // Lifecycle

    private volatile boolean running, stopping;

    public void start() {
        synchronized (lock) {
            if (stopping)
                throw new IllegalArgumentException("Already stopping or stopped");
            if (running) return;
            running = true;
            thread.start();
        }
    }

    public void stop() {
        synchronized (lock) {
            doStop().join();
        }
    }

    public boolean isRunning() {
        return running;
    }

    private CompletableFuture<Void> doStop() {
        if (!running)
            throw new IllegalStateException("Not started yet or already stopped");

        return submit(consumer -> {
            if (!stopping) {
                stopping = true;
            }
            return null;
        });
    }

    /**
     * A dynamic pool of {@link ConsumerContainer}s (typically configured with the same group.id)
     * sized dynamically by {@link #setConsumerThreads(int)}
     *
     * @param <K>
     * @param <V>
     */
    public static class DynamicPool<K, V> {
        private final Properties consumerProperties;
        private final Deserializer<K> keyDeserializer;
        private final Deserializer<V> valueDeserializer;
        private final String topic;
        private final Oneof2<
            java.util.function.Consumer<? super ConsumerRecord<K, V>>,
            java.util.function.Consumer<? super ConsumerRecords<K, V>>
            > recordOrRecordsConsumer;
        private final LinkedList<ConsumerContainer<K, V>> containers = new LinkedList<>();

        public DynamicPool(
            Properties consumerProperties,
            Deserializer<K> keyDeserializer,
            Deserializer<V> valueDeserializer,
            String topic,
            int initialConsumerThreads,
            Oneof2<
                java.util.function.Consumer<? super ConsumerRecord<K, V>>,
                java.util.function.Consumer<? super ConsumerRecords<K, V>>
                > recordOrRecordsConsumer
        ) {
            this.consumerProperties = Objects.requireNonNull(consumerProperties);
            this.keyDeserializer = Objects.requireNonNull(keyDeserializer);
            this.valueDeserializer = Objects.requireNonNull(valueDeserializer);
            this.topic = Objects.requireNonNull(topic);
            this.recordOrRecordsConsumer = Objects.requireNonNull(recordOrRecordsConsumer);
            setConsumerThreads(initialConsumerThreads);
        }


        public synchronized int getConsumerThreads() {
            return containers.size();
        }

        /**
         * Resize the consumer pool as requested.
         *
         * @param consumerThreads number of consumer threads to set this pool to dynamically
         */
        public synchronized void setConsumerThreads(int consumerThreads) {
            checkNotStopped();
            if (consumerThreads < 0) {
                throw new IllegalArgumentException("consumerThreads should be non-negative");
            }

            while (containers.size() > consumerThreads) {
                ConsumerContainer<K, V> container = containers.removeLast();
                if (running) {
                    container.stop();
                }
            }

            while (containers.size() < consumerThreads) {
                ConsumerContainer<K, V> container = new ConsumerContainer<>(
                    consumerProperties,
                    keyDeserializer, valueDeserializer,
                    recordOrRecordsConsumer
                );
                container.submit(consumer -> {
                    consumer.subscribe(Collections.singletonList(topic));
                    return null;
                });
                if (running) {
                    container.start();
                }
                containers.addLast(container);
            }
        }

        private volatile boolean running, stopped;

        private void checkNotStopped() {
            if (stopped) {
                throw new IllegalArgumentException("Already stopped");
            }
        }

        public synchronized void start() {
            checkNotStopped();
            if (!running) {
                containers.forEach(ConsumerContainer::start);
                running = true;
            }
        }

        public synchronized void stop() {
            if (stopped) return;
            if (running) {
                containers.forEach(ConsumerContainer::stop);
                running = false;
            }
            stopped = true;
        }

        public boolean isRunning() {
            return running;
        }
    }
}
