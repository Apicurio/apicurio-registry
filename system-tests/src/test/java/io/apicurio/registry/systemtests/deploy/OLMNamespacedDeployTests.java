package io.apicurio.registry.systemtests;

import io.apicurio.registry.systemtests.framework.LoggerUtils;

public class OLMNamespacedDeployTests extends OLMDeployTests {
    @Override
    public void setupTestClass() {
        LOGGER = LoggerUtils.getLogger();

        setClusterWide(false);
    }
}
