package io.apicurio.registry.systemtests.deploy;

import io.apicurio.registry.systemtests.framework.LoggerUtils;
import io.apicurio.registry.systemtests.operator.types.ApicurioRegistryBundleOperatorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;

public class BundleDeployTests extends DeployTests {
    @Override
    public void setupTestClass() {
        LOGGER = LoggerUtils.getLogger();
    }

    @BeforeEach
    public void testBeforeEach(ExtensionContext testContext) {
        LOGGER.info("BeforeEach: " + testContext.getDisplayName());

        ApicurioRegistryBundleOperatorType registryBundleOperator = new ApicurioRegistryBundleOperatorType();

        operatorManager.installOperator(testContext, registryBundleOperator);
    }
}
