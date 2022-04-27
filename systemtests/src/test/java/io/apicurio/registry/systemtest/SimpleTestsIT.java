package io.apicurio.registry.systemtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtest.framework.DatabaseUtils;
import io.apicurio.registry.systemtest.framework.LoggerUtils;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.framework.Utils;
import io.apicurio.registry.systemtest.operator.types.ApicurioRegistryBundleOperatorType;
import io.apicurio.registry.systemtest.operator.types.ApicurioRegistryOLMOperatorType;
import io.apicurio.registry.systemtest.operator.types.KeycloakOLMOperatorType;
import io.apicurio.registry.systemtest.operator.types.StrimziClusterBundleOperatorType;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.resources.ApicurioRegistryResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.KafkaResourceType;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.SubscriptionBuilder;
import io.strimzi.api.kafka.model.Kafka;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.AssertionFailedError;
import org.slf4j.Logger;

import java.io.FileNotFoundException;
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
        ApicurioRegistryBundleOperatorType apicurioRegistryBundleOperatorType = new ApicurioRegistryBundleOperatorType();

        operatorManager.installOperator(testContext, apicurioRegistryBundleOperatorType);

        ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultMem("apicurio-registry-test-mem", apicurioRegistryBundleOperatorType.getNamespaceName());

        try {
            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }

        resourceManager.deleteResources(testContext);

        operatorManager.uninstallOperators(testContext);
    }

    @Test
    public void testApicurioRegistryWithSqlPersistenceBecomeReady(ExtensionContext testContext) {
        ApicurioRegistryBundleOperatorType apicurioRegistryBundleOperatorType = new ApicurioRegistryBundleOperatorType();

        operatorManager.installOperator(testContext, apicurioRegistryBundleOperatorType);

        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

        ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-test-sql", apicurioRegistryBundleOperatorType.getNamespaceName());

        try {
            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }

        resourceManager.deleteResources(testContext);

        operatorManager.uninstallOperators(testContext);
    }

    @Test
    public void testApicurioRegistryWithKafkasqlPersistenceBecomeReady(ExtensionContext testContext) {
        StrimziClusterBundleOperatorType strimziClusterBundleOperatorType = new StrimziClusterBundleOperatorType();

        operatorManager.installOperator(testContext, strimziClusterBundleOperatorType);

        ApicurioRegistryBundleOperatorType apicurioRegistryBundleOperatorType = new ApicurioRegistryBundleOperatorType();

        operatorManager.installOperator(testContext, apicurioRegistryBundleOperatorType);

        Kafka kafka = KafkaResourceType.getDefault();

        try {
            resourceManager.createResource(testContext, true, kafka);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultKafkasql("apicurio-registry-test-kafkasql", apicurioRegistryBundleOperatorType.getNamespaceName());

        try {
            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }

        resourceManager.deleteResources(testContext);

        operatorManager.uninstallOperators(testContext);

    }

    @Test
    public void testInstallApicurioRegistryBundleOperatorFile(ExtensionContext testContext) {
        ApicurioRegistryBundleOperatorType apicurioRegistryBundleOperatorType = new ApicurioRegistryBundleOperatorType("/Users/rkubis/codes/apicurio/install/install.yaml");

        operatorManager.installOperator(testContext, apicurioRegistryBundleOperatorType);

        operatorManager.uninstallOperators(testContext);
    }

    @Test
    public void testInstallStrimziClusterBundleOperatorUrl(ExtensionContext testContext) {
        StrimziClusterBundleOperatorType strimziClusterBundleOperatorType = new StrimziClusterBundleOperatorType();

        operatorManager.installOperator(testContext, strimziClusterBundleOperatorType);

        operatorManager.uninstallOperators(testContext);
    }

    @Test
    public void testInstallApicurioRegistry(ExtensionContext testContext) {
        ApicurioRegistryBundleOperatorType testOperator = new ApicurioRegistryBundleOperatorType("http://radimkubis.cz/apicurio_install.yaml");

        operatorManager.installOperator(testContext, testOperator);

        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

        ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-test-instance", OperatorUtils.getApicurioRegistryOperatorNamespace());

        try {
            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }

        resourceManager.deleteResources(testContext);

        operatorManager.uninstallOperators(testContext);
    }

    @Test
    public void testInstallApicurioRegistryOLMOperatorNamespaced(ExtensionContext testContext) {
        ApicurioRegistryOLMOperatorType testOperator = new ApicurioRegistryOLMOperatorType(OperatorUtils.getApicurioRegistryOLMOperatorCatalogSourceImage(), OperatorUtils.getApicurioRegistryOperatorNamespace(),false);

        operatorManager.installOperator(testContext, testOperator);

        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

        ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-operator-namespace-test-instance", OperatorUtils.getApicurioRegistryOperatorNamespace());

        try {
            // Try to create registry in operator namespace,
            // it should be OK
            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Apicurio Registry should be ready here
        TEST_LOGGER.info(ApicurioRegistryResourceType.getOperation().inNamespace(apicurioRegistry.getMetadata().getNamespace()).withName(apicurioRegistry.getMetadata().getName()).get().getStatus().getConditions().toString());

        ApicurioRegistry apicurioRegistryNamespace = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-operator-namespace-test-instance-fail", "some-namespace");

        try {
            // Try to create registry in another namespace than operator namespace,
            // this should fail
            AssertionFailedError assertionFailedError = assertThrows(AssertionFailedError.class, () -> resourceManager.createResource(testContext, true, apicurioRegistryNamespace));

            assertEquals(
                    MessageFormat.format("Timed out waiting for resource {0} with name {1} to be ready in namespace {2}. ==> expected: <true> but was: <false>", apicurioRegistryNamespace.getKind(), apicurioRegistryNamespace.getMetadata().getName(), apicurioRegistryNamespace.getMetadata().getNamespace()),
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
        ApicurioRegistryOLMOperatorType testOperator = new ApicurioRegistryOLMOperatorType(OperatorUtils.getApicurioRegistryOLMOperatorCatalogSourceImage(), null,true);

        operatorManager.installOperator(testContext, testOperator);

        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

        ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-operator-cluster-wide-test-instance", "my-apicurio-registry-test-namespace");

        try {
            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Apicurio Registry should be ready here
        TEST_LOGGER.info(ApicurioRegistryResourceType.getOperation().inNamespace(apicurioRegistry.getMetadata().getNamespace()).withName(apicurioRegistry.getMetadata().getName()).get().getStatus().getConditions().toString());

        resourceManager.deleteResources(testContext);

        operatorManager.uninstallOperators(testContext);
    }

    @Test
    @Disabled
    public void testCreateApicurioRegistryInMyNamespace(ExtensionContext testContext) {
        // There needs to be Apicurio Registry operator installed

        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

        ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-test-sql", "apicurio-registry-operator-namespace-e2e-test");

        try {
            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testInstallKeycloakOLMOperator(ExtensionContext testContext) {
        KeycloakOLMOperatorType keycloakOLMOperatorType = new KeycloakOLMOperatorType(null, "apicurio-registry-keycloak-namespace");

        operatorManager.installOperator(testContext, keycloakOLMOperatorType);

        // Operator should be ready here
        TEST_LOGGER.info(Kubernetes.getClient().apps().deployments().inNamespace(keycloakOLMOperatorType.getNamespaceName()).withName(keycloakOLMOperatorType.getDeploymentName()).get().getStatus().getConditions().toString());

        Utils.deployKeycloak(testContext, keycloakOLMOperatorType.getNamespaceName());

        // Keycloak should be deployed here

        Utils.removeKeycloak(keycloakOLMOperatorType.getNamespaceName());

        resourceManager.deleteResources(testContext);

        operatorManager.uninstallOperators(testContext);
    }

    @Test
    @Disabled
    public void testYamlOutput(ExtensionContext testContext) {
        // Just for checking YAML output of resources

        Subscription subscription = new SubscriptionBuilder()
                .withNewMetadata()
                    .withName("apicurio-registry-sub")
                    .withNamespace("apicurio-registry-operator-namespace")
                .endMetadata()
                .withNewSpec()
                    .withName(OperatorUtils.getApicurioRegistryOLMOperatorPackage())
                    .withSource("apicurio-registry-catalog-source")
                    .withSourceNamespace("apicurio-registry-catalog-source-namespace")
                    .withStartingCSV("<csv>")
                    .withChannel("<channel>")
                    .withInstallPlanApproval("Automatic")
                .endSpec()
                .build();
        try {
            String yaml = SerializationUtils.dumpAsYaml(subscription);

            TEST_LOGGER.info(yaml);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Test
    @Disabled
    public void testCode(ExtensionContext testContext) throws FileNotFoundException {
        // Just for trying Java code

        // System.getenv()
        // testLogger.info(Kubernetes.getClient().pods().inNamespace("postgresql").withLabels(new HashMap<String, String>() {{ put("app", "postgresql"); }}).list().getItems().toString());
        // testLogger.info(System.getenv("TEST"));

        // Load Yaml into Kubernetes resources
        // List<HasMetadata> result = Kubernetes.getClient().load(new FileInputStream("/Users/rkubis/codes/apicurio/install/install.yaml")).get();
        // Apply Kubernetes Resources
        // Kubernetes.getClient().resourceList(result).inNamespace("rkubis-namespace").createOrReplace();
        // System.out.println(System.getProperty("user.dir"));
        /*
        for (HasMetadata r : result) {
            testLogger.info(r.getKind());
        }
         */
        //Kubernetes.getClient().namespaces().create(new NamespaceBuilder().withNewMetadata().withName("openshift-marketplace").endMetadata().build());
    }
}
