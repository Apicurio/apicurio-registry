package io.apicurio.registry.systemtests;

import io.apicurio.registry.systemtests.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtests.registryinfra.resources.PersistenceKind;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class Tests extends TestBase {
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
    public void testBeforeEach(ExtensionContext testContext) throws InterruptedException {
        LOGGER.info("BeforeEach: " + testContext.getDisplayName());
    }

    @AfterEach
    public void testAfterEach(ExtensionContext testContext) {
        LOGGER.info("AfterEach: " + testContext.getDisplayName());

        resourceManager.deleteResources();

        operatorManager.uninstallOperators();
    }

    /* TESTS - PostgreSQL */

    @Test
    public void testRegistrySqlNoKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.SQL, null, false, true);
    }

    @Test
    public void testRegistrySqlKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.SQL, null, true, true);
    }

    /* TESTS - KafkaSQL */

    @Test
    public void testRegistryKafkasqlNoAuthNoKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, false, true);
    }

    @Test
    public void testRegistryKafkasqlNoAuthKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, true, true);
    }

    @Test
    public void testRegistryKafkasqlTLSNoKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, false, true);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, true, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMNoKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, false, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloak(ExtensionContext testContext) throws InterruptedException {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, true, true);
    }
}
