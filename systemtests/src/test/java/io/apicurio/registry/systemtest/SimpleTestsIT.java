package io.apicurio.registry.systemtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtest.framework.DatabaseUtils;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.operator.types.ApicurioRegistryBundleOperatorType;
import io.apicurio.registry.systemtest.operator.types.ApicurioRegistryOLMOperatorType;
import io.apicurio.registry.systemtest.operator.types.StrimziClusterBundleOperatorType;
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

import java.io.FileNotFoundException;

public class SimpleTestsIT extends TestBase {

    @BeforeAll
    public static void prepareInfra() {
        testLogger.info("Prepare infra before all tests.");
    }

    @AfterAll
    public static void destroyInfra() {
        testLogger.info("Destroy infra after all tests.");
    }

    @Test
    public void testApicurioRegistryWithMemPersistenceBecomeReady(ExtensionContext testContext) {
        ApicurioRegistryBundleOperatorType apicurioRegistryBundleOperatorType = new ApicurioRegistryBundleOperatorType();

        operatorManager.installOperator(apicurioRegistryBundleOperatorType);

        ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultMem("apicurio-registry-test-mem", apicurioRegistryBundleOperatorType.getNamespaceName());

        try {
            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }

        resourceManager.deleteResources(testContext);

        operatorManager.uninstallOperator(apicurioRegistryBundleOperatorType);
    }

    @Test
    public void testApicurioRegistryWithSqlPersistenceBecomeReady(ExtensionContext testContext) {
        ApicurioRegistryBundleOperatorType apicurioRegistryBundleOperatorType = new ApicurioRegistryBundleOperatorType();

        operatorManager.installOperator(apicurioRegistryBundleOperatorType);

        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

        ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-test-sql", apicurioRegistryBundleOperatorType.getNamespaceName());

        try {
            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }

        resourceManager.deleteResources(testContext);

        operatorManager.uninstallOperator(apicurioRegistryBundleOperatorType);
    }

    @Test
    public void testApicurioRegistryWithKafkasqlPersistenceBecomeReady(ExtensionContext testContext) {
        StrimziClusterBundleOperatorType strimziClusterBundleOperatorType = new StrimziClusterBundleOperatorType();

        operatorManager.installOperator(strimziClusterBundleOperatorType);

        ApicurioRegistryBundleOperatorType apicurioRegistryBundleOperatorType = new ApicurioRegistryBundleOperatorType();

        operatorManager.installOperator(apicurioRegistryBundleOperatorType);

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

        operatorManager.uninstallOperator(apicurioRegistryBundleOperatorType);

        operatorManager.uninstallOperator(strimziClusterBundleOperatorType);

    }

    @Test
    public void testInstallApicurioRegistryBundleOperatorFile(ExtensionContext testContext) {
        ApicurioRegistryBundleOperatorType apicurioRegistryBundleOperatorType = new ApicurioRegistryBundleOperatorType("/Users/rkubis/codes/apicurio/install/install.yaml");

        operatorManager.installOperator(apicurioRegistryBundleOperatorType);

        operatorManager.uninstallOperator(apicurioRegistryBundleOperatorType);
    }

    @Test
    public void testInstallStrimziClusterBundleOperatorUrl(ExtensionContext testContext) {
        StrimziClusterBundleOperatorType strimziClusterBundleOperatorType = new StrimziClusterBundleOperatorType();

        operatorManager.installOperator(strimziClusterBundleOperatorType);

        operatorManager.uninstallOperator(strimziClusterBundleOperatorType);
    }

    @Test
    public void testInstallApicurioRegistry(ExtensionContext testContext) {
        ApicurioRegistryBundleOperatorType testOperator = new ApicurioRegistryBundleOperatorType("http://radimkubis.cz/apicurio_install.yaml");

        operatorManager.installOperator(testOperator);

        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

        ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-test-instance", OperatorUtils.getApicurioRegistryOperatorNamespace());

        try {
            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }

        resourceManager.deleteResources(testContext);

        operatorManager.uninstallOperator(testOperator);
    }

    @Test
    public void testInstallApicurioRegistryOLMOperatorNamespaced(ExtensionContext testContext) {
        ApicurioRegistryOLMOperatorType testOperator = new ApicurioRegistryOLMOperatorType(OperatorUtils.getApicurioRegistryOLMOperatorCatalogSourceImage(), OperatorUtils.getApicurioRegistryOperatorNamespace(),false);

        operatorManager.installOperator(testOperator);

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
        testLogger.info(ApicurioRegistryResourceType.getOperation().inNamespace(apicurioRegistry.getMetadata().getNamespace()).withName(apicurioRegistry.getMetadata().getName()).get().getStatus().getConditions().toString());

        ApicurioRegistry apicurioRegistryNamespace = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-operator-namespace-test-instance-fail", "some-namespace");

        try {
            // Try to create registry in another namespace than operator namespace,
            // this should fail
            resourceManager.createResource(testContext, true, apicurioRegistryNamespace);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            resourceManager.deleteResources(testContext);

            operatorManager.uninstallOperator(testOperator);
        }
    }

    @Test
    public void testInstallApicurioRegistryOLMOperatorClusterWide(ExtensionContext testContext) {
        ApicurioRegistryOLMOperatorType testOperator = new ApicurioRegistryOLMOperatorType(OperatorUtils.getApicurioRegistryOLMOperatorCatalogSourceImage(), null,true);

        operatorManager.installOperator(testOperator);

        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

        ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("apicurio-registry-operator-cluster-wide-test-instance", "my-apicurio-registry-test-namespace");

        try {
            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Apicurio Registry should be ready here
        testLogger.info(ApicurioRegistryResourceType.getOperation().inNamespace(apicurioRegistry.getMetadata().getNamespace()).withName(apicurioRegistry.getMetadata().getName()).get().getStatus().getConditions().toString());

        resourceManager.deleteResources(testContext);

        operatorManager.uninstallOperator(testOperator);
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

            testLogger.info(yaml);
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
