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
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.test.utils.KafkaTestContainerManager;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.CustomTestsUtils;
import io.apicurio.tests.utils.TestSeparator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer.ARTIFACT_CONTENT;
import static io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer.PREPARE_PROTO_GROUP;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Carles Arnal
 */
@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.KAFKA_SQL)
@QuarkusTestResource(value = KafkaTestContainerManager.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = KafkaSqlStorageUpgradeIT.KafkaSqlStorageUpgradeInitializer.class, restrictToAnnotatedClass = true)
@QuarkusIntegrationTest
public class KafkaSqlStorageUpgradeIT extends ApicurioRegistryBaseIT implements TestSeparator, Constants {

    static final Logger logger = LoggerFactory.getLogger(KafkaSqlLogCompactionIT.class);

    protected static CustomTestsUtils.ArtifactData artifactWithReferences;
    protected static List<ArtifactReference> artifactReferences;
    protected static CustomTestsUtils.ArtifactData protoData;

    @Override
    public void cleanArtifacts() throws Exception {
        //Don't clean artifacts for this test
    }

    @Test
    public void testStorageUpgradeProtobufUpgraderKafkaSql() throws Exception {
        //The check must be retried so the kafka storage has been bootstrapped
        retry(() -> assertEquals(3, registryClient.listArtifactsInGroup(PREPARE_PROTO_GROUP).getCount()), 1000L);

        var searchResults = registryClient.listArtifactsInGroup(PREPARE_PROTO_GROUP);

        var protobufs = searchResults.getArtifacts().stream()
                .filter(ar -> ar.getType().equals(ArtifactType.PROTOBUF))
                .collect(Collectors.toList());

        System.out.println("Protobuf artifacts are " + protobufs.size());
        assertEquals(1, protobufs.size());
        var protoMetadata = registryClient.getArtifactMetaData(protobufs.get(0).getGroupId(), protobufs.get(0).getId());
        var content = registryClient.getContentByGlobalId(protoMetadata.getGlobalId());

        //search with canonicalize
        var versionMetadata = registryClient.getArtifactVersionMetaDataByContent(protobufs.get(0).getGroupId(), protobufs.get(0).getId(), true, null, content);
        assertEquals(protoData.meta.getContentId(), versionMetadata.getContentId());

        String test1content = ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "protobuf/tutorial_v1.proto");

        //search with canonicalize
        versionMetadata = registryClient.getArtifactVersionMetaDataByContent(protobufs.get(0).getGroupId(), protobufs.get(0).getId(), true, null, IoUtil.toStream(test1content));
        assertEquals(protoData.meta.getContentId(), versionMetadata.getContentId());

        //search without canonicalize
        versionMetadata = registryClient.getArtifactVersionMetaDataByContent(protobufs.get(0).getGroupId(), protobufs.get(0).getId(), false, null, IoUtil.toStream(test1content));
        assertEquals(protoData.meta.getContentId(), versionMetadata.getContentId());

        //create one more protobuf artifact and verify
        String test2content = ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "protobuf/tutorial_v2.proto");
        protoData = CustomTestsUtils.createArtifact(registryClient, PREPARE_PROTO_GROUP, ArtifactType.PROTOBUF, test2content);
        versionMetadata = registryClient.getArtifactVersionMetaDataByContent(PREPARE_PROTO_GROUP, protoData.meta.getId(), true, null, IoUtil.toStream(test2content));
        assertEquals(protoData.meta.getContentId(), versionMetadata.getContentId());

        //assert total num of artifacts
        assertEquals(4, registryClient.listArtifactsInGroup(PREPARE_PROTO_GROUP).getCount());
    }

    @Test
    public void testStorageUpgradeReferencesContentHash() throws Exception {
        //The check must be retried so the kafka storage has been bootstrapped
        retry(() -> Assertions.assertTrue(registryClient.listArtifactsInGroup(artifactWithReferences.meta.getGroupId()).getCount() > 0), 1000L);
        //Once the storage is filled with the proper information, if we try to create the same artifact with the same references, no new version will be created and the same ids are used.
        CustomTestsUtils.ArtifactData upgradedArtifact = CustomTestsUtils.createArtifactWithReferences(artifactWithReferences.meta.getGroupId(), artifactWithReferences.meta.getId(), registryClient, ArtifactType.AVRO, ARTIFACT_CONTENT, artifactReferences);
        assertEquals(artifactWithReferences.meta.getGlobalId(), upgradedArtifact.meta.getGlobalId());
        assertEquals(artifactWithReferences.meta.getContentId(), upgradedArtifact.meta.getContentId());
    }

    public static class KafkaSqlStorageUpgradeInitializer implements QuarkusTestResourceLifecycleManager {
        private GenericContainer genericContainer;

        @Override
        public Map<String, String> start() {
            if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {

                String bootstrapServers = System.getProperty("bootstrap.servers");
                startOldRegistryVersion("quay.io/apicurio/apicurio-registry-kafkasql:2.1.2.Final", bootstrapServers);

                try {
                    var registryClient = RegistryClientFactory.create("http://localhost:8081/");

                    UpgradeTestsDataInitializer.prepareProtobufHashUpgradeTest(registryClient);
                    UpgradeTestsDataInitializer.prepareReferencesUpgradeTest(registryClient);
                } catch (Exception e) {
                    logger.warn("Error filling old registry with information: ", e);
                }
            }
            return Collections.emptyMap();
        }

        @Override
        public int order() {
            return 10000;
        }

        @Override
        public void stop() {
            if (genericContainer != null && genericContainer.isRunning()) {
                genericContainer.stop();
            }
        }

        private void startOldRegistryVersion(String registryImage, String bootstrapServers) {
            genericContainer = new GenericContainer<>(registryImage)
                    .withEnv(Map.of("KAFKA_BOOTSTRAP_SERVERS", bootstrapServers, "QUARKUS_HTTP_PORT", "8081"))
                    .withNetworkMode("host");

            genericContainer.start();
            genericContainer.waitingFor(Wait.forLogMessage(".*(KSQL Kafka Consumer Thread) KafkaSQL storage bootstrapped.*", 1));
        }
    }
}