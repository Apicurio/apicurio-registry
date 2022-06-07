package io.apicurio.registry.systemtests;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.DatabaseUtils;
import io.apicurio.registry.systemtests.framework.KafkaUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import io.apicurio.registry.systemtests.operator.types.KeycloakOLMOperatorType;
import io.apicurio.registry.systemtests.operator.types.StrimziClusterOLMOperatorType;
import io.apicurio.registry.systemtests.registryinfra.resources.ApicurioRegistryResourceType;
import io.strimzi.api.kafka.model.Kafka;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class Tests extends TestBase {
    protected static Logger LOGGER = LoggerUtils.getLogger();

    /* Function to set all necessary variables for test subclasses */

    public abstract void setupTestClass();

    /* Constructor for all test subclasses */

    public Tests() {
        setupTestClass();
    }

    /* Functions for all tests */

    @BeforeAll
    public void testBeforeAll(ExtensionContext testContext) {
        LOGGER.info("BeforeAll: " + testContext.getDisplayName());
    }

    @AfterAll
    public void testAfterAll(ExtensionContext testContext) {
        LOGGER.info("AfterAll: " + testContext.getDisplayName());
    }

    /* Functions for each test */

    @BeforeEach
    public void testBeforeEach(ExtensionContext testContext) {
        LOGGER.info("BeforeEach: " + testContext.getDisplayName());
    }

    @AfterEach
    public void testAfterEach(ExtensionContext testContext) {
        LOGGER.info("AfterEach: " + testContext.getDisplayName());

        resourceManager.deleteResources(testContext);

        operatorManager.uninstallOperators(testContext);
    }

    /* TESTS - PostgreSQL */

    @Test
    public void testRegistrySqlNoKeycloak(ExtensionContext testContext) {
        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

        ApicurioRegistry registry = ApicurioRegistryResourceType.getDefaultSql();
        resourceManager.createResource(testContext, true, registry);

        // TODO: Assert to check that test passed
    }

    @Test
    public void testRegistrySqlKeycloak(ExtensionContext testContext) {
        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

        KeycloakOLMOperatorType keycloakOLMOperator = new KeycloakOLMOperatorType();
        operatorManager.installOperator(testContext, keycloakOLMOperator);

        KeycloakUtils.deployKeycloak(testContext);

        ApicurioRegistry registry = ApicurioRegistryResourceType.getDefaultSql();
        ApicurioRegistryResourceType.updateWithDefaultKeycloak(registry);
        resourceManager.createResource(testContext, true, registry);

        // TODO: Assert to check that test passed

        KeycloakUtils.removeKeycloak();
    }

    /* TESTS - KafkaSQL */

    @Test
    public void testRegistryKafkasqlNoAuthNoKeycloak(ExtensionContext testContext) {
        StrimziClusterOLMOperatorType strimziOperator = new StrimziClusterOLMOperatorType();
        operatorManager.installOperator(testContext, strimziOperator);

        KafkaUtils.deployDefaultKafkaNoAuth(testContext);

        ApicurioRegistryUtils.deployDefaultApicurioRegistryKafkasqlNoAuth(testContext, false);

        // TODO: Assert to check that test passed
    }

    @Test
    public void testRegistryKafkasqlNoAuthKeycloak(ExtensionContext testContext) {
        KeycloakOLMOperatorType keycloakOLMOperator = new KeycloakOLMOperatorType();
        operatorManager.installOperator(testContext, keycloakOLMOperator);

        KeycloakUtils.deployKeycloak(testContext);

        StrimziClusterOLMOperatorType strimziOperator = new StrimziClusterOLMOperatorType();
        operatorManager.installOperator(testContext, strimziOperator);

        KafkaUtils.deployDefaultKafkaNoAuth(testContext);

        ApicurioRegistryUtils.deployDefaultApicurioRegistryKafkasqlNoAuth(testContext, true);

        // TODO: Assert to check that test passed

        KeycloakUtils.removeKeycloak();
    }

    @Test
    public void testRegistryKafkasqlTLSNoKeycloak(ExtensionContext testContext) {
        StrimziClusterOLMOperatorType strimziOperator = new StrimziClusterOLMOperatorType();
        operatorManager.installOperator(testContext, strimziOperator);

        Kafka kafka = KafkaUtils.deployDefaultKafkaTls(testContext);

        ApicurioRegistryUtils.deployDefaultApicurioRegistryKafkasqlTLS(testContext, kafka, false);

        // TODO: Assert to check that test passed
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloak(ExtensionContext testContext) {
        KeycloakOLMOperatorType keycloakOLMOperator = new KeycloakOLMOperatorType();
        operatorManager.installOperator(testContext, keycloakOLMOperator);

        KeycloakUtils.deployKeycloak(testContext);

        StrimziClusterOLMOperatorType strimziOperator = new StrimziClusterOLMOperatorType();
        operatorManager.installOperator(testContext, strimziOperator);

        Kafka kafka = KafkaUtils.deployDefaultKafkaTls(testContext);

        ApicurioRegistryUtils.deployDefaultApicurioRegistryKafkasqlTLS(testContext, kafka, true);

        // TODO: Assert to check that test passed

        KeycloakUtils.removeKeycloak();
    }

    @Test
    public void testRegistryKafkasqlSCRAMNoKeycloak(ExtensionContext testContext) {
        StrimziClusterOLMOperatorType strimziOperator = new StrimziClusterOLMOperatorType();
        operatorManager.installOperator(testContext, strimziOperator);

        Kafka kafka = KafkaUtils.deployDefaultKafkaScram(testContext);

        ApicurioRegistryUtils.deployDefaultApicurioRegistryKafkasqlSCRAM(testContext, kafka, false);

        // TODO: Assert to check that test passed
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloak(ExtensionContext testContext) {
        KeycloakOLMOperatorType keycloakOLMOperator = new KeycloakOLMOperatorType();
        operatorManager.installOperator(testContext, keycloakOLMOperator);

        KeycloakUtils.deployKeycloak(testContext);

        StrimziClusterOLMOperatorType strimziOperator = new StrimziClusterOLMOperatorType();
        operatorManager.installOperator(testContext, strimziOperator);

        Kafka kafka = KafkaUtils.deployDefaultKafkaScram(testContext);

        ApicurioRegistryUtils.deployDefaultApicurioRegistryKafkasqlSCRAM(testContext, kafka, true);

        // TODO: Assert to check that test passed

        KeycloakUtils.removeKeycloak();
    }
}
