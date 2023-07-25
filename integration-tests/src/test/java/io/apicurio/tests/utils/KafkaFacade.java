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
package io.apicurio.tests.utils;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;

import java.util.Arrays;
import java.util.Properties;

/**
 * Facade class for simulate Kafka cluster
 */
public class KafkaFacade implements AutoCloseable {
    static final Logger LOGGER = LoggerFactory.getLogger(KafkaFacade.class);

    private AdminClient client;

    private static KafkaFacade instance;
    private KafkaContainer kafkaContainer;

    public static KafkaFacade getInstance() {
        if (instance == null) {
            instance = new KafkaFacade();
        }
        return instance;
    }

    private KafkaFacade() {
        //hidden constructor, singleton class
    }

    public void createTopic(String topic, int partitions, int replicationFactor) {
        adminClient().createTopics(Arrays.asList(new NewTopic(topic, partitions, (short) replicationFactor)));
    }

    public String bootstrapServers() {
        return kafkaContainer.getBootstrapServers();
    }

    public Properties connectionProperties() {
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        properties.put(CommonClientConfigs.CONNECTIONS_MAX_IDLE_MS_CONFIG, 10000);
        properties.put(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        return properties;
    }

    private boolean isRunning() {
        return kafkaContainer != null && kafkaContainer.isRunning();
    }

    public void startIfNeeded() {
        if (isRunning()) {
            LOGGER.info("Skipping deployment of kafka, because it's already deployed");
        } else {
            start();
        }
    }

    public void start() {
        if (isRunning()) {
            throw new IllegalStateException("Kafka cluster is already running");
        }

        LOGGER.info("Starting kafka container");
        this.kafkaContainer = new KafkaContainer();
        kafkaContainer.addEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
        kafkaContainer.addEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
        kafkaContainer.start();

    }

    public void stopIfPossible() throws Exception {
        if (isRunning()) {
            close();
        }
    }

    public AdminClient adminClient() {
        if (client == null) {
            client = AdminClient.create(connectionProperties());
        }
        return client;
    }

    @Override
    public void close() throws Exception {
        LOGGER.info("Stopping kafka container");
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
