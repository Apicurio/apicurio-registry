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
package io.apicurio.tests.common;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.output.OutputFrame.OutputType;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.common.utils.RegistryUtils;

import java.util.Arrays;
import java.util.Properties;

/**
 * Facade class for simulate Kafka cluster
 */
public class KafkaFacade implements RegistryTestProcess {
    static final Logger LOGGER = LoggerFactory.getLogger(KafkaFacade.class);

    private KafkaContainer kafkaContainer;
    private AdminClient client;

    private static KafkaFacade instance;

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
        if (kafkaContainer != null) {
            return kafkaContainer.getBootstrapServers();
        }
        return null;
    }

    public void startIfNeeded() {
        if (!TestUtils.isExternalRegistry() && isKafkaBasedRegistry() && kafkaContainer != null) {
            LOGGER.info("Skipping deployment of kafka, because it's already deployed as registry storage");
        } else {
            start();
        }
    }

    public void stopIfPossible() throws Exception {
        if (!TestUtils.isExternalRegistry() && isKafkaBasedRegistry()) {
            LOGGER.info("Skipping stopping of kafka, because it's needed for registry storage");
        } else {
            if (kafkaContainer != null) {
                close();
            }
        }
    }

    private boolean isKafkaBasedRegistry() {
        return RegistryUtils.REGISTRY_STORAGE == RegistryStorageType.kafkasql;
    }

    public void start() {
        LOGGER.info("Starting kafka container");
        kafkaContainer = new KafkaContainer();
        kafkaContainer.addEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
        kafkaContainer.addEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
        kafkaContainer.start();
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

    @Override
    public String getName() {
        return "kafka";
    }

    @Override
    public void close() throws Exception {
        LOGGER.info("Stopping kafka container");
        if (client != null) {
            client.close();
            client = null;
        }
        if (kafkaContainer != null) {
            kafkaContainer.stop();
            kafkaContainer = null;
        }
    }

    @Override
    public String getStdOut() {
        return kafkaContainer.getLogs(OutputType.STDOUT);
    }

    @Override
    public String getStdErr() {
        return kafkaContainer.getLogs(OutputType.STDERR);
    }

    @Override
    public boolean isContainer() {
        return true;
    }

}
