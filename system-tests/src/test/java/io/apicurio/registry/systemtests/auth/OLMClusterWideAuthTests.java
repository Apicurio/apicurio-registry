package io.apicurio.registry.systemtests.auth;

import io.apicurio.registry.systemtests.framework.LoggerUtils;

public class OLMClusterWideAuthTests extends OLMAuthTests {
    @Override
    public void setupTestClass() {
        LOGGER = LoggerUtils.getLogger();

        setClusterWide(true);
    }
}
