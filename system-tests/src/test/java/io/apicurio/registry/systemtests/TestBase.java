package io.apicurio.registry.systemtests;

import io.apicurio.registry.systemtests.framework.LoggerUtils;
import io.apicurio.registry.systemtests.framework.TestNameGenerator;
import io.apicurio.registry.systemtests.operator.OperatorManager;
import io.apicurio.registry.systemtests.registryinfra.ResourceManager;
import io.apicurio.registry.systemtests.resolver.ExtensionContextParameterResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;

@DisplayNameGeneration(TestNameGenerator.class)
@ExtendWith(ExtensionContextParameterResolver.class)
public abstract class TestBase {
    protected final Logger BASE_LOGGER = LoggerUtils.getLogger();
    protected final ResourceManager resourceManager = ResourceManager.getInstance();
    protected final OperatorManager operatorManager = OperatorManager.getInstance();

    @BeforeEach
    protected void beforeEachTest(TestInfo testInfo) {
        LoggerUtils.logDelimiter("#");
        BASE_LOGGER.info("[TEST-START] {}.{}-STARTED", testInfo.getTestClass().get().getName(), testInfo.getDisplayName());
        LoggerUtils.logDelimiter("#");
        BASE_LOGGER.info("");
    }

    @AfterEach
    protected void afterEachTest(TestInfo testInfo) {
        BASE_LOGGER.info("");
        LoggerUtils.logDelimiter("#");
        BASE_LOGGER.info("[TEST-END] {}.{}-FINISHED", testInfo.getTestClass().get().getName(), testInfo.getDisplayName());
        LoggerUtils.logDelimiter("#");
    }
}
