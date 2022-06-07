package io.apicurio.registry.systemtests;

import io.apicurio.registry.systemtests.framework.LoggerUtils;

public class OLMClusterWideTests extends OLMTests {
    @Override
    public void setupTestClass() {
        LOGGER = LoggerUtils.getLogger();

        setClusterWide(true);
    }
}
