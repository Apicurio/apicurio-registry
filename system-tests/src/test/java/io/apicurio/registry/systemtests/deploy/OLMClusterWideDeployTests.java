package io.apicurio.registry.systemtests.deploy;

import io.apicurio.registry.systemtests.framework.LoggerUtils;

public class OLMClusterWideDeployTests extends OLMDeployTests {
    @Override
    public void setupTestClass() {
        LOGGER = LoggerUtils.getLogger();

        setClusterWide(true);
    }
}
