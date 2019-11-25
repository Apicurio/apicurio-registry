/*
 * Copyright 2019 Red Hat
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
package io.apicurio.tests;

import io.debezium.kafka.KafkaCluster;
import io.debezium.util.Testing;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Facade class for simulate Kafka cluster
 */
public class KafkaFacade {
    static final Logger LOGGER = LoggerFactory.getLogger(KafkaFacade.class);

    protected static final int ZOOKEEPER_PORT = 2181;
    protected static final int KAFKA_PORT = 9092;
    protected static final String DATA_DIR = "cluster";
    private static final long OPERATION_TIMEOUT = 60L;

    private static File dataDir;
    protected static KafkaCluster kafkaCluster;

    private static KafkaCluster kafkaCluster() {

        if (kafkaCluster != null) {
            throw new IllegalStateException();
        }
        dataDir = Testing.Files.createTestingDirectory(DATA_DIR);

        Properties props = new Properties();
        props.put("auto.create.topics.enable", "false");

        kafkaCluster =
            new KafkaCluster()
                .usingDirectory(dataDir)
                .withPorts(ZOOKEEPER_PORT, KAFKA_PORT)
                .withKafkaConfiguration(props);
        return kafkaCluster;
    }

    public void createTopic(String topic, int partitions, int replicationFactor) {
        kafkaCluster.createTopic(topic, partitions, replicationFactor);
    }

    public Properties getConsumerProperties() {
        return kafkaCluster.useTo().getConsumerProperties("groupId", null, OffsetResetStrategy.EARLIEST);
    }

    public void produce(String topic, String body, int messageCount, int partition) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Boolean> produce = new CompletableFuture<>();

        kafkaCluster.useTo().produce("", messageCount, new StringSerializer(), new StringSerializer(),
            () -> produce.complete(true), () -> new ProducerRecord<>(topic, partition, null, body));

        produce.get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
    }

    public void produce(String topic, byte[] bytes, int messageCount, int partition) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Boolean> produce = new CompletableFuture<>();

        kafkaCluster.useTo().produce("", messageCount, new ByteArraySerializer(), new ByteArraySerializer(),
            () -> produce.complete(true), () -> new ProducerRecord<>(topic, partition, null, bytes));

        produce.get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
    }

    public void produce(String topic, int messageCount, int partition) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Boolean> produce = new CompletableFuture<>();
        AtomicInteger index = new AtomicInteger();
        kafkaCluster.useTo().produce("", messageCount, new StringSerializer(), new StringSerializer(),
            () -> produce.complete(true), () -> new ProducerRecord<>(topic, partition, "key-" + index.get(), "value-" + index.getAndIncrement()));
        produce.get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
    }

    public void produceStrings(String topic, String sentBody, int messageCount, int partition) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Boolean> produce = new CompletableFuture<>();
        kafkaCluster.useTo().produceStrings(messageCount, () -> produce.complete(true), () ->
            new ProducerRecord<>(topic, partition, null, sentBody));
        produce.get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
    }

    public void produceStrings(String topic, int messageCount, int partition) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Boolean> produce = new CompletableFuture<>();
        AtomicInteger index = new AtomicInteger();
        kafkaCluster.useTo().produceStrings(messageCount, () -> produce.complete(true),
            () -> new ProducerRecord<>(topic, partition, "key-" + index.get(), "value-" + index.getAndIncrement()));
        produce.get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
    }

    public void start() {
        try {
            kafkaCluster = kafkaCluster().deleteDataPriorToStartup(true).addBrokers(1).startup();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (kafkaCluster != null) {
            LOGGER.info("Shutting down Kafka cluster");
            kafkaCluster.shutdown();
            kafkaCluster = null;
            boolean delete = dataDir.delete();
            // If files are still locked and a test fails: delete on exit to allow subsequent test execution
            if (!delete) {
                dataDir.deleteOnExit();
            }
            LOGGER.info("Kafka cluster and all related data was deleted");
        }
    }
}
