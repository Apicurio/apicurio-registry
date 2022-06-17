package io.apicurio.registry.systemtests;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.DatabaseUtils;
import io.apicurio.registry.systemtests.framework.KafkaUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import io.apicurio.registry.systemtests.operator.types.ApicurioRegistryOLMOperatorType;
import io.apicurio.registry.systemtests.operator.types.KeycloakOLMOperatorType;
import io.apicurio.registry.systemtests.operator.types.StrimziClusterOLMOperatorType;
import io.apicurio.registry.systemtests.registryinfra.resources.ApicurioRegistryResourceType;
import io.strimzi.api.kafka.model.Kafka;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

public class SimpleTestsIT extends TestBase {
    protected static final Logger LOGGER = LoggerUtils.getLogger();

    @BeforeAll
    public static void prepareInfra() {
        LOGGER.info("Prepare infra before all tests.");
    }

    @AfterAll
    public static void destroyInfra() {
        LOGGER.info("Destroy infra after all tests.");
    }

    @Test
    @Disabled
    public void testDemo(ExtensionContext testContext) {
        try {
            // INSTALL OLM OPERATORS
            // Install Apicurio Registry operator from default catalog
            ApicurioRegistryOLMOperatorType registryOperator = new ApicurioRegistryOLMOperatorType(
                    null,
                    true
            );
            operatorManager.installOperator(testContext, registryOperator);
            // Install Strimzi operator from default catalog
            StrimziClusterOLMOperatorType kafkaOperator = new StrimziClusterOLMOperatorType(true);
            operatorManager.installOperator(testContext, kafkaOperator);
            // Install Keycloak operator from default catalog
            KeycloakOLMOperatorType ssoOperator = new KeycloakOLMOperatorType();
            operatorManager.installOperator(testContext, ssoOperator);

            // PREPARE OTHER RESOURCES
            // Deploy PostgreSQL database
            DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);
            // Deploy KafkaSQL instance with TLS
            Kafka kafkasqlTls = KafkaUtils.deployDefaultKafkaTls(testContext);
            // Deploy Keycloak
            KeycloakUtils.deployKeycloak(testContext);

            // TEST CREATION OF APICURIO REGISTRIES
            // Apicurio Registry with PostgreSQL persistence
            ApicurioRegistry registrySql = ApicurioRegistryResourceType.getDefaultSql(
                    "registry-sql"
            );
            resourceManager.createResource(testContext, true, registrySql);
            // Apicurio Registry with KafkaSQL (with TLS) persistence
            ApicurioRegistryUtils.deployDefaultApicurioRegistryKafkasqlTLS(testContext, kafkasqlTls, false);
            // Apicurio Registry with PostgreSQL persistence and Keycloak authentication
            ApicurioRegistry registryKeycloak = ApicurioRegistryResourceType.getDefaultSql("registry-keycloak");
            ApicurioRegistryResourceType.updateWithDefaultKeycloak(registryKeycloak);
            resourceManager.createResource(testContext, true, registryKeycloak);
        } catch (Exception e) {
            // Print stack trace of exception
            e.printStackTrace();
            // Fail test
            Assertions.fail("Unexpected exception happened.");
        } finally {
            // Remove Keycloak
            KeycloakUtils.removeKeycloak();
            // Remove all resources created during test
            resourceManager.deleteResources(testContext);
            // Uninstall all operators installed during test
            operatorManager.uninstallOperators(testContext);
        }
    }
}
