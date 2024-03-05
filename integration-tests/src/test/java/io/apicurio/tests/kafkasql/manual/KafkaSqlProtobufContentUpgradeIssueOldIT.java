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
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static io.apicurio.deployment.manual.ProxyKafkaRunner.createCompactingTopic;
import static io.apicurio.deployment.manual.ProxyRegistryRunner.createClusterOrDocker;
import static io.apicurio.tests.ApicurioRegistryBaseIT.resourceToString;
import static org.junit.jupiter.api.Assertions.assertEquals;


@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(Lifecycle.PER_CLASS)
@Tag(Constants.KAFKASQL_MANUAL)
public class KafkaSqlProtobufContentUpgradeIssueOldIT implements TestSeparator, Constants {

    private long testTimeoutMultiplier = 1;

    private ProxyKafkaRunner kafka;
    private ProxyRegistryRunner registry258;
    private RegistryClient client;


    @BeforeAll
    protected void beforeAll() {
        if (TestConfiguration.isClusterTests()) {
            testTimeoutMultiplier = 3; // We need more time for Kubernetes
        }
    }


    @Test
    public void testOld() throws IOException {
        try {
            kafka = new ProxyKafkaRunner();
            kafka.startAndWait();

            // Create the topic with aggressive log compaction
            createCompactingTopic(kafka, "kafkasql-journal", 1);

            registry258 = createClusterOrDocker();
            registry258.start(1, null, "quay.io/apicurio/apicurio-registry-kafkasql:2.5.8.Final", kafka.getBootstrapServers(), null, null);
            registry258.waitUntilReady();
            client = RegistryClientFactory.create(registry258.getClientURL());

            // Create a protobuf artifact with reference
            var anyData = resourceToString("artifactTypes/protobuf/any.proto");
            var errorData = resourceToString("artifactTypes/protobuf/error.proto");

            var anyMeta = client.createArtifact("default", "any", null, null, null, null, null, null, null, null, null, ContentHandle.create(anyData).stream(), List.of());
            var errorMeta = client.createArtifact("default", "error", null, null, null, null, null, null, null, null, null, ContentHandle.create(errorData).stream(), List.of(
                    ArtifactReference.builder().name("google/protobuf/any.proto").groupId("default").artifactId("any").version(anyMeta.getVersion()).build()
            ));

            assertEquals(2, client.listArtifactsInGroup("default").getCount());

            // Work with the topic to induce compaction
            Awaitility.await("reproduce protobuf upgrade issue").atMost(Duration.ofSeconds(60 * testTimeoutMultiplier)).until(() -> {

                // Flip a global rule several times
                client.createGlobalRule(new Rule("FULL", RuleType.VALIDITY));
                IntStream.range(0, 20).forEach(ignored -> {
                    client.updateGlobalRuleConfig(RuleType.VALIDITY, new Rule("FULL", RuleType.VALIDITY));
                    client.updateGlobalRuleConfig(RuleType.VALIDITY, new Rule("NONE", RuleType.VALIDITY));
                });
                client.deleteGlobalRule(RuleType.VALIDITY);

                // Restart Registry
                registry258.stopAndWait();
                registry258.start(1, null, "quay.io/apicurio/apicurio-registry-kafkasql:2.5.8.Final", kafka.getBootstrapServers(), null, null);
                registry258.waitUntilReady();

                // Check that the protobuf artifact disappeared
                try {
                    client.getArtifactMetaData("default", "error");
                    return false;
                } catch (ArtifactNotFoundException ex) {
                    return true;
                }
            });

        } finally {
            if (client != null) {
                client.close();
            }
            if (registry258 != null) {
                registry258.stopAndWait();
            }
            if (kafka != null) {
                kafka.stopAndWait();
            }
        }
    }
}
