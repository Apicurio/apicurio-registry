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

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.output.OutputFrame.OutputType;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.common.kafka.EmbeddedKafka;
import io.apicurio.tests.common.kafka.TrustAllSslEngineFactory;
import io.apicurio.tests.common.utils.RegistryUtils;
import java.util.Arrays;
import java.util.Properties;

/**
 * Facade class for simulate Kafka cluster
 */
public class KafkaFacade implements RegistryTestProcess {
    static final Logger LOGGER = LoggerFactory.getLogger(KafkaFacade.class);

    private KafkaContainer kafkaContainer;
    private EmbeddedKafka embeddedKafka;
    private boolean sharedKafkaCluster = false;
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
        if (embeddedKafka != null) {
            return embeddedKafka.bootstrapServers();
        }
        if (sharedKafkaCluster) {
            return System.getenv(Constants.TESTS_SHARED_KAFKA_ENV_VAR);
        }
        return null;
    }

    public Properties connectionProperties() {
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        properties.put(CommonClientConfigs.CONNECTIONS_MAX_IDLE_MS_CONFIG, 10000);
        properties.put(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        //shared kafka cluster is deployed in k8s/ocp and exposed externally by using SSL
        if (sharedKafkaCluster) {
            properties.put(SslConfigs.SSL_ENGINE_FACTORY_CLASS_CONFIG, TrustAllSslEngineFactory.class.getName());
            properties.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "");
//            properties.put(SslConfigs.SSL_KEYMANAGER_ALGORITHM_CONFIG, "");
//            properties.put(SslConfigs.SSL_TRUSTMANAGER_ALGORITHM_CONFIG, "");
            properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name);
            properties.put("enable.ssl.certificate.verification", false);
        }
        return properties;
    }

    public void startIfNeeded() {
        if (!TestUtils.isExternalRegistry() && isKafkaBasedRegistry() && isRunning()) {
            LOGGER.info("Skipping deployment of kafka, because it's already deployed as registry storage");
        } else {
            start();
        }
    }

    public void stopIfPossible() throws Exception {
        if (!TestUtils.isExternalRegistry() && isKafkaBasedRegistry()) {
            LOGGER.info("Skipping stopping of kafka, because it's needed for registry storage");
        } else {
            if (isRunning()) {
                close();
            }
        }
    }

    private boolean isRunning() {
        return kafkaContainer != null || embeddedKafka != null || sharedKafkaCluster;
    }

    private boolean isKafkaBasedRegistry() {
        return RegistryUtils.REGISTRY_STORAGE == RegistryStorageType.kafkasql;
    }

    public void start() {
        if (isRunning()) {
            throw new IllegalStateException("Kafka cluster is already running");
        }

        String useSharedKafka = System.getenv(Constants.TESTS_SHARED_KAFKA_ENV_VAR);
        String noDocker = System.getenv(Constants.NO_DOCKER_ENV_VAR);

        if (useSharedKafka != null && !useSharedKafka.isEmpty()) {
            LOGGER.info("Using pre-deployed shared kafka cluster");
            sharedKafkaCluster = true;
        } else if (noDocker != null && noDocker.equals("true")) {
            LOGGER.info("Starting kafka embedded");
            embeddedKafka = new EmbeddedKafka();
            embeddedKafka.start();
        } else {
            LOGGER.info("Starting kafka container");
            kafkaContainer = new KafkaContainer();
            kafkaContainer.addEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
            kafkaContainer.addEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
            kafkaContainer.start();
        }
    }

    public KafkaContainer startNewKafka() {
        LOGGER.info("Starting new kafka container");
        KafkaContainer c = new KafkaContainer();
        c.addEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
        c.addEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
        c.start();
        return c;
    }

    public AdminClient adminClient() {
        if (client == null) {
            client = AdminClient.create(connectionProperties());
        }
        return client;
    }

    @Override
    public String getName() {
        return "kafka-" + kafkaContainer == null ? embeddedKafka == null ? "pre-deployed" : "embedded" : "container";
    }

    @Override
    public void close() throws Exception {
        LOGGER.info("Stopping kafka container");
        if (client != null) {
            client.close();
            client = null;
        }
        sharedKafkaCluster = false;
        if (kafkaContainer != null) {
            kafkaContainer.stop();
            kafkaContainer = null;
        }
        if (embeddedKafka != null) {
            embeddedKafka.stop();
            embeddedKafka = null;
        }
    }

    @Override
    public String getStdOut() {
        if (kafkaContainer == null) {
            return "";
        }
        return kafkaContainer.getLogs(OutputType.STDOUT);
    }

    @Override
    public String getStdErr() {
        if (kafkaContainer == null) {
            return "";
        }
        return kafkaContainer.getLogs(OutputType.STDERR);
    }

    @Override
    public boolean isContainer() {
        return true;
    }

}
