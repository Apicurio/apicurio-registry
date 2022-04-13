package io.apicurio.registry.systemtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtest.framework.DatabaseUtils;
import io.apicurio.registry.systemtest.operator.types.ApicurioRegistryBundleOperatorType;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.resources.ApicurioRegistryResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.DeploymentResourceType;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;

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
        ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultMem();

        try {
            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }

        resourceManager.deleteResources(testContext);
    }

    @Test
    public void testApicurioRegistryWithSqlPersistenceBecomeReady(ExtensionContext testContext) {
        DatabaseUtils.deployDefaultPostgresqlDatabase(testContext);

        ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql("rkubis-test", "rkubis-namespace");

        try {
            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }

        resourceManager.deleteResources(testContext);
    }

    /*
    Operator test from file
     */

    @Test
    public void testInstallApicurioRegistryBundleOperatorFile(ExtensionContext testContext) {
        operatorManager.installOperator(new ApicurioRegistryBundleOperatorType("/Users/rkubis/codes/apicurio/install/install.yaml"));
    }

    @Test
    public void testUninstallApicurioRegistryBundleOperatorFile(ExtensionContext testContext) {
        operatorManager.uninstallOperator(new ApicurioRegistryBundleOperatorType("/Users/rkubis/codes/apicurio/install/install.yaml"));
    }

    /*
    Operator test from URL
     */

    @Test
    public void testInstallApicurioRegistryBundleOperatorUrl(ExtensionContext testContext) {
        operatorManager.installOperator(new ApicurioRegistryBundleOperatorType("http://radimkubis.cz/install.yaml"), true);
    }

    @Test
    public void testUninstallApicurioRegistryBundleOperatorUrl(ExtensionContext testContext) {
        operatorManager.uninstallOperator(new ApicurioRegistryBundleOperatorType("http://radimkubis.cz/install.yaml"), true);
    }

    @Test
    public void testYamlOutput(ExtensionContext testContext) {
        Deployment deployment = DeploymentResourceType.getDefaultPostgresql();

        try {
            String yaml = SerializationUtils.dumpAsYaml(deployment);

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
        List<HasMetadata> result = Kubernetes.getClient().load(new FileInputStream("/Users/rkubis/codes/apicurio/install/install.yaml")).get();
        // Apply Kubernetes Resources
        // Kubernetes.getClient().resourceList(result).inNamespace("rkubis-namespace").createOrReplace();

        for (HasMetadata r : result) {
            testLogger.info(r.getKind());
        }

    }
}
