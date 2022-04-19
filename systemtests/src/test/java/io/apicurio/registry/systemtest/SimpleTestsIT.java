package io.apicurio.registry.systemtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtest.framework.DatabaseUtils;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.operator.types.ApicurioRegistryBundleOperatorType;
import io.apicurio.registry.systemtest.operator.types.StrimziClusterBundleOperatorType;
import io.apicurio.registry.systemtest.registryinfra.resources.ApicurioRegistryResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.KafkaResourceType;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import io.strimzi.api.kafka.model.Kafka;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
    public void testInstallApicurioRegistryBundleOperatorUrl(ExtensionContext testContext) {
        ApicurioRegistryBundleOperatorType apicurioRegistryBundleOperatorType = new ApicurioRegistryBundleOperatorType("http://radimkubis.cz/install.yaml");

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
    public void testInstallServiceRegistry(ExtensionContext testContext) {
        ApicurioRegistryBundleOperatorType testOperator = new ApicurioRegistryBundleOperatorType("http://radimkubis.cz/redhat_install.yaml");

        operatorManager.installOperator(testOperator);

        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

        ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("service-registry-test-instance", OperatorUtils.getApicurioRegistryOperatorNamespace());

        try {
            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }

        resourceManager.deleteResources(testContext);

        operatorManager.uninstallOperator(testOperator);
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
    public void testYamlOutput(ExtensionContext testContext) {
        Kafka kafka = KafkaResourceType.getDefault("rkubis", "rkubis");

        try {
            String yaml = SerializationUtils.dumpAsYaml(kafka);

            testLogger.info(yaml);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCode(ExtensionContext testContext) throws FileNotFoundException {
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

    }
}
