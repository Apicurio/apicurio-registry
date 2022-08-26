package io.apicurio.registry.systemtests;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.DatabaseUtils;
import io.apicurio.registry.systemtests.framework.KafkaUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.operator.types.KeycloakOLMOperatorType;
import io.apicurio.registry.systemtests.operator.types.StrimziClusterOLMOperatorType;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtests.registryinfra.resources.PersistenceKind;
import io.strimzi.api.kafka.model.Kafka;
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
    public void testBeforeEach(ExtensionContext testContext) {
        LOGGER.info("BeforeEach: " + testContext.getDisplayName());
    }

    @AfterEach
    public void testAfterEach(ExtensionContext testContext) {
        LOGGER.info("AfterEach: " + testContext.getDisplayName());

        KeycloakUtils.removeKeycloak();

        resourceManager.deleteResources(testContext);

        operatorManager.uninstallOperators(testContext);
    }

    /* Base test method */

    protected void runTest(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind,
            boolean useKeycloak,
            boolean testAPI,
            boolean testAuth
    ) {
        ApicurioRegistry registry = null;

        if (useKeycloak) {
            // Install Keycloak operator
            KeycloakOLMOperatorType keycloakOLMOperator = new KeycloakOLMOperatorType();
            operatorManager.installOperator(testContext, keycloakOLMOperator);

            // Deploy Keycloak
            KeycloakUtils.deployKeycloak(testContext);
        }

        if (persistenceKind.equals(PersistenceKind.KAFKA_SQL)) {
            // Install Strimzi operator
            StrimziClusterOLMOperatorType strimziOperator = new StrimziClusterOLMOperatorType();
            operatorManager.installOperator(testContext, strimziOperator);
        }

        if (persistenceKind.equals(PersistenceKind.SQL)) {
            // Deploy PostgreSQL with/without Keycloak
            DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

            registry = ApicurioRegistryUtils.deployDefaultApicurioRegistrySql(testContext, useKeycloak);
        } else if (persistenceKind.equals(PersistenceKind.KAFKA_SQL)) {
            Kafka kafka;

            // Deploy Kafka
            if (kafkaKind.equals(KafkaKind.NO_AUTH)) {
                // Deploy noAuthKafka
                KafkaUtils.deployDefaultKafkaNoAuth(testContext);

                registry = ApicurioRegistryUtils.deployDefaultApicurioRegistryKafkasqlNoAuth(
                        testContext,
                        useKeycloak
                );
            } else if (kafkaKind.equals(KafkaKind.TLS)) {
                // Deploy tlsKafka
                kafka = KafkaUtils.deployDefaultKafkaTls(testContext);

                registry = ApicurioRegistryUtils.deployDefaultApicurioRegistryKafkasqlTLS(
                        testContext,
                        kafka,
                        useKeycloak
                );
            } else if (kafkaKind.equals(KafkaKind.SCRAM)) {
                // Deploy scramKafka
                kafka = KafkaUtils.deployDefaultKafkaScram(testContext);

                registry = ApicurioRegistryUtils.deployDefaultApicurioRegistryKafkasqlSCRAM(
                        testContext,
                        kafka,
                        useKeycloak
                );
            } else {
                LOGGER.error("Unrecognized KafkaKind: {}.", kafkaKind);
            }
        } else if (persistenceKind.equals(PersistenceKind.MEM)) {
            // TODO: Deploy mem with/without Keycloak
        } else {
            LOGGER.error("Unrecognized PersistenceKind: {}.", persistenceKind);
        }

        if (registry != null && (testAPI || testAuth)) {
            String username = "registry-admin";
            String password = "changeme";

            // TODO: Add more users to check API

            if (testAPI) {
                // Run API tests
                APITests.run(registry, username, password, useKeycloak);
            }

            if (testAuth) {
                // Run auth tests
                LOGGER.info("Running auth tests...");
                // AuthTests.testAnonymousReadAccess(registry, username, password, useKeycloak);
                AuthTests.testBasicAuthentication(registry, username, password);
            }
        }
    }

    /* TESTS - PostgreSQL */

    @Test
    public void testRegistrySqlNoKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.SQL, null, false, true, true);
    }

    @Test
    public void testRegistrySqlKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.SQL, null, true, true, true);
    }

    /* TESTS - KafkaSQL */

    @Test
    public void testRegistryKafkasqlNoAuthNoKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, false, true, true);
    }

    @Test
    public void testRegistryKafkasqlNoAuthKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.NO_AUTH, true, true, true);
    }

    @Test
    public void testRegistryKafkasqlTLSNoKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, false, true, true);
    }

    @Test
    public void testRegistryKafkasqlTLSKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.TLS, true, true, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMNoKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, false, true, true);
    }

    @Test
    public void testRegistryKafkasqlSCRAMKeycloak(ExtensionContext testContext) {
        runTest(testContext, PersistenceKind.KAFKA_SQL, KafkaKind.SCRAM, true, true, true);
    }
}
