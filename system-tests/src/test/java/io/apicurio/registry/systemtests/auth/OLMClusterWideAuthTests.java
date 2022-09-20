package io.apicurio.registry.systemtests.auth;

import io.apicurio.registry.systemtests.framework.LoggerUtils;
import org.junit.jupiter.api.Tag;

@Tag("olm")
@Tag("olm-clusterwide")
public class OLMClusterWideAuthTests extends OLMAuthTests {
    @Override
    public void setupTestClass() {
        LOGGER = LoggerUtils.getLogger();

        setClusterWide(true);
    }
}
