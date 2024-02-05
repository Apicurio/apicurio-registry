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
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.dbupgrade.KafkaSqlStorageUpgraderManagerIT.RegistryRunner;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.TestSeparator;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static io.apicurio.tests.ApicurioRegistryBaseIT.resourceToString;
import static io.apicurio.tests.dbupgrade.KafkaSqlProtobufContentUpgradeIssueOldIT.waitOnRegistryStart;
import static io.apicurio.tests.dbupgrade.KafkaSqlStorageUpgraderManagerIT.LOCK_TIMEOUT_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.KAFKA_SQL)
@QuarkusIntegrationTest
public class KafkaSqlProtobufContentUpgradeIssueUpgradeIT implements TestSeparator, Constants {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlProtobufContentUpgradeIssueUpgradeIT.class);

    private RedpandaContainer kafka;
    private GenericContainer olderRegistry;
    private RegistryRunner node;
    private RegistryClient client;


    @Test
    public void testUpgrade() throws IOException, InterruptedException, TimeoutException {
        try {
            log.info("Starting the Kafka Test Container");
            kafka = new RedpandaContainer("docker.redpanda.com/vectorized/redpanda");
            kafka.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("kafka")));
            kafka.start();

            olderRegistry = new GenericContainer("quay.io/apicurio/apicurio-registry-kafkasql:2.4.1.Final")
                    .withEnv(Map.of("KAFKA_BOOTSTRAP_SERVERS", kafka.getBootstrapServers(), "QUARKUS_HTTP_PORT", "8781"))
                    .withNetworkMode("host");

            olderRegistry.start();

            client = RegistryClientFactory.create("http://localhost:8781");
            RestAssured.baseURI = "";
            waitOnRegistryStart(client);

            // Create a protobuf artifact with reference
            var anyData = resourceToString("artifactTypes/protobuf/any.proto");
            var errorData = resourceToString("artifactTypes/protobuf/error.proto");

            var anyMeta = client.createArtifact(null, "any", null, null, null, null, null, null, null, null, null, ContentHandle.create(anyData).stream(), List.of());
            var errorMeta = client.createArtifact(null, "error", null, null, null, null, null, null, null, null, null, ContentHandle.create(errorData).stream(), List.of(
                    ArtifactReference.builder().name("google/protobuf/any.proto").groupId(null).artifactId("any").version(anyMeta.getVersion()).build()
            ));

            assertEquals(2, client.listArtifactsInGroup("default").getCount());
            var c1 = ContentHandle.create(client.getContentByHash("558c60d9927042d1517d37d680600efab58c186da05aecce757cbb7c31e0aef8"));
            // NOTE: The canonical param is ignored, we have to use other way to verify correct upgrade
            // ContentHandle.create(client.getContentByHash("59e286281876629c2715b06c8ef294a1d4a713f5e4249d7a3e386bb734f7db90", true));
            assertTrue(c1.getSizeBytes() > 0);

            olderRegistry.stop();
            Awaitility.await("old registry is stopped").atMost(Duration.ofSeconds(10)).until(() -> !olderRegistry.isRunning());

            node = new RegistryRunner();
            node.start(1, Instant.now(), kafka.getBootstrapServers(), List.of("-Dregistry.kafkasql.upgrade-test-mode=true"), (line, node) -> {
                if (line.contains("Canonical content hash before: 59e286281876629c2715b06c8ef294a1d4a713f5e4249d7a3e386bb734f7db90")) {
                    node.getReport().put("correct-canonical-hash-before", true);
                }
                if (line.contains("Canonical content hash after: b5a276ddf3fc1724dbe206cbc6da60adf8e32af5613ef0fe52fb1dde8da6b67a")) {
                    node.getReport().put("correct-canonical-hash-after", true);
                }
                if (line.contains("Successfully updated 1 content hashes.")) {
                    node.getReport().put("updated-content-hash", true);
                }
                if (line.contains("Successfully updated 1 canonical content hashes.")) {
                    node.getReport().put("updated-canonical-hash", true);
                }
                if (line.contains("KafkaSQL storage bootstrapped in ")) {
                    node.getReport().put("finished", true);
                }
            });

            // Wait on the node to stop
            TestUtils.waitFor("Registry node has stopped", 3 * 1000, 6000 * LOCK_TIMEOUT_SECONDS * 1000, () -> node.getReport().get("finished") == Boolean.TRUE);

            assertEquals(true, node.getReport().get("updated-content-hash"));
            assertEquals(true, node.getReport().get("updated-canonical-hash"));

            assertEquals(2, client.listArtifactsInGroup("default").getCount());
            c1 = ContentHandle.create(client.getContentByHash("4a0536e71d71091be9553ba864fcc663930d8d3cf400470f42b4103d41311f2f"));
            // NOTE: The canonical param is ignored, we have to use other way to verify correct upgrade
            // ContentHandle.create(client.getContentByHash("b5a276ddf3fc1724dbe206cbc6da60adf8e32af5613ef0fe52fb1dde8da6b67a", true));
            assertTrue(c1.getSizeBytes() > 0);

            assertEquals(true, node.getReport().get("correct-canonical-hash-before"));
            assertEquals(true, node.getReport().get("correct-canonical-hash-after"));

        } finally {
            if (kafka != null) {
                kafka.stop();
            }
            if (olderRegistry != null) {
                olderRegistry.stop();
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
