package io.apicurio.registry.systemtests;

import io.apicurio.registry.systemtests.framework.LoggerUtils;
import org.junit.jupiter.api.Tag;

@Tag("olm")
@Tag("olm-namespace")
public class OLMNamespacedTests extends OLMTests {
    @Override
    public void setupTestClass() {
        LOGGER = LoggerUtils.getLogger();

        setClusterWide(false);
    }
}
