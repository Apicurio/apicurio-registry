package io.apicurio.registry.systemtests;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.client.ApicurioRegistryApiClient;
import io.apicurio.registry.systemtests.client.ArtifactContent;
import io.apicurio.registry.systemtests.client.ArtifactList;
import io.apicurio.registry.systemtests.client.ArtifactType;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.DatabaseUtils;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import io.apicurio.registry.systemtests.operator.types.ApicurioRegistryOLMOperatorType;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.text.MessageFormat;

public class OLMUpgradeTests extends TestBase {
    @Override
    public void setupTestClass() {
        LOGGER = LoggerUtils.getLogger();
    }

    @AfterEach
    public void testAfterEach(ExtensionContext testContext) {
        LOGGER.info("AfterEach: " + testContext.getDisplayName());

        resourceManager.deleteResources();

        operatorManager.uninstallOperators();
    }

    public void runUpgradeTest(ExtensionContext testContext, boolean clusterWide) throws InterruptedException {
        // Install operator from default catalog (do not use catalog source image, it will be used for upgrade)
        ApicurioRegistryOLMOperatorType registryOLMOperator = new ApicurioRegistryOLMOperatorType(
                null,
                clusterWide
        );
        operatorManager.installOperator(registryOLMOperator);

        // Save current (pre-upgrade) ClusterServiceVersion of operator
        DefaultArtifactVersion oldVersion = new DefaultArtifactVersion(registryOLMOperator.getClusterServiceVersion());

        // DEPLOY REGISTRY
        // Deploy PostgreSQL database for registry
        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);
        // Deploy registry with PostgreSQL storage
        ApicurioRegistry apicurioRegistry = ApicurioRegistryUtils.deployDefaultApicurioRegistrySql(testContext, false);

        // Run basic API tests
        APITests.run(apicurioRegistry);

        // CREATE ARTIFACTS TO CHECK REGISTRY OPERABILITY AFTER UPGRADE
        // Get registry hostname
        String hostname = ApicurioRegistryUtils.getApicurioRegistryHostname(apicurioRegistry);
        // Instantiate API client
        ApicurioRegistryApiClient apiClient = new ApicurioRegistryApiClient(hostname);
        // Set number of artifacts to be created
        int artifactsCount = 50;
        // Create artifact one by one
        for (int i = 0; i < artifactsCount; i++) {
            // Create one single artifact in registry
            apiClient.createArtifact("testsuite-upgrade", "upgrade-" + i, ArtifactType.AVRO, ArtifactContent.DEFAULT_AVRO);
        }

        // CHECK CREATION OF ARTIFACTS
        // Get list of artifacts
        ArtifactList artifactList = apiClient.listArtifacts();
        // Check number of present artifacts
        Assertions.assertEquals(artifactList.getCount(), artifactsCount, MessageFormat.format(
                "Registry {0} does not contain {1} artifacts, but {2}.",
                apicurioRegistry.getMetadata().getName(),
                artifactsCount,
                artifactList.getCount()
        ));

        // Run upgrade of operator from catalog source image
        registryOLMOperator.upgrade();

        // Save current (post-upgrade) ClusterServiceVersion of operator
        DefaultArtifactVersion newVersion = new DefaultArtifactVersion(registryOLMOperator.getClusterServiceVersion());

        // Check if operator is updated
        if (oldVersion.compareTo(newVersion) < 0) {
            LOGGER.info("Operator is updated.");
        } else {
            LOGGER.error("Operator is NOT updated.");
        }

        // Wait for registry readiness after upgrade
        ApicurioRegistryUtils.waitApicurioRegistryReady(apicurioRegistry);

        // Run basic API tests
        APITests.run(apicurioRegistry);

        // CHECK PRESENCE OF ARTIFACTS AFTER UPGRADE
        // Get list of artifacts
        artifactList = apiClient.listArtifacts();
        // Check number of present artifacts
        Assertions.assertEquals(artifactList.getCount(), artifactsCount, MessageFormat.format(
                "Registry {0} does not contain {1} artifacts, but {2}.",
                apicurioRegistry.getMetadata().getName(),
                artifactsCount,
                artifactList.getCount()
        ));
    }

    @Test
    @Disabled
    public void testUpgradeClusterWide(ExtensionContext testContext) throws InterruptedException {
        runUpgradeTest(testContext, true);
    }

    @Test
    @Disabled
    public void testUpgradeNamespaced(ExtensionContext testContext) throws InterruptedException {
        runUpgradeTest(testContext, false);
    }
}
