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
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.TestSeparator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static io.apicurio.deployment.manual.ProxyRegistryRunner.createClusterOrDocker;
import static io.apicurio.deployment.manual.ProxyRegistryRunner.createClusterOrJAR;
import static io.apicurio.tests.ApicurioRegistryBaseIT.resourceToString;
import static io.apicurio.tests.kafkasql.manual.KafkaSqlStorageUpgraderManagerIT.LOCK_TIMEOUT_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(Lifecycle.PER_CLASS)
@Tag(Constants.KAFKASQL_MANUAL)
public class KafkaSqlProtobufContentUpgradeIssueUpgradeIT implements TestSeparator, Constants {

    private long testTimeoutMultiplier = 1;

    private ProxyKafkaRunner kafka;
    private ProxyRegistryRunner registry241;
    private ProxyRegistryRunner registry;
    private RegistryClient client;


    @BeforeAll
    protected void beforeAll() {
        if (TestConfiguration.isClusterTests()) {
            testTimeoutMultiplier = 3; // We need more time for Kubernetes
        }
    }


    @Test
    public void testUpgrade() throws IOException, TimeoutException {
        try {
            kafka = new ProxyKafkaRunner();
            kafka.startAndWait();

            registry241 = createClusterOrDocker();
            registry241.start(1, null, "quay.io/apicurio/apicurio-registry-kafkasql:2.4.1.Final", kafka.getBootstrapServers(), null, null);
            registry241.waitUntilReady();

            client = RegistryClientFactory.create(registry241.getClientURL());

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

            registry241.stopAndWait();

            registry = createClusterOrJAR();
            registry.start(1, Instant.now(), null, kafka.getBootstrapServers(), List.of("-Dregistry.kafkasql.upgrade-test-mode=true"), (line, node) -> {
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
            TestUtils.waitFor("Registry node has bootstrapped", 3 * 1000, 6000 * LOCK_TIMEOUT_SECONDS * 1000 * testTimeoutMultiplier,
                    () -> registry.getReport().get("finished") == Boolean.TRUE);

            assertEquals(true, registry.getReport().get("updated-content-hash"));
            assertEquals(true, registry.getReport().get("updated-canonical-hash"));

            client = RegistryClientFactory.create(registry.getClientURL());

            assertEquals(2, client.listArtifactsInGroup("default").getCount());
            c1 = ContentHandle.create(client.getContentByHash("4a0536e71d71091be9553ba864fcc663930d8d3cf400470f42b4103d41311f2f"));
            // NOTE: The canonical param is ignored, we have to use other way to verify correct upgrade
            // ContentHandle.create(client.getContentByHash("b5a276ddf3fc1724dbe206cbc6da60adf8e32af5613ef0fe52fb1dde8da6b67a", true));
            assertTrue(c1.getSizeBytes() > 0);

            assertEquals(true, registry.getReport().get("correct-canonical-hash-before"));
            assertEquals(true, registry.getReport().get("correct-canonical-hash-after"));

        } finally {
            if (client != null) {
                client.close();
            }
            if (registry != null) {
                registry.stopAndWait();
            }
            if (registry241 != null) {
                registry241.stopAndWait();
            }
            if (kafka != null) {
                kafka.stopAndWait();
            }
        }
    }
}
