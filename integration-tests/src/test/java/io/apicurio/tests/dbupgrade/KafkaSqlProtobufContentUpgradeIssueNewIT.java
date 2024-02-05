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
import io.apicurio.tests.dbupgrade.KafkaSqlStorageUpgraderManagerIT.RegistryRunner;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.TestSeparator;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.shaded.org.awaitility.core.ConditionTimeoutException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static io.apicurio.tests.ApicurioRegistryBaseIT.resourceToString;
import static io.apicurio.tests.dbupgrade.KafkaSqlProtobufContentUpgradeIssueOldIT.createTopic;
import static io.apicurio.tests.dbupgrade.KafkaSqlProtobufContentUpgradeIssueOldIT.waitOnRegistryStart;
import static org.junit.jupiter.api.Assertions.assertEquals;


@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.KAFKA_SQL)
@QuarkusIntegrationTest
public class KafkaSqlProtobufContentUpgradeIssueNewIT implements TestSeparator, Constants {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlProtobufContentUpgradeIssueNewIT.class);

    private RedpandaContainer kafka;
    private RegistryRunner node;
    private RegistryClient client;


    @Test
    public void testNew() throws IOException, InterruptedException {
        if (!RegistryRunner.isSupported()) {
            log.warn("TESTS IN 'KafkaSqlProtobufContentUpgradeIssueNewIT' COULD NOT RUN");
            return;
        }
        try {
            log.info("Starting the Kafka Test Container");
            kafka = new RedpandaContainer("docker.redpanda.com/vectorized/redpanda");
            kafka.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("kafka")));
            kafka.start();

            // Create the topic with aggressive log compaction
            createTopic("kafkasql-journal", 1, kafka.getBootstrapServers());

            node = new RegistryRunner();
            node.start(1, Instant.now(), kafka.getBootstrapServers(), List.of(/*"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5001"*/), (node, line) -> {
            });

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

            try {
                // Work with the topic to induce compaction
                Awaitility.await("reproduce protobuf upgrade issue").atMost(Duration.ofSeconds(90)).until(() -> {

                    // Flip a global rule several times
                    client.createGlobalRule(new Rule("FULL", RuleType.VALIDITY));
                    IntStream.range(0, 10).forEach(_ignored -> {
                        client.updateGlobalRuleConfig(RuleType.VALIDITY, new Rule("FULL", RuleType.VALIDITY));
                        client.updateGlobalRuleConfig(RuleType.VALIDITY, new Rule("NONE", RuleType.VALIDITY));
                    });
                    client.deleteGlobalRule(RuleType.VALIDITY);

                    // Restart Registry
                    node.stop();
                    Awaitility.await("node is stopped").atMost(Duration.ofSeconds(10)).until(() -> node.isStopped());
                    node.start(1, Instant.now(), kafka.getBootstrapServers(), List.of(/*"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5001"*/), (node, line) -> {
                    });
                    waitOnRegistryStart(client);

                    // Check that the protobuf artifact disappeared
                    try {
                        client.getArtifactMetaData("default", "error");
                        return false;
                    } catch (ArtifactNotFoundException ex) {
                        return true;
                    }
                });
                // No timeout means failure, compaction did cause the artifact to disappear
                Assertions.fail("Protobuf artifact should not disappear because of compaction");
            } catch (ConditionTimeoutException ex) {
                // This means success, compaction did not cause the artifact to disappear
            }

        } finally {
            if (kafka != null) {
                kafka.stop();
            }
            if (node != null) {
                node.stop();
                Thread.sleep(3000); // Give time to stop
            }
            if (client != null) {
                client.close();
            }
        }
    }
}
