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

package io.apicurio.registry.test.utils;

import io.apicurio.registry.utils.kafka.AsyncProducer;
import io.apicurio.registry.utils.kafka.ConsumerContainer;
import io.apicurio.registry.utils.kafka.Oneof2;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Ales Justin
 */
public class KafkaRetryTest {
    private static final Logger log = LoggerFactory.getLogger(KafkaRetryTest.class);
    private static final String BTOPIC = "broken-topic";
    private static final Properties kafkaProperties;

    static {
        kafkaProperties = new Properties();
        kafkaProperties.put(
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                System.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
        );
    }

    /**
     * This methos is two-fold:
     * <ul>
     *     <li>It checks that Kafka broker(s) are reachable and throws {@link TestAbortedException}
     *     if not so a test that invokes this method is skipped</li>
     *     <li>In case Kafka is reachable, it ensures that given topics are present and
     *     creates them if necessary</li>
     * </ul>
     *
     * @param topicNames
     */
    protected final void ensureTopics(String... topicNames) {
        try (Admin admin = Admin.create(kafkaProperties)) {
            // creating admin client succeeds even if Kafka is not reachable
            // but listing the topics reveals the org.apache.kafka.common.errors.TimeoutException
            // wrapped into ExecutionException then...
            Set<String> existingTopicNames;
            try {
                existingTopicNames = admin.listTopics().names().get(5, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw e.getCause();
            }
            try {
                for (String topicName : topicNames) {
                    if (existingTopicNames.contains(topicName)) {
                        log.info("Topic already exists: {}", topicName);
                    } else {
                        log.info("Creating topic: {}", topicName);
                        CreateTopicsResult ctr = admin.createTopics(
                                Collections.singleton(new NewTopic(topicName, 1, (short) 1))
                        );
                        ctr.all().get();
                    }
                }
            } catch (Exception e) {
                throw new AssertionError("Can't create topics", e);
            }
        } catch (KafkaException | TimeoutException e) {
            // abort test (not failing it) when Kafka is not available
            log.info("Kafka not available - aborting test", e);
            throw new TestAbortedException("Kafka not available - aborting test", e);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testIntermittentDeserializerFailures() {
        ensureTopics(BTOPIC);

        // 10 messages with unique keys
        Map<String, String> msgs = IntStream.range(0, 10)
                .boxed()
                .collect(Collectors.toMap(i -> UUID.randomUUID().toString(),
                        i -> "msg#" + i));

        try (AsyncProducer<String, String> producer = new AsyncProducer<>(
                kafkaProperties,
                new StringSerializer(),
                new StringSerializer()
        )) {
            msgs.forEach((k, v) -> producer.apply(new ProducerRecord<>(BTOPIC, k, v)).join());
        }

        ExpectedMessageCollector<String, String> expected = new ExpectedMessageCollector<>(msgs);

        Properties kprops = new Properties();
        kprops.putAll(kafkaProperties);
        kprops.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "test2");
        kprops.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (ConsumerContainer.DynamicPool<String, String> container = new ConsumerContainer.DynamicPool<>(
                kprops,
                new StringDeserializer(),
                new BrokenDeserializer(0, 5),
                BTOPIC,
                1,
                Oneof2.first(expected),
                (consumer, ex) -> {
                    if (ex instanceof SerializationException) {
                        log.info("Got serialization exception - will retry", ex);
                    } else {
                        throw ex;
                    }
                }
        )) {
            Map<String, String> missing = expected.wait(5, TimeUnit.SECONDS);
            if (!missing.isEmpty()) {
                throw new AssertionError("Missing consumed messages: " + missing.values());
            }
        }
    }

    /**
     * This deserializer fails on the specified calls but succeeds on all others
     */
    private static class BrokenDeserializer extends StringDeserializer {
        private final int[] failCallIndices;
        private final AtomicInteger counter = new AtomicInteger();

        BrokenDeserializer(int... failCallIndices) {
            this.failCallIndices = failCallIndices;
        }

        @Override
        public String deserialize(String topic, byte[] data) {
            int c = counter.getAndIncrement();
            for (int i : failCallIndices) {
                if (c == i) {
                    throw new RuntimeException("Failure at call#" + i);
                }
            }
            return super.deserialize(topic, data);
        }
    }

    public abstract static class WaitableMessageCollector<K, V> implements Consumer<ConsumerRecord<K, V>> {

        /**
         * Waits for at most given timeout or until {@link #terminatingCondition()} evaluates
         * to {@code true} and then returns the {@link #resultMessages()}.
         */
        public final synchronized Map<K, V> wait(long timeout, TimeUnit unit) {
            boolean interrupted = false;
            long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
            long waitNanos;
            while (!terminatingCondition() && (waitNanos = deadlineNanos - System.nanoTime()) > 0L) {
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, waitNanos);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return resultMessages();
        }

        protected abstract boolean terminatingCondition();

        protected abstract Map<K, V> resultMessages();
    }

    public static class ExpectedMessageCollector<K, V> extends WaitableMessageCollector<K, V> {
        private final Map<K, V> expected;

        public ExpectedMessageCollector(Map<K, V> expected) {
            this.expected = new LinkedHashMap<>(expected);
        }

        @Override
        public synchronized void accept(ConsumerRecord<K, V> cr) {
            expected.remove(cr.key(), cr.value());
            notifyAll();
        }

        @Override
        protected boolean terminatingCondition() {
            return expected.isEmpty();
        }

        @Override
        protected Map<K, V> resultMessages() {
            return new LinkedHashMap<>(expected);
        }
    }

    public static class UnexpectedMessageCollector<K, V> extends WaitableMessageCollector<K, V> {
        private final Map<K, V> unexpected;
        private final Map<K, V> consumed;

        public UnexpectedMessageCollector(Map<K, V> unexpected) {
            this.unexpected = new LinkedHashMap<>(unexpected);
            this.consumed = new LinkedHashMap<>();
        }

        @Override
        public synchronized void accept(ConsumerRecord<K, V> cr) {
            if (cr.value().equals(unexpected.get(cr.key()))) {
                consumed.put(cr.key(), cr.value());
                notifyAll();
            }
        }

        @Override
        protected boolean terminatingCondition() {
            return !consumed.isEmpty();
        }

        @Override
        protected Map<K, V> resultMessages() {
            return new LinkedHashMap<>(consumed);
        }
    }
}
