/*
 * Copyright 2023 Red Hat
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

package io.apicurio.tests.dbupgrade;

import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.test.utils.KafkaTestContainerManager;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.CustomTestsUtils;
import io.apicurio.tests.utils.RegistryWaitUtils;
import io.apicurio.tests.utils.TestSeparator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer.PREPARE_LOG_COMPACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Carles Arnal
 */
@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.KAFKA_SQL)
@QuarkusTestResource(value = KafkaTestContainerManager.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = KafkaSqlLogCompactionIT.KafkaSqlLogCompactionTestInitializer.class, restrictToAnnotatedClass = true)
@QuarkusIntegrationTest
public class KafkaSqlLogCompactionIT extends ApicurioRegistryBaseIT implements TestSeparator, Constants {

    static final Logger logger = LoggerFactory.getLogger(KafkaSqlLogCompactionIT.class);

    @Override
    public void cleanArtifacts() throws Exception {
        //Don't clean artifacts for this test
    }

    @Test
    public void testLogCompaction() throws Exception {
        //The check must be retried so the kafka storage has been bootstrapped
        retry(() -> assertEquals(3, registryClient.listArtifactsInGroup(PREPARE_LOG_COMPACTION).getCount()));

        var searchResults = registryClient.listArtifactsInGroup(PREPARE_LOG_COMPACTION);
        assertEquals(3, searchResults.getCount());

        String test2content = ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "protobuf/tutorial_v2.proto");
        String originalHash = DigestUtils.sha256Hex(test2content);
        var artifact = CustomTestsUtils.createArtifact(registryClient, PREPARE_LOG_COMPACTION, ArtifactType.PROTOBUF, test2content);

        assertEquals(originalHash, artifact.contentHash);

        String byglobalidHash = DigestUtils.sha256Hex(registryClient.getContentByGlobalId(artifact.meta.getGlobalId()));
        String bycontentidHash = DigestUtils.sha256Hex(registryClient.getContentById(artifact.meta.getContentId()));

        assertEquals(originalHash, byglobalidHash);
        assertEquals(originalHash, bycontentidHash);

        //assert total num of artifacts
        assertEquals(4, registryClient.listArtifactsInGroup(PREPARE_LOG_COMPACTION).getCount());
    }

    public static class KafkaSqlLogCompactionTestInitializer implements QuarkusTestResourceLifecycleManager {
        GenericContainer genericContainer;
        AdminClient adminClient;

        @Override
        public int order() {
            return 10000;
        }

        @Override
        public Map<String, String> start() {
            if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {

                String bootstrapServers = System.getProperty("bootstrap.servers");

                genericContainer = new GenericContainer("quay.io/apicurio/apicurio-registry-kafkasql:2.1.2.Final")
                        .withEnv(Map.of("KAFKA_BOOTSTRAP_SERVERS", bootstrapServers, "QUARKUS_HTTP_PORT", "8081"))
                        .withNetworkMode("host");

                //create the topic with agressive log compaction
                createTopic("kafkasql-journal", 1, bootstrapServers);

                genericContainer.start();
                genericContainer.waitingFor(Wait.forLogMessage(".*(KSQL Kafka Consumer Thread) KafkaSQL storage bootstrapped.*", 1));

                var registryClient = RegistryClientFactory.create("http://localhost:8081");

                try {
                    RegistryWaitUtils.retry(registryClient, registryClient1 -> CustomTestsUtils.createArtifact(registryClient, ArtifactType.AVRO, ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "avro/multi-field_v1.json")));

                    var artifactdata = CustomTestsUtils.createArtifact(registryClient, ArtifactType.JSON, ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "jsonSchema/person_v1.json"));
                    CustomTestsUtils.createArtifact(registryClient, ArtifactType.PROTOBUF, ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "protobuf/tutorial_v1.proto"));

                    assertEquals(3, registryClient.listArtifactsInGroup(PREPARE_LOG_COMPACTION).getCount());

                    //spend some time doing something
                    //this is just to give kafka some time to be 100% the topic is log compacted
                    logger.info("Giving kafka some time to do log compaction");
                    for (int i = 0; i < 15; i++) {
                        registryClient.getArtifactMetaData(artifactdata.meta.getGroupId(), artifactdata.meta.getId());
                        try {
                            Thread.sleep(900);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    logger.info("Finished giving kafka some time");
                } catch (Exception e) {
                    logger.warn("Error filling origin with artifacts information:", e);
                }
            }

            return Collections.emptyMap();
        }

        @Override
        public void stop() {
            if (genericContainer != null && genericContainer.isRunning()) {
                genericContainer.stop();
            }
        }

        public AdminClient adminClient(String bootstrapServers) {
            if (adminClient == null) {
                adminClient = AdminClient.create(connectionProperties(bootstrapServers));
            }
            return adminClient;
        }

        public void createTopic(String topic, int partitions, String bootstrapServers) {
            var journal = new NewTopic(topic, partitions, (short) 1);

            /*journal.configs(Map.of(
                    "min.cleanable.dirty.ratio","0.000001",
                    "cleanup.policy","compact",
                    "segment.ms", "100",
                    "delete.retention.ms", "100"
            ));*/

            journal.configs(Map.of(
                    "cleanup.policy", "compact"
            ));

            adminClient(bootstrapServers).createTopics(List.of(journal));
        }

        public Properties connectionProperties(String bootstrapServers) {
            Properties properties = new Properties();
            properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            properties.put(CommonClientConfigs.CONNECTIONS_MAX_IDLE_MS_CONFIG, 10000);
            properties.put(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, 5000);
            return properties;
        }
    }
}
