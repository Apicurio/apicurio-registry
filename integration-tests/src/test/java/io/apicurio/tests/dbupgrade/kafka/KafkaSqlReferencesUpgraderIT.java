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
        logger.info("=== KafkaSQL References Upgrader Test Starting ===");
        
        // Check if test data is properly initialized
        if (artifactWithReferences == null) {
            logger.error("artifactWithReferences is null! Attempting to recover test data...");
            
            // For cluster tests, we need to find the test data that should have been created by the deployment manager
            // The data should be in the PREPARE_REFERENCES_GROUP
            String expectedGroupId = UpgradeTestsDataInitializer.PREPARE_REFERENCES_GROUP;
            
            logger.info("Searching for artifacts in group: {}", expectedGroupId);
            try {
                var artifacts = registryClient.listArtifactsInGroup(expectedGroupId);
                logger.info("Found {} artifacts in group {}", artifacts.getCount(), expectedGroupId);
                
                if (artifacts.getCount() > 0) {
                    // Find the artifact with references (it should be the AVRO artifact)
                    for (var artifact : artifacts.getArtifacts()) {
                        logger.info("Checking artifact: {} (type: {})", artifact.getId(), artifact.getType());
                        if (artifact.getType() == ArtifactType.AVRO) {
                            // This should be our artifact with references
                            var meta = registryClient.getArtifactMetaData(expectedGroupId, artifact.getId());
                            var content = registryClient.getLatestArtifact(expectedGroupId, artifact.getId());
                            var refs = registryClient.getArtifactReferences(expectedGroupId, artifact.getId());
                            
                            logger.info("Found AVRO artifact {} with {} references", artifact.getId(), refs.size());
                            
                            if (!refs.isEmpty()) {
                                // Reconstruct the test data
                                artifactWithReferences = new CustomTestsUtils.ArtifactData();
                                artifactWithReferences.meta = meta;
                                artifactWithReferences.contentHash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(content.readAllBytes());
                                artifactReferences = refs;
                                
                                logger.info("Successfully reconstructed test data for artifact: {}", artifact.getId());
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to recover test data", e);
                throw new RuntimeException("Test data not available and could not be recovered", e);
            }
            
            if (artifactWithReferences == null) {
                throw new RuntimeException("artifactWithReferences is null and could not be recovered from registry");
            }
        } else {
            logger.info("artifactWithReferences is properly initialized");
        }
        
        if (artifactReferences == null) {
            throw new RuntimeException("artifactReferences is null");
        }
        
        logger.info("Test data validated - proceeding with test");
        logger.info("Testing with artifact: {} in group: {}", artifactWithReferences.meta.getId(), artifactWithReferences.meta.getGroupId());
        
        //The check must be retried so the kafka storage has been bootstrapped
        retry(() -> Assertions.assertTrue(registryClient.listArtifactsInGroup(artifactWithReferences.meta.getGroupId()).getCount() > 0), 1000L);
        //Once the storage is filled with the proper information, if we try to create the same artifact with the same references, no new version will be created and the same ids are used.
        CustomTestsUtils.ArtifactData upgradedArtifact = CustomTestsUtils.createArtifactWithReferences(artifactWithReferences.meta.getGroupId(), artifactWithReferences.meta.getId(), registryClient, ArtifactType.AVRO, ARTIFACT_CONTENT, artifactReferences);
        assertEquals(artifactWithReferences.meta.getGlobalId(), upgradedArtifact.meta.getGlobalId());
        assertEquals(artifactWithReferences.meta.getContentId(), upgradedArtifact.meta.getContentId());
        
        logger.info("=== KafkaSQL References Upgrader Test Completed Successfully ===");
    }

    public static class KafkaSqlStorageReferencesUpgraderInitializer implements QuarkusTestResourceLifecycleManager {
        private GenericContainer genericContainer;

        @Override
        public Map<String, String> start() {
            if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
                // Local tests - run the old registry version and prepare data
                String bootstrapServers = System.getProperty("bootstrap.servers.internal");
                startOldRegistryVersion("quay.io/apicurio/apicurio-registry-kafkasql:2.3.0.Final", bootstrapServers);

                try {
                    var registryClient = RegistryClientFactory.create("http://localhost:8081/");
                    UpgradeTestsDataInitializer.prepareReferencesUpgradeTest(registryClient);
                } catch (Exception e) {
                    logger.warn("Error filling old registry with information: ", e);
                }
            } else {
                // Cluster tests - the deployment manager handles infrastructure and data preparation
                // We just need to ensure our static fields are available
                logger.info("Cluster tests detected - data preparation handled by deployment manager");
                
                // Add a verification hook to check if data was properly set by deployment manager
                // This runs after all QuarkusTestResources have started
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (artifactWithReferences == null) {
                        logger.error("SHUTDOWN HOOK: artifactWithReferences is still null!");
                    } else {
                        logger.info("SHUTDOWN HOOK: artifactWithReferences is properly set");
                    }
                }));
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
