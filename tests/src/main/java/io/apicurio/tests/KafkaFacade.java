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
package io.apicurio.tests;

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
public class KafkaFacade {
    static final Logger LOGGER = LoggerFactory.getLogger(KafkaFacade.class);

    protected static KafkaContainer kafkaContainer;
    protected static AdminClient client;

    public void createTopic(String topic, int partitions, int replicationFactor) {
        adminClient().createTopics(Arrays.asList(new NewTopic(topic, partitions, (short) replicationFactor)));
    }

    public static String bootstrapServers() {
        if (kafkaContainer != null) {
            return kafkaContainer.getBootstrapServers();
        }
        return null;
    }

    public void start() {
        LOGGER.info("Starting kafka container");
        kafkaContainer = new KafkaContainer();
        kafkaContainer.addEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
        kafkaContainer.addEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
        kafkaContainer.start();
    }

    public void stop() {
        LOGGER.info("Stopping kafka container");
        if (client != null) {
            client.close();
        }
        if (kafkaContainer != null) {
            kafkaContainer.stop();
        }
    }

    private AdminClient adminClient() {
        if (client == null) {
            Properties properties = new Properties();
            properties.put("bootstrap.servers", bootstrapServers());
            properties.put("connections.max.idle.ms", 10000);
            properties.put("request.timeout.ms", 5000);
            client = AdminClient.create(properties);
        }
        return client;
    }

}
