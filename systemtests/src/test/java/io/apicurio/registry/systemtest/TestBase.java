package io.apicurio.registry.systemtest;

import io.apicurio.registry.systemtest.framework.TestSeparator;
import io.apicurio.registry.systemtest.framework.LoggerUtils;
import io.apicurio.registry.systemtest.registryinfra.ResourceManager;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.slf4j.Logger;

@DisplayNameGeneration(TestNameGenerator.class)
public abstract class TestBase implements TestSeparator {
    protected final ResourceManager resourceManager = ResourceManager.getInstance();
    protected static final Logger testLogger = LoggerUtils.getLogger();
}
