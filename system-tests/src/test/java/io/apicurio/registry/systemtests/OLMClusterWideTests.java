package io.apicurio.registry.systemtests;

import io.apicurio.registry.systemtests.framework.LoggerUtils;
import org.junit.jupiter.api.Disabled;

@Disabled
public class OLMClusterWideTests extends OLMTests {
    @Override
    public void setupTestClass() {
        LOGGER = LoggerUtils.getLogger();

        setClusterWide(true);
    }
}
