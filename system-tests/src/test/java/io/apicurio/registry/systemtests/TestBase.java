package io.apicurio.registry.systemtests;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.DatabaseUtils;
import io.apicurio.registry.systemtests.framework.Environment;
import io.apicurio.registry.systemtests.framework.KafkaUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import io.apicurio.registry.systemtests.framework.TestNameGenerator;
import io.apicurio.registry.systemtests.operator.OperatorManager;
import io.apicurio.registry.systemtests.registryinfra.ResourceManager;
import io.apicurio.registry.systemtests.resolver.ExtensionContextParameterResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;

import java.time.Duration;

@DisplayNameGeneration(TestNameGenerator.class)
@ExtendWith(ExtensionContextParameterResolver.class)
public abstract class TestBase {
    protected static Logger LOGGER = LoggerUtils.getLogger();
    protected final ResourceManager resourceManager = ResourceManager.getInstance();
    protected final OperatorManager operatorManager = OperatorManager.getInstance();

    /* Function to set all necessary variables for test subclasses */

    public abstract void setupTestClass();

    /* Constructor for all test subclasses */

    public TestBase() {
        setupTestClass();
    }

    @BeforeAll
    protected void beforeAllTests() throws InterruptedException {
        // Install Keycloak operator
        LoggerUtils.logDelimiter("#");
        LOGGER.info("Deploying shared keycloak operator and instance!");
        LoggerUtils.logDelimiter("#");

        KeycloakOLMOperatorType keycloakOLMOperator = new KeycloakOLMOperatorType();
        operatorManager.installOperatorShared(keycloakOLMOperator);
        KeycloakUtils.deployKeycloak();
        Thread.sleep(Duration.ofMinutes(2).toMillis());
        LoggerUtils.logDelimiter("#");
        LOGGER.info("Deploying shared strimzi operator and kafka");
        LoggerUtils.logDelimiter("#");

        StrimziClusterOLMOperatorType strimziOperator = new StrimziClusterOLMOperatorType();
        operatorManager.installOperatorShared(strimziOperator);

        LoggerUtils.logDelimiter("#");
        LOGGER.info("Deployment of shared resources is done!");
        LoggerUtils.logDelimiter("#");
    }

    @AfterAll
    protected void afterAllTests() throws InterruptedException {
        LoggerUtils.logDelimiter("#");
        LOGGER.info("Cleaning shared resources!");
        LoggerUtils.logDelimiter("#");
        resourceManager.deleteKafka();
        KeycloakUtils.removeKeycloak(Environment.NAMESPACE);
        Thread.sleep(Duration.ofMinutes(2).toMillis());
        operatorManager.uninstallSharedOperators();
        resourceManager.deleteSharedResources();
        LoggerUtils.logDelimiter("#");
        LOGGER.info("Cleaning done!");
        LoggerUtils.logDelimiter("#");
    }

    @BeforeEach
    protected void beforeEachTest(TestInfo testInfo) {
        LoggerUtils.logDelimiter("#");
        LOGGER.info("[TEST-START] {}.{}-STARTED", testInfo.getTestClass().get().getName(), testInfo.getDisplayName());
        LoggerUtils.logDelimiter("#");
        LOGGER.info("");
    }

    @AfterEach
    protected void afterEachTest(TestInfo testInfo) {
        LOGGER.info("");
        LoggerUtils.logDelimiter("#");
        LOGGER.info("[TEST-END] {}.{}-FINISHED", testInfo.getTestClass().get().getName(), testInfo.getDisplayName());
        LoggerUtils.logDelimiter("#");
    }
}
