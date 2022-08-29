package io.apicurio.registry.systemtests.deploy;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.framework.DatabaseUtils;
import io.apicurio.registry.systemtests.operator.types.ApicurioRegistryOLMOperatorType;
import io.apicurio.registry.systemtests.registryinfra.ResourceManager;
import io.apicurio.registry.systemtests.registryinfra.resources.ApicurioRegistryResourceType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.AssertionFailedError;

import java.text.MessageFormat;

@Disabled
public abstract class OLMDeployTests extends DeployTests {
    protected boolean clusterWide;

    public boolean getClusterWide() {
        return clusterWide;
    }

    public void setClusterWide(boolean clusterWide) {
        this.clusterWide = clusterWide;
    }

    @BeforeEach
    public void testBeforeEach(ExtensionContext testContext) {
        LOGGER.info("BeforeEach: " + testContext.getDisplayName());

        ApicurioRegistryOLMOperatorType registryOLMOperator = new ApicurioRegistryOLMOperatorType(clusterWide);

        operatorManager.installOperator(testContext, registryOLMOperator);
    }

    @Test
    public void testMultipleNamespaces(ExtensionContext testContext) {
        // Deploy default PostgreSQL database
        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);
        // Deploy default Apicurio Registry with default PostgreSQL database
        ApicurioRegistryUtils.deployDefaultApicurioRegistrySql(testContext, false);

        // Set suffix of second resources
        String suffix = "-multi";
        // Get second PostgreSQL database name
        String secondSqlName = "postgresql" + suffix;
        // Get second PostgreSQL database namespace
        String secondSqlNamespace = "postgresql" + suffix;

        // Deploy second PostgreSQL database
        DatabaseUtils.deployPostgresqlDatabase(testContext, secondSqlName, secondSqlNamespace);
        // Get second Apicurio Registry with second PostgreSQL database
        ApicurioRegistry secondSqlRegistry = ApicurioRegistryResourceType.getDefaultSql(
                Constants.REGISTRY + suffix,
                Constants.TESTSUITE_NAMESPACE + suffix,
                secondSqlName,
                secondSqlNamespace
        );

        // Deploy second Apicurio Registry with second PostgreSQL database
        if (clusterWide) {
            // If OLM operator is installed as cluster wide,
            // second Apicurio Registry should be deployed successfully
            ResourceManager.getInstance().createResource(testContext, true, secondSqlRegistry);
        } else {
            // If OLM operator is installed as namespaced,
            // second Apicurio Registry deployment should fail
            AssertionFailedError assertionFailedError = Assertions.assertThrows(
                    AssertionFailedError.class,
                    () -> ResourceManager.getInstance().createResource(testContext, true, secondSqlRegistry)
            );

            Assertions.assertEquals(
                    MessageFormat.format(
                            "Timed out waiting for resource {0} with name {1} in namespace {2} to be ready. " +
                                    "==> expected: <true> but was: <false>",
                            secondSqlRegistry.getKind(),
                            secondSqlRegistry.getMetadata().getName(),
                            secondSqlRegistry.getMetadata().getNamespace()
                    ),
                    assertionFailedError.getMessage()
            );
        }
    }
}
