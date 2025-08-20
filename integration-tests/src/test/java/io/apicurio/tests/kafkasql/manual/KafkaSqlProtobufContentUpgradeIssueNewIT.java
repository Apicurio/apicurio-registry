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

package io.apicurio.tests.kafkasql.manual;

import io.apicurio.deployment.RegistryDeploymentManager;
import io.apicurio.deployment.TestConfiguration;
import io.apicurio.deployment.manual.ProxyKafkaRunner;
import io.apicurio.deployment.manual.ProxyRegistryRunner;
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.shaded.org.awaitility.core.ConditionTimeoutException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static io.apicurio.deployment.manual.ProxyKafkaRunner.createCompactingTopic;
import static io.apicurio.deployment.manual.ProxyRegistryRunner.createClusterOrJAR;
import static io.apicurio.tests.ApicurioRegistryBaseIT.resourceToString;
import static org.junit.jupiter.api.Assertions.assertEquals;


@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(Lifecycle.PER_CLASS)
@Tag(Constants.KAFKASQL_MANUAL)
public class KafkaSqlProtobufContentUpgradeIssueNewIT implements TestSeparator, Constants {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaSqlProtobufContentUpgradeIssueNewIT.class);

    private long testTimeoutMultiplier = 1;

    private ProxyKafkaRunner kafka;
    private ProxyRegistryRunner registry;
    private RegistryClient client;


    @BeforeAll
    protected void beforeAll() {
        if (TestConfiguration.isClusterTests()) {
            testTimeoutMultiplier = 3; // We need more time for Kubernetes
            
            // Add comprehensive health checks for cluster tests
            performPreTestHealthChecks();
        }
    }

    /**
     * Performs comprehensive health checks before test execution
     */
    private void performPreTestHealthChecks() {
        LOGGER.info("=== Starting Pre-Test Health Checks ===");
        
        try {
            // 1. Verify Kubernetes infrastructure if in cluster mode
            if (TestConfiguration.isClusterTests()) {
                LOGGER.info("Verifying Kubernetes infrastructure...");
                RegistryDeploymentManager.verifyPodsReady();
                RegistryDeploymentManager.verifyServiceEndpoints();
                RegistryDeploymentManager.verifyNetworkConnectivity();
            }
            
            LOGGER.info("=== All Health Checks Passed ===");
            
        } catch (Exception e) {
            LOGGER.error("=== Health Check Failed ===", e);
            RegistryDeploymentManager.collectDiagnosticInfo();
            throw new RuntimeException("Pre-test health checks failed", e);
        }
    }

    /**
     * Verifies that Kafka is ready and accessible
     */
    private void verifyKafkaReadiness() {
        LOGGER.info("Verifying Kafka readiness...");
        
        if (kafka != null) {
            kafka.verifyKafkaConnectivity();
            kafka.verifyKafkaTopicOperations();
        }
    }

    /**
     * Verifies that Registry is ready and accessible
     */
    private void verifyRegistryReadiness() {
        LOGGER.info("Verifying Registry readiness...");
        
        if (registry != null) {
            registry.verifyRegistryHealth();
            registry.verifyRegistryApiOperations();
            registry.verifyRegistryReadiness();
        }
    }


    @Test
    public void testNew() throws IOException {
        try {
            LOGGER.info("=== Starting KafkaSQL Test Services ===");
            
            // Start Kafka
            LOGGER.info("Starting Kafka...");
            kafka = new ProxyKafkaRunner();
            kafka.startAndWait();
            
            // Verify Kafka is ready
            verifyKafkaReadiness();

            // Create the topic with aggressive log compaction
            LOGGER.info("Creating kafkasql-journal topic...");
            createCompactingTopic(kafka, "kafkasql-journal", 1);

            // Start Registry
            LOGGER.info("Starting Registry...");
            registry = createClusterOrJAR();
            registry.start(kafka.getBootstrapServers());
            registry.waitUntilReady();
            
            // Verify Registry is ready
            verifyRegistryReadiness();

            LOGGER.info("Creating Registry client...");
            client = RegistryClientFactory.create(registry.getClientURL());
            
            LOGGER.info("=== All Services Ready - Starting Test Logic ===");

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
                Awaitility.await("reproduce protobuf upgrade issue").atMost(Duration.ofSeconds(90 * testTimeoutMultiplier)).until(() -> {

                    // Flip a global rule several times
                    client.createGlobalRule(new Rule("FULL", RuleType.VALIDITY));
                    IntStream.range(0, 20).forEach(ignored -> {
                        client.updateGlobalRuleConfig(RuleType.VALIDITY, new Rule("FULL", RuleType.VALIDITY));
                        client.updateGlobalRuleConfig(RuleType.VALIDITY, new Rule("NONE", RuleType.VALIDITY));
                    });
                    client.deleteGlobalRule(RuleType.VALIDITY);

                    // Restart Registry
                    registry.stopAndWait();
                    registry.start(kafka.getBootstrapServers());
                    registry.waitUntilReady();

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
                // Sadly we need to wait to confirm
            }

        } finally {
            if (client != null) {
                client.close();
            }
            if (registry != null) {
                registry.stopAndWait();
            }
            if (kafka != null) {
                kafka.stopAndWait();
            }
        }
    }
}
