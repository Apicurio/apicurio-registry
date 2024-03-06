package io.apicurio.tests.dbupgrade.kafka;

import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.test.utils.KafkaTestContainerManager;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer;
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
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer.ARTIFACT_CONTENT;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.KAFKA_SQL)
@QuarkusTestResource(value = KafkaTestContainerManager.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = KafkaSqlReferencesUpgraderIT.KafkaSqlStorageReferencesUpgraderInitializer.class, restrictToAnnotatedClass = true)
@QuarkusIntegrationTest
public class KafkaSqlReferencesUpgraderIT extends ApicurioRegistryBaseIT implements TestSeparator, Constants {

    static final Logger logger = LoggerFactory.getLogger(KafkaSqlLogCompactionIT.class);
    public static CustomTestsUtils.ArtifactData artifactWithReferences;
    public static List<ArtifactReference> artifactReferences;

    @Override
    public void cleanArtifacts() throws Exception {
        //Don't clean artifacts for this test
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

    public static class KafkaSqlStorageReferencesUpgraderInitializer implements QuarkusTestResourceLifecycleManager {
        private GenericContainer genericContainer;

        @Override
        public Map<String, String> start() {
            if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {

                String bootstrapServers = System.getProperty("bootstrap.servers.internal");
                startOldRegistryVersion("quay.io/apicurio/apicurio-registry-kafkasql:2.3.0.Final", bootstrapServers);

                try {
                    var registryClient = RegistryClientFactory.create("http://localhost:8081/");
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
                    .withExposedPorts(8081)
                    .withNetwork(Network.SHARED);

            genericContainer.setPortBindings(List.of("8081:8081"));

            genericContainer.waitingFor(Wait.forHttp("/apis/registry/v2/search/artifacts").forStatusCode(200));
            genericContainer.start();
        }
    }
}
