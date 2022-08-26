package io.apicurio.registry.systemtests;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.framework.DatabaseUtils;
import io.apicurio.registry.systemtests.operator.types.ApicurioRegistryOLMOperatorType;
import io.apicurio.registry.systemtests.registryinfra.ResourceManager;
import io.apicurio.registry.systemtests.registryinfra.resources.ApicurioRegistryResourceType;
import io.apicurio.registry.systemtests.time.TimeoutBudget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;

public abstract class OLMTests extends Tests {
    protected boolean clusterWide;

    public boolean getClusterWide() {
        return clusterWide;
    }

    public void setClusterWide(boolean clusterWide) {
        this.clusterWide = clusterWide;
    }

    @BeforeEach
    public void testBeforeEach(ExtensionContext testContext) throws InterruptedException {
        LOGGER.info("BeforeEach: " + testContext.getDisplayName());

        ApicurioRegistryOLMOperatorType registryOLMOperator = new ApicurioRegistryOLMOperatorType(clusterWide);

        operatorManager.installOperator(registryOLMOperator);
    }

    @Test
    public void testMultipleNamespaces(ExtensionContext testContext) throws InterruptedException {
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
            ResourceManager.getInstance().createResource(true, secondSqlRegistry);
        } else {
            // If OLM operator is installed as namespaced,
            // second Apicurio Registry deployment should fail
            ResourceManager.getInstance().createResource(false, secondSqlRegistry);
            assertFalse(ResourceManager.getInstance().waitResourceCondition(secondSqlRegistry,
                    ResourceManager.getInstance().findResourceType(secondSqlRegistry)::isReady,
                    TimeoutBudget.ofDuration(Duration.ofMinutes(2))));
        }
    }
}
