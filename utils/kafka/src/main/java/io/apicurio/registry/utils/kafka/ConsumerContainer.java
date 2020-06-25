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

package io.apicurio.registry.utils.kafka;

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
import java.util.function.BiConsumer;
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
    private final java.util.function.Consumer<? super ConsumerRecord<K, V>> recordHandler;
    private final java.util.function.Consumer<? super ConsumerRecords<K, V>> recordsHandler;
    // exceptions from consumer.poll get passed to this handler
    private final BiConsumer<? super Consumer<?, ?>, ? super RuntimeException> consumerExceptionHandler;
    // if there are no records consumed for a particular TopicPartition in idlePingTimeout
    // millis, the idlePingHandler is notified about that...
    private final long idlePingTimeout;
    private final java.util.function.Consumer<? super TopicPartition> idlePingHandler;

    private final Thread thread;
    private final BlockingQueue<CompletableFuture<Consumer<K, V>>> tasks = new LinkedTransferQueue<>();

    public ConsumerContainer(
            Properties consumerProperties,
            Deserializer<K> keyDeserializer,
            Deserializer<V> valueDeserializer,
            Oneof2<
                    java.util.function.Consumer<? super ConsumerRecord<K, V>>,
                    java.util.function.Consumer<? super ConsumerRecords<K, V>>
                    > recordOrRecordsHandler,
            BiConsumer<? super Consumer<?, ?>, ? super RuntimeException> consumerExceptionHandler
    ) {
        this(
                consumerProperties,
                keyDeserializer,
                valueDeserializer,
                DEFAULT_CONSUMER_POLL_TIMEOUT,
                recordOrRecordsHandler,
                consumerExceptionHandler,
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
                    > recordOrRecordsHandler,
            BiConsumer<? super Consumer<?, ?>, ? super RuntimeException> consumerExceptionHandler,
            long idlePingTimeout,
            java.util.function.Consumer<? super TopicPartition> idlePingHandler
    ) {
        this.consumerProperties = Objects.requireNonNull(consumerProperties);
        this.keyDeserializer = Objects.requireNonNull(keyDeserializer);
        this.valueDeserializer = Objects.requireNonNull(valueDeserializer);
        this.consumerPollTimeout = Duration.ofMillis(consumerPollTimeout);
        this.recordHandler = recordOrRecordsHandler.isFirst() ? recordOrRecordsHandler.getFirst() : null;
        this.recordsHandler = recordOrRecordsHandler.isSecond() ? recordOrRecordsHandler.getSecond() : null;
        this.consumerExceptionHandler = Objects.requireNonNull(consumerExceptionHandler);
        this.idlePingTimeout = idlePingTimeout;
        this.idlePingHandler = /* optional */ idlePingHandler;
        this.thread = new Thread(this::consumerLoop,
                "kafka-consumer-container-" + containerCount.incrementAndGet());
        thread.start();
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
                if (closed) {
                    consumerTask.completeExceptionally(new IllegalStateException("Container already closed"));
                } else {
                    tasks.add(consumerTask);
                }
                return actionTask;
            }
        }
    }

    private void consumerLoop() {
        boolean waitingForSubscriptionOrAssignment = false;
        Map<TopicPartition, Long> activeTopics = idlePingHandler == null ? null : new HashMap<>();
        try (KafkaConsumer<K, V> consumer = new KafkaConsumer<>(consumerProperties, keyDeserializer, valueDeserializer)) {
            while (!closed) {
                CompletableFuture<Consumer<K, V>> task;
                boolean interrupted = false;
                try {
                    task = waitingForSubscriptionOrAssignment ? tasks.take() : tasks.poll();
                } catch (InterruptedException e) {
                    log.warn("Consumer thread interrupted", e);
                    task = null;
                    interrupted = true;
                }
                if (task != null) {
                    task.complete(consumer);
                    if (waitingForSubscriptionOrAssignment) {
                        waitingForSubscriptionOrAssignment = consumer.subscription().isEmpty() &&
                                consumer.assignment().isEmpty();
                    }
                } else if (!interrupted) {
                    assert !waitingForSubscriptionOrAssignment;

                    ConsumerRecords<K, V> records = null;
                    try {
                        records = consumer.poll(consumerPollTimeout);
                    } catch (IllegalStateException e) { // thrown when there's no subscription or assignment
                        log.info("{} - will wait", e.getMessage());
                        waitingForSubscriptionOrAssignment = true;
                    } catch (RuntimeException e) {
                        consumerExceptionHandler.accept(consumer, e);
                    }

                    Long time = System.currentTimeMillis(); // make just one object

                    if (records != null) {
                        if (activeTopics != null) {
                            for (TopicPartition partition : records.partitions()) {
                                activeTopics.put(partition, time);
                            }
                        }
                        handleRecords(records, consumer);
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
                            idlePartitions.forEach(idlePingHandler);
                            activeTopics.keySet().retainAll(consumer.assignment());
                        }
                    }
                }
            }
            log.info("Consumer loop finished");
        } catch (Throwable e) {
            log.warn("Exception caught in consumer polling thread", e);
        } finally {
            log.info("Consumer thread terminating");
        }
    }

    private void handleRecords(ConsumerRecords<K, V> records, Consumer<K, V> consumer) {
        if (recordsHandler != null) {
            acceptRetryable(records, recordsHandler, consumer);
        } else {
            assert recordHandler != null;
            for (ConsumerRecord<K, V> record : records) {
                acceptRetryable(record, recordHandler, consumer);
            }
        }
    }

    /**
     * Handle record(s) by passing it/them to {@code handler} and retrying that until successful (possibly ad infinity)
     *
     * @param record the record/records to handle
     */
    private <T> void acceptRetryable(T record, java.util.function.Consumer<? super T> handler, Consumer<K, V> consumer) {
        applyRetryable(record, r -> {
            handler.accept(r);
            return null;
        }, consumer);
    }

    /**
     * Handle record(s) by passing it/them to {@code function} and retrying that until successful (possibly ad infinity)
     *
     * @param record the record/records to handle
     * @return function's result or null if closed while retrying
     */
    private <T, R> R applyRetryable(T record, Function<? super T, ? extends R> handler, Consumer<K, V> consumer) {
        long delay = MIN_RETRY_DELAY;
        boolean interrupted = false;
        // retry loop
        while (!closed) {
            try {
                return handler.apply(record);
            } catch (Exception e) {
                log.warn("Exception caught while processing {} - retrying in {} ms", formatRecord(record), delay, e);
                CompletableFuture<Consumer<K, V>> task;
                try {
                    while (!closed && (task = tasks.poll(delay, TimeUnit.MILLISECONDS)) != null) {
                        task.complete(consumer);
                    }
                } catch (InterruptedException ie) {
                    log.info("Interrupted - keeping retrying");
                    interrupted = true;
                }
                delay = Math.min(delay * 2, MAX_RETRY_DELAY);
            } catch (Throwable e) {
                // in case we get Error (OutOfMemoryError or similar) there's no point in continue-ing with retries, but we can't
                // ignore the error either. So what we're left with is bailing-out and hoping that e.g. Kubernetes gives us a fresh start...
                // For OOME, we can start the Java app with -XX:+ExitOnOutOfMemoryError
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
        return null;
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

    private volatile boolean closed;

    /**
     * AutoCloseable
     */
    @Override
    public void close() {
        synchronized (lock) {
            if (closed) return;
            submit(consumer -> {
                closed = true;
                return null;
            }).join();
        }
    }

    /**
     * A dynamic pool of {@link ConsumerContainer}s (typically configured with the same group.id)
     * sized dynamically by {@link #setConsumerThreads(int)}
     *
     * @param <K>
     * @param <V>
     */
    public static class DynamicPool<K, V> implements AutoCloseable {
        private final Properties consumerProperties;
        private final Deserializer<K> keyDeserializer;
        private final Deserializer<V> valueDeserializer;
        private final String topic;
        private final Oneof2<
                java.util.function.Consumer<? super ConsumerRecord<K, V>>,
                java.util.function.Consumer<? super ConsumerRecords<K, V>>
                > recordOrRecordsHandler;
        private final BiConsumer<? super Consumer<?, ?>, ? super RuntimeException> consumerExceptionHandler;
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
                        > recordOrRecordsHandler,
                BiConsumer<? super Consumer<?, ?>, ? super RuntimeException> consumerExceptionHandler
        ) {
            this.consumerProperties = Objects.requireNonNull(consumerProperties);
            this.keyDeserializer = Objects.requireNonNull(keyDeserializer);
            this.valueDeserializer = Objects.requireNonNull(valueDeserializer);
            this.topic = Objects.requireNonNull(topic);
            this.recordOrRecordsHandler = Objects.requireNonNull(recordOrRecordsHandler);
            this.consumerExceptionHandler = Objects.requireNonNull(consumerExceptionHandler);
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
            checkNotClosed();
            if (consumerThreads < 0) {
                throw new IllegalArgumentException("consumerThreads should be non-negative");
            }

            while (containers.size() > consumerThreads) {
                ConsumerContainer<K, V> container = containers.removeLast();
                container.close();
            }

            while (containers.size() < consumerThreads) {
                ConsumerContainer<K, V> container = new ConsumerContainer<>(
                        consumerProperties,
                        keyDeserializer, valueDeserializer,
                        recordOrRecordsHandler,
                        consumerExceptionHandler
                );
                container.submit(consumer -> {
                    consumer.subscribe(Collections.singletonList(topic));
                    return null;
                });
                containers.addLast(container);
            }
        }

        private volatile boolean closed;

        private void checkNotClosed() {
            if (closed) {
                throw new IllegalStateException("Container already closed");
            }
        }

        /**
         * AutoCloseable
         */
        @Override
        public synchronized void close() {
            if (closed) return;
            try {
                containers.forEach(ConsumerContainer::close);
            } finally {
                closed = true;
            }
        }
    }
}
