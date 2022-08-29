package io.apicurio.registry.systemtests.api;

import io.apicurio.registry.systemtests.framework.LoggerUtils;
import org.junit.jupiter.api.Disabled;

@Disabled
public class OLMClusterWideAPITests extends OLMAPITests {
    @Override
    public void setupTestClass() {
        LOGGER = LoggerUtils.getLogger();

        setClusterWide(true);
    }
}
