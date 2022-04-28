package io.apicurio.registry.systemtest;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtest.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtest.framework.DatabaseUtils;
import io.apicurio.registry.systemtest.framework.KafkaUtils;
import io.apicurio.registry.systemtest.framework.LoggerUtils;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.framework.Utils;
import io.apicurio.registry.systemtest.operator.types.ApicurioRegistryBundleOperatorType;
import io.apicurio.registry.systemtest.operator.types.ApicurioRegistryOLMOperatorType;
import io.apicurio.registry.systemtest.operator.types.KeycloakOLMOperatorType;
import io.apicurio.registry.systemtest.operator.types.StrimziClusterBundleOperatorType;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.resources.ApicurioRegistryResourceType;
import io.strimzi.api.kafka.model.Kafka;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.AssertionFailedError;
import org.slf4j.Logger;

import java.text.MessageFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SimpleTestsIT extends TestBase {
    protected static final Logger TEST_LOGGER = LoggerUtils.getLogger();

    @BeforeAll
    public static void prepareInfra() {
        TEST_LOGGER.info("Prepare infra before all tests.");
    }

    @AfterAll
    public static void destroyInfra() {
        TEST_LOGGER.info("Destroy infra after all tests.");
    }

    @Test
    public void testApicurioRegistryWithMemPersistenceBecomeReady(ExtensionContext testContext) {
        try {
            ApicurioRegistryBundleOperatorType apicurioRegistryBundleOperatorType = new ApicurioRegistryBundleOperatorType();

            operatorManager.installOperator(testContext, apicurioRegistryBundleOperatorType);

            ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultMem("apicurio-registry-test-mem", apicurioRegistryBundleOperatorType.getNamespaceName());

            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            resourceManager.deleteResources(testContext);

            operatorManager.uninstallOperators(testContext);
        }
    }

    @Test
    public void testApicurioRegistryWithSqlPersistenceBecomeReady(ExtensionContext testContext) {
        try {
            ApicurioRegistryBundleOperatorType apicurioRegistryBundleOperatorType = new ApicurioRegistryBundleOperatorType();

            operatorManager.installOperator(testContext, apicurioRegistryBundleOperatorType);

            DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

            ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-test-sql", apicurioRegistryBundleOperatorType.getNamespaceName());

            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            resourceManager.deleteResources(testContext);

            operatorManager.uninstallOperators(testContext);
        }
    }

    @Test
    public void testApicurioRegistryWithKafkasqlPersistenceBecomeReady(ExtensionContext testContext) {
        try {
            StrimziClusterBundleOperatorType strimziClusterBundleOperatorType = new StrimziClusterBundleOperatorType();

            operatorManager.installOperator(testContext, strimziClusterBundleOperatorType);

            ApicurioRegistryBundleOperatorType apicurioRegistryBundleOperatorType = new ApicurioRegistryBundleOperatorType();

            operatorManager.installOperator(testContext, apicurioRegistryBundleOperatorType);

            KafkaUtils.deployDefaultKafkaNoAuth(testContext);

            ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultKafkasql("apicurio-registry-test-kafkasql", apicurioRegistryBundleOperatorType.getNamespaceName());

            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            resourceManager.deleteResources(testContext);

            operatorManager.uninstallOperators(testContext);
        }
    }

    @Test
    public void testInstallApicurioRegistryBundleOperatorFile(ExtensionContext testContext) {
        try {
            ApicurioRegistryBundleOperatorType apicurioRegistryBundleOperatorType = new ApicurioRegistryBundleOperatorType("/Users/rkubis/codes/apicurio/install/install.yaml");

            operatorManager.installOperator(testContext, apicurioRegistryBundleOperatorType);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            operatorManager.uninstallOperators(testContext);
        }
    }

    @Test
    public void testInstallStrimziClusterBundleOperatorUrl(ExtensionContext testContext) {
        try {
            StrimziClusterBundleOperatorType strimziClusterBundleOperatorType = new StrimziClusterBundleOperatorType();

            operatorManager.installOperator(testContext, strimziClusterBundleOperatorType);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            operatorManager.uninstallOperators(testContext);
        }
    }

    @Test
    public void testInstallApicurioRegistry(ExtensionContext testContext) {
        try {
            ApicurioRegistryBundleOperatorType testOperator = new ApicurioRegistryBundleOperatorType("http://radimkubis.cz/apicurio_install.yaml");

            operatorManager.installOperator(testContext, testOperator);

            DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

            ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-test-instance", OperatorUtils.getApicurioRegistryOperatorNamespace());

            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            resourceManager.deleteResources(testContext);

            operatorManager.uninstallOperators(testContext);
        }
    }

    @Test
    public void testInstallApicurioRegistryOLMOperatorNamespaced(ExtensionContext testContext) {
        try {
            ApicurioRegistryOLMOperatorType testOperator = new ApicurioRegistryOLMOperatorType(OperatorUtils.getApicurioRegistryOLMOperatorCatalogSourceImage(), OperatorUtils.getApicurioRegistryOperatorNamespace(),false);

            operatorManager.installOperator(testContext, testOperator);

            DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

            ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-operator-namespace-test-instance", OperatorUtils.getApicurioRegistryOperatorNamespace());

            // Try to create registry in operator namespace,
            // it should be OK
            resourceManager.createResource(testContext, true, apicurioRegistry);

            // Apicurio Registry should be ready here
            TEST_LOGGER.info(ApicurioRegistryResourceType.getOperation().inNamespace(apicurioRegistry.getMetadata().getNamespace()).withName(apicurioRegistry.getMetadata().getName()).get().getStatus().getConditions().toString());

            ApicurioRegistry apicurioRegistryNamespace = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-operator-namespace-test-instance-fail", "some-namespace");

            // Try to create registry in another namespace than operator namespace,
            // this should fail
            AssertionFailedError assertionFailedError = assertThrows(AssertionFailedError.class, () -> resourceManager.createResource(testContext, true, apicurioRegistryNamespace));

            assertEquals(
                    MessageFormat.format(
                            "Timed out waiting for resource {0} with name {1} to be ready in namespace {2}. ==> expected: <true> but was: <false>",
                            apicurioRegistryNamespace.getKind(), apicurioRegistryNamespace.getMetadata().getName(), apicurioRegistryNamespace.getMetadata().getNamespace()
                    ),
                    assertionFailedError.getMessage()
            );
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            resourceManager.deleteResources(testContext);

            operatorManager.uninstallOperators(testContext);
        }
    }

    @Test
    public void testInstallApicurioRegistryOLMOperatorClusterWide(ExtensionContext testContext) {
        try {
            ApicurioRegistryOLMOperatorType testOperator = new ApicurioRegistryOLMOperatorType(OperatorUtils.getApicurioRegistryOLMOperatorCatalogSourceImage(), null,true);

            operatorManager.installOperator(testContext, testOperator);

            DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

            ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-operator-cluster-wide-test-instance", "my-apicurio-registry-test-namespace");

            resourceManager.createResource(testContext, true, apicurioRegistry);

            // Apicurio Registry should be ready here
            TEST_LOGGER.info(ApicurioRegistryResourceType.getOperation().inNamespace(apicurioRegistry.getMetadata().getNamespace()).withName(apicurioRegistry.getMetadata().getName()).get().getStatus().getConditions().toString());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            resourceManager.deleteResources(testContext);

            operatorManager.uninstallOperators(testContext);
        }
    }

    @Test
    @Disabled
    public void testCreateApicurioRegistryInMyNamespace(ExtensionContext testContext) {
        try {
            // There needs to be Apicurio Registry operator installed

            DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

            ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-test-sql", "apicurio-registry-operator-namespace-e2e-test");

            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            resourceManager.deleteResources(testContext);
        }
    }

    @Test
    public void testInstallKeycloakOLMOperator(ExtensionContext testContext) {
        try {
            KeycloakOLMOperatorType keycloakOLMOperatorType = new KeycloakOLMOperatorType(null, "apicurio-registry-keycloak-namespace");

            operatorManager.installOperator(testContext, keycloakOLMOperatorType);

            // Operator should be ready here
            TEST_LOGGER.info(Kubernetes.getClient().apps().deployments().inNamespace(keycloakOLMOperatorType.getNamespaceName()).withName(keycloakOLMOperatorType.getDeploymentName()).get().getStatus().getConditions().toString());

            Utils.deployKeycloak(testContext, keycloakOLMOperatorType.getNamespaceName());

            // Keycloak should be deployed here

            Utils.removeKeycloak(keycloakOLMOperatorType.getNamespaceName());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            resourceManager.deleteResources(testContext);

            operatorManager.uninstallOperators(testContext);
        }
    }

    @Test
    public void testApicurioRegistryKafkasqlTLS(ExtensionContext testContext) {
        try {
            StrimziClusterBundleOperatorType strimziClusterBundleOperatorType = new StrimziClusterBundleOperatorType();

            operatorManager.installOperator(testContext, strimziClusterBundleOperatorType);

            Kafka kafkasqlTls = KafkaUtils.deployDefaultKafkaTLS(testContext);

            ApicurioRegistryOLMOperatorType apicurioRegistryOLMOperatorType = new ApicurioRegistryOLMOperatorType(OperatorUtils.getApicurioRegistryOLMOperatorCatalogSourceImage(), null, true);

            operatorManager.installOperator(testContext, apicurioRegistryOLMOperatorType);

            ApicurioRegistryUtils.deployDefaultApicurioRegistryKafkasqlTLS(testContext, kafkasqlTls);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            resourceManager.deleteResources(testContext);

            operatorManager.uninstallOperators(testContext);
        }
    }

    @Test
    public void testApicurioRegistryKafkasqlSCRAM(ExtensionContext testContext) {
        try {
            StrimziClusterBundleOperatorType strimziClusterBundleOperatorType = new StrimziClusterBundleOperatorType();

            operatorManager.installOperator(testContext, strimziClusterBundleOperatorType);

            Kafka kafkasqlScram = KafkaUtils.deployDefaultKafkaSCRAM(testContext);

            ApicurioRegistryOLMOperatorType apicurioRegistryOLMOperatorType = new ApicurioRegistryOLMOperatorType(OperatorUtils.getApicurioRegistryOLMOperatorCatalogSourceImage(), null, true);

            operatorManager.installOperator(testContext, apicurioRegistryOLMOperatorType);

            ApicurioRegistryUtils.deployDefaultApicurioRegistryKafkasqlSCRAM(testContext, kafkasqlScram);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            resourceManager.deleteResources(testContext);

            operatorManager.uninstallOperators(testContext);
        }
    }

    @Test
    @Disabled
    public void testYamlOutput(ExtensionContext testContext) {
        // Just for checking YAML output of resources

        /*
        try {
            String yaml = SerializationUtils.dumpAsYaml(object);

            TEST_LOGGER.info(yaml);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
         */
    }

    @Test
    @Disabled
    public void testCode(ExtensionContext testContext) {
        // Just for trying Java code
    }
}
