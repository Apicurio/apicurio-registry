/*
 * Copyright 2021 Red Hat
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

package io.apicurio.tests.common.kafka;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.kafka.KafkaCluster;
import io.debezium.util.Testing;

/**
 * @author Fabian Martinez
 */
public class EmbeddedKafka {
    static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedKafka.class);

    protected static final int ZOOKEEPER_PORT = 2181;
    protected static final int KAFKA_PORT = 9092;
    protected static final String DATA_DIR = "cluster";

    private static File dataDir;
    private KafkaCluster kafkaCluster;

    public String bootstrapServers() {
        return kafkaCluster.brokerList();
    }

    public void start() {
        try {
            dataDir = Testing.Files.createTestingDirectory(DATA_DIR);

            Properties props = new Properties();
            props.put("auto.create.topics.enable", "false");

            kafkaCluster = new KafkaCluster()
                    .usingDirectory(dataDir)
                    .withPorts(ZOOKEEPER_PORT, KAFKA_PORT)
                    .withKafkaConfiguration(props)
                    .deleteDataPriorToStartup(true)
                    .addBrokers(1)
                    .startup();

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
