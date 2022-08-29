package io.apicurio.registry.systemtests;

import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtests.registryinfra.resources.PersistenceKind;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;

@Disabled
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class DeployTests extends TestBase {
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

    /* Base test method */

    protected void runTest(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind,
            boolean useKeycloak
    ) {
        deployTestResources(testContext, persistenceKind, kafkaKind, useKeycloak);

        if (useKeycloak) {
            KeycloakUtils.removeKeycloak();
        }
    }

    /* TESTS - PostgreSQL */

    @Test
    public void testRegistrySqlNoKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.SQL, null, false);
    }

    @Test
    public void testRegistrySqlKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.SQL, null, true);
    }

    /* TESTS - KafkaSQL */

    @Test
    public void testRegistryKafkasqlNoAuthNoKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, false);
    }

    @Test
    public void testRegistryKafkasqlNoAuthKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, true);
    }

    @Test
    public void testRegistryKafkasqlTLSNoKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, false);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMNoKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, false);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, true);
    }
}
