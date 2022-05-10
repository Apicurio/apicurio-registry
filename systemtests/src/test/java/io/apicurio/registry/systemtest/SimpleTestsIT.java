package io.apicurio.registry.systemtest;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtest.client.ApicurioRegistryApiClient;
import io.apicurio.registry.systemtest.client.ArtifactType;
import io.apicurio.registry.systemtest.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtest.framework.DatabaseUtils;
import io.apicurio.registry.systemtest.framework.Environment;
import io.apicurio.registry.systemtest.framework.KafkaUtils;
import io.apicurio.registry.systemtest.framework.KeycloakUtils;
import io.apicurio.registry.systemtest.framework.LoggerUtils;
import io.apicurio.registry.systemtest.operator.types.ApicurioRegistryBundleOperatorType;
import io.apicurio.registry.systemtest.operator.types.ApicurioRegistryOLMOperatorType;
import io.apicurio.registry.systemtest.operator.types.KeycloakOLMOperatorType;
import io.apicurio.registry.systemtest.operator.types.StrimziClusterBundleOperatorType;
import io.apicurio.registry.systemtest.operator.types.StrimziClusterOLMOperatorType;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.resources.ApicurioRegistryResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.KafkaConnectResourceType;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaConnect;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.AssertionFailedError;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
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
    public void testInstallStrimziClusterBundleOperatorGitHubRepoFirst(ExtensionContext testContext) {
        try {
            // Ability to install bundle operator from GitHub repository,
            // source should contain repository URL and path to operator files inside repository,
            // these two parts are joined by semicolon
            StrimziClusterBundleOperatorType strimziClusterBundleOperatorType = new StrimziClusterBundleOperatorType("https://github.com/jboss-container-images/amqstreams-1-openshift-image;install/cluster-operator");

            operatorManager.installOperator(testContext, strimziClusterBundleOperatorType);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            operatorManager.uninstallOperators(testContext);
        }
    }

    @Test
    public void testInstallStrimziClusterBundleOperatorGitHubRepoSecond(ExtensionContext testContext) {
        try {
            // Ability to install bundle operator from GitHub repository,
            // source should contain repository URL and path to operator files inside repository,
            // these two parts are joined by semicolon
            StrimziClusterBundleOperatorType strimziClusterBundleOperatorType = new StrimziClusterBundleOperatorType("https://github.com/strimzi/strimzi-kafka-operator;install/cluster-operator");

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

            ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-test-instance", Environment.APICURIO_OPERATOR_NAMESPACE);

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
            ApicurioRegistryOLMOperatorType testOperator = new ApicurioRegistryOLMOperatorType(Environment.APICURIO_OLM_CATALOG_SOURCE_IMAGE, Environment.APICURIO_OPERATOR_NAMESPACE,false);

            operatorManager.installOperator(testContext, testOperator);

            DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

            ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-operator-namespace-test-instance", Environment.APICURIO_OPERATOR_NAMESPACE);

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
            ApicurioRegistryOLMOperatorType testOperator = new ApicurioRegistryOLMOperatorType(Environment.APICURIO_OLM_CATALOG_SOURCE_IMAGE, null,true);

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
    public void testInstallApicurioRegistryOLMOperatorNamespacedWithoutCatalogSourceImage(ExtensionContext testContext) {
        // Use when default catalog is already present only
        try {
            ApicurioRegistryOLMOperatorType testOperator = new ApicurioRegistryOLMOperatorType(null, Environment.APICURIO_OPERATOR_NAMESPACE,false);

            operatorManager.installOperator(testContext, testOperator);

            DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

            ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-operator-namespace-test-instance", Environment.APICURIO_OPERATOR_NAMESPACE);

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
    @Disabled
    public void testInstallApicurioRegistryOLMOperatorClusterWideWithoutCatalogSourceImage(ExtensionContext testContext) {
        try {
            ApicurioRegistryOLMOperatorType testOperator = new ApicurioRegistryOLMOperatorType(null, null,true);

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

            KeycloakUtils.deployKeycloak(testContext, keycloakOLMOperatorType.getNamespaceName());

            // Keycloak should be deployed here

            KeycloakUtils.removeKeycloak(keycloakOLMOperatorType.getNamespaceName());
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

            Kafka kafkasqlTls = KafkaUtils.deployDefaultKafkaTls(testContext);

            ApicurioRegistryOLMOperatorType apicurioRegistryOLMOperatorType = new ApicurioRegistryOLMOperatorType(Environment.APICURIO_OLM_CATALOG_SOURCE_IMAGE, null, true);

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

            Kafka kafkasqlScram = KafkaUtils.deployDefaultKafkaScram(testContext);

            ApicurioRegistryOLMOperatorType apicurioRegistryOLMOperatorType = new ApicurioRegistryOLMOperatorType(Environment.APICURIO_OLM_CATALOG_SOURCE_IMAGE, null, true);

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
    public void testCreateKafkaConnect(ExtensionContext testContext) {
        try {
            StrimziClusterBundleOperatorType strimziClusterBundleOperatorType = new StrimziClusterBundleOperatorType();

            operatorManager.installOperator(testContext, strimziClusterBundleOperatorType);

            KafkaUtils.deployDefaultKafkaNoAuth(testContext);

            ApicurioRegistryOLMOperatorType apicurioRegistryOLMOperatorType = new ApicurioRegistryOLMOperatorType(Environment.APICURIO_OLM_CATALOG_SOURCE_IMAGE, null, true);

            operatorManager.installOperator(testContext, apicurioRegistryOLMOperatorType);

            ApicurioRegistryUtils.deployDefaultApicurioRegistryKafkasqlNoAuth(testContext);

            KafkaConnect kafkaConnect = KafkaConnectResourceType.getDefault();

            resourceManager.createResource(testContext, true, kafkaConnect);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            resourceManager.deleteResources(testContext);

            operatorManager.uninstallOperators(testContext);
        }
    }

    @Test
    public void testInstallStrimziOLMOperatorClusterWide(ExtensionContext testContext) {
        try {
            StrimziClusterOLMOperatorType strimziClusterOLMOperatorTypeClusterWide = new StrimziClusterOLMOperatorType(null, null, true);

            operatorManager.installOperator(testContext, strimziClusterOLMOperatorTypeClusterWide);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            operatorManager.uninstallOperators(testContext);
        }
    }

    @Test
    public void testInstallStrimziOLMOperatorNamespaced(ExtensionContext testContext) {
        try {
            StrimziClusterOLMOperatorType strimziClusterOLMOperatorTypeNamespaced = new StrimziClusterOLMOperatorType(null, "strimzi-olm-namespace", false);

            operatorManager.installOperator(testContext, strimziClusterOLMOperatorTypeNamespaced);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            operatorManager.uninstallOperators(testContext);
        }
    }

    @Test
    @Disabled
    public void testApicurioRegistryApiClient(ExtensionContext testContext) throws URISyntaxException, IOException, InterruptedException {
        ApicurioRegistryApiClient apicurioRegistryApiClient = new ApicurioRegistryApiClient("apicurio-registry-test-kafkasql.apicurio-registry-operator-namespace.router-default.apps.ocp4-707.0422-bd1.fw.rhcloud.com", 80);

        String artifactGroup = "artifact-group";
        String artifactId = "artifact-id";
        String artifactData = new JSONObject()
                .put("type", "record")
                .put("name", "price")
                .toString();

        TEST_LOGGER.info("=== List artifacts ===");
        for (String s : apicurioRegistryApiClient.listArtifacts()) {
            TEST_LOGGER.info(s);
        }

        TEST_LOGGER.info("=== Create artifact " + artifactGroup + "/" + artifactId + " with data=" + artifactData + " ===");
        apicurioRegistryApiClient.createArtifact(artifactGroup, artifactId, ArtifactType.AVRO, artifactData);

        TEST_LOGGER.info("=== List artifacts ===");
        for (String s : apicurioRegistryApiClient.listArtifacts()) {
            TEST_LOGGER.info(s);
        }

        TEST_LOGGER.info("=== Read artifact " + artifactGroup + "/" + artifactId + " ===");
        TEST_LOGGER.info(apicurioRegistryApiClient.readArtifactContent(artifactGroup, artifactId));

        TEST_LOGGER.info("=== Delete artifact " + artifactGroup + "/" + artifactId + " ===");
        apicurioRegistryApiClient.deleteArtifact(artifactGroup, artifactId);

        TEST_LOGGER.info("=== List artifacts ===");
        for (String s : apicurioRegistryApiClient.listArtifacts()) {
            TEST_LOGGER.info(s);
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
