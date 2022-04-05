package io.apicurio.registry.systemtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtest.framework.DatabaseUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.resources.ApicurioRegistryResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.DeploymentResourceType;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.HashMap;

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

        ApicurioRegistry apicurioRegistry = ApicurioRegistryResourceType.getDefaultSql();

        try {
            resourceManager.createResource(testContext, true, apicurioRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }

        resourceManager.deleteResources(testContext);
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
    public void testCode(ExtensionContext testContext) {
        testLogger.info(Kubernetes.getClient().pods().inNamespace("postgresql").withLabels(new HashMap<String, String>() {{ put("app", "postgresql"); }}).list().getItems().toString());
    }
}
