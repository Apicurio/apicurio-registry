package io.apicurio.registry.systemtests;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.DatabaseUtils;
import io.apicurio.registry.systemtests.framework.KafkaUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import io.apicurio.registry.systemtests.framework.TestNameGenerator;
import io.apicurio.registry.systemtests.operator.OperatorManager;
import io.apicurio.registry.systemtests.operator.types.KeycloakOLMOperatorType;
import io.apicurio.registry.systemtests.operator.types.StrimziClusterOLMOperatorType;
import io.apicurio.registry.systemtests.registryinfra.ResourceManager;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtests.registryinfra.resources.PersistenceKind;
import io.apicurio.registry.systemtests.resolver.ExtensionContextParameterResolver;
import io.strimzi.api.kafka.model.Kafka;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

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

    protected void runTest(
            ExtensionContext testContext,
            PersistenceKind persistenceKind,
            KafkaKind kafkaKind,
            boolean useKeycloak,
            boolean testAPI
    ) throws InterruptedException {
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
            // Deploy PostreSQL with/without Keycloak
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

        if (registry != null && testAPI) {
            // Run API tests
            APITests.run(registry, "registry-admin", "changeme", useKeycloak);

            // TODO: Add more users to check API
        }

        if (useKeycloak) {
            // Remove Keycloak
            KeycloakUtils.removeKeycloak();
        }
    }
}
