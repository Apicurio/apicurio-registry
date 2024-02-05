/*
 * Copyright 2024 Red Hat
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

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.TestSeparator;
import io.quarkus.test.junit.QuarkusIntegrationTest;
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
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.IntStream;

import static io.apicurio.tests.ApicurioRegistryBaseIT.resourceToString;
import static org.junit.jupiter.api.Assertions.assertEquals;


@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.KAFKA_SQL)
@QuarkusIntegrationTest
public class KafkaSqlProtobufContentUpgradeIssueOldIT implements TestSeparator, Constants {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlProtobufContentUpgradeIssueOldIT.class);

    private RedpandaContainer kafka;
    private GenericContainer olderRegistry;
    private RegistryClient client;


    @Test
    public void testOld() throws IOException {
        try {
            log.info("Starting the Kafka Test Container");
            kafka = new RedpandaContainer("docker.redpanda.com/vectorized/redpanda");
            kafka.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("kafka")));
            kafka.start();

            // Create the topic with aggressive log compaction
            createTopic("kafkasql-journal", 1, kafka.getBootstrapServers());

            olderRegistry = new GenericContainer("quay.io/apicurio/apicurio-registry-kafkasql:2.5.8.Final")
                    .withEnv(Map.of("KAFKA_BOOTSTRAP_SERVERS", kafka.getBootstrapServers(), "QUARKUS_HTTP_PORT", "8781"))
                    .withNetworkMode("host");

            olderRegistry.start();

            client = RegistryClientFactory.create("http://localhost:8781");
            waitOnRegistryStart(client);

            // Create a protobuf artifact with reference
            var anyData = resourceToString("artifactTypes/protobuf/any.proto");
            var errorData = resourceToString("artifactTypes/protobuf/error.proto");

            var anyMeta = client.createArtifact("default", "any", null, null, null, null, null, null, null, null, null, ContentHandle.create(anyData).stream(), List.of());
            var errorMeta = client.createArtifact("default", "error", null, null, null, null, null, null, null, null, null, ContentHandle.create(errorData).stream(), List.of(
                    ArtifactReference.builder().name("google/protobuf/any.proto").groupId("default").artifactId("any").version(anyMeta.getVersion()).build()
            ));

            assertEquals(2, client.listArtifactsInGroup("default").getCount());

            // Work with the topic to induce compaction
            Awaitility.await("reproduce protobuf upgrade issue").atMost(Duration.ofSeconds(60)).until(() -> {

                // Flip a global rule several times
                client.createGlobalRule(new Rule("FULL", RuleType.VALIDITY));
                IntStream.range(0, 10).forEach(_ignored -> {
                    client.updateGlobalRuleConfig(RuleType.VALIDITY, new Rule("FULL", RuleType.VALIDITY));
                    client.updateGlobalRuleConfig(RuleType.VALIDITY, new Rule("NONE", RuleType.VALIDITY));
                });
                client.deleteGlobalRule(RuleType.VALIDITY);

                // Restart Registry
                olderRegistry.stop();
                olderRegistry.start();
                waitOnRegistryStart(client);

                // Check that the protobuf artifact disappeared
                try {
                    client.getArtifactMetaData("default", "error");
                    return false;
                } catch (ArtifactNotFoundException ex) {
                    return true;
                }
            });

        } finally {
            if (kafka != null) {
                kafka.stop();
            }
            if (olderRegistry != null) {
                olderRegistry.stop();
            }
            if (client != null) {
                client.close();
            }
        }
    }


    public static void createTopic(String topic, int partitions, String bootstrapServers) {
        var journal = new NewTopic(topic, partitions, (short) 1);

        journal.configs(Map.of(
                "min.cleanable.dirty.ratio", "0.000001",
                "cleanup.policy", "compact",
                "segment.ms", "100",
                "delete.retention.ms", "100"
        ));

        try (var adminClient = AdminClient.create(connectionProperties(bootstrapServers))) {
            adminClient.createTopics(List.of(journal));
        }
    }


    private static Properties connectionProperties(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(CommonClientConfigs.CONNECTIONS_MAX_IDLE_MS_CONFIG, 10000);
        properties.put(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        return properties;
    }


    public static void waitOnRegistryStart(RegistryClient client) {
        // Assuming empty Registry
        // Try to "create" the rule
        Awaitility.await("registry connection available").atMost(Duration.ofSeconds(30)).until(() -> {
            try {
                client.createGlobalRule(new Rule("FULL", RuleType.VALIDITY));
                return true;
            } catch (Exception ex) {
                return false;
            }
        });
        // Now wait until the change has propagated
        Awaitility.await("registry ready").atMost(Duration.ofSeconds(30)).until(() -> {
            try {
                return "FULL".equals(client.getGlobalRuleConfig(RuleType.VALIDITY).getConfig());
            } catch (Exception ex) {
                return false;
            }
        });
        // Reset
        client.deleteGlobalRule(RuleType.VALIDITY);
    }
}
