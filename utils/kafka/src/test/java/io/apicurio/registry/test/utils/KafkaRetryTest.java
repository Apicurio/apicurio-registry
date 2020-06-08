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
import io.apicurio.registry.utils.kafka.ProducerActions;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
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

    protected boolean isKafkaAvailable() {
        Set<String> topics = getKafkaTopics();
        return topics != null && topics.size() > 0;
    }

    protected Set<String> getKafkaTopics() {
        try (Admin admin = Admin.create(kafkaProperties)) {
            return admin.listTopics().names().get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.info("Kafka not available - skipping Kafka related test: {}", e.toString());
            return null;
        }
    }

    interface ConsumedMsgsChecker {
        void check(Map<String, String> producedMsgs, Map<String, String> liveConsumedMsgs) throws Exception;
    }

    @Test
    public void testPoll() throws Exception {
        Assumptions.assumeTrue(isKafkaAvailable());

        int producerThreads = 4;
        ExecutorService exe = Executors.newFixedThreadPool(producerThreads);

        Map<String, String> msgs = IntStream.range(0, producerThreads * 8)
                .boxed()
                .collect(Collectors.toMap(i -> UUID.randomUUID().toString(),
                        i -> "msg#" + i));

        CyclicBarrier cycle = new CyclicBarrier(producerThreads + 1);

        ProducerActions<String, String> producer = new AsyncProducer<>(
                kafkaProperties,
                new StringSerializer(),
                new StringSerializer()
        );

        CompletableFuture<?>[] tasks = IntStream
                .range(0, producerThreads)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        cycle.await(); // all tasks should start work at the same time...
                        msgs.entrySet()
                                .stream()
                                // take only the task's share of messages
                                .filter(e -> Math.abs(e.getKey().hashCode()) % producerThreads == i)
                                .forEach(e -> producer.apply(new ProducerRecord<>(BTOPIC, e.getKey(), e.getValue())));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, exe))
                .toArray(CompletableFuture<?>[]::new);

        cycle.await();

        for (CompletableFuture<?> task : tasks) {
            task.join();
        }

        Map<String, String> liveConsumedMsgs = new ConcurrentHashMap<>();

        ConsumedMsgsChecker checker = (producedMsgs, lcm) -> {
            Map<String, String> missing = new HashMap<>(producedMsgs);
            int tries = 10;
            while (tries > 0) {
                missing.keySet().removeAll(lcm.keySet());
                if (missing.isEmpty()) {
                    break;
                }
                Thread.sleep(250);
                tries--;
            }
            Assertions.assertTrue(missing.isEmpty(), "Failed to consume all messages: " + missing);
        };

        kafkaProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "test2");
        kafkaProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        ConsumerContainer.DynamicPool<String, String> container = new ConsumerContainer.DynamicPool<>(
                kafkaProperties,
                new StringDeserializer(),
                new BrokenDeserializer(),
                BTOPIC,
                1,
                Oneof2.first(record -> liveConsumedMsgs.put(record.key(), record.value()))
        );
        container.start();
        try {
            checker.check(msgs, liveConsumedMsgs);
        } finally {
            container.stop();
        }
    }

    /**
     * This deserializer fails on the first attempt,
     * then after 5 reads, and then it finally works. :-)
     */
    private static class BrokenDeserializer implements Deserializer<String> {
        private int N = 5;
        private int counter;
        private boolean failedOnFirst;
        private boolean failed;

        @Override
        public String deserialize(String topic, byte[] data) {
            if (!failedOnFirst) {
                failedOnFirst = true;
                throw new RuntimeException("Broken");
            }
            if (!failed) {
                counter++;
                if (counter > N) {
                    failed = true;
                    counter = 0;
                    throw new RuntimeException("Broken");
                }
            }
            return new String(data);
        }
    }
}
