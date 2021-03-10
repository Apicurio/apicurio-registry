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

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.apache.kafka.clients.CommonClientConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

/**
 * @author Fabian Martinez Gonzalez
 * @author Ales Justin
 */
public class KafkaTestContainerManager implements QuarkusTestResourceLifecycleManager {
    private static final Logger log = LoggerFactory.getLogger(KafkaTestContainerManager.class);

    private final boolean skipKafkaContainer = Boolean.getBoolean("skipKafkaContainer");
    private KafkaContainer kafka;

    @SuppressWarnings("deprecation")
    @Override
    public Map<String, String> start() {
        log.info("Starting the Kafka Test Container");
        String bootstrapServers = "localhost:9092";
        if (!skipKafkaContainer) {
            kafka = new KafkaContainer();
            kafka.addEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
            kafka.addEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
            kafka.start();

            bootstrapServers = kafka.getBootstrapServers();

            createTopics(bootstrapServers);
        }
        return Collections.singletonMap(
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
    }

    private void createTopics(String bootstrapServers) {
        log.info("Creating topics using bootstrap servers: {}", bootstrapServers);

        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServers);
        properties.put("connections.max.idle.ms", 10000);
        properties.put("request.timeout.ms", 5000);
    }

    @Override
    public void stop() {
        log.info("Stopping the Kafka Test Container");
        if (!skipKafkaContainer) {
            kafka.stop();
        }
    }
}
