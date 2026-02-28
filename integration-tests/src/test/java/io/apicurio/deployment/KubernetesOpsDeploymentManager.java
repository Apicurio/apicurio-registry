package io.apicurio.deployment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_KUBERNETESOPS_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.KUBERNETESOPS_CONFIGMAPS;
import static io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE;
import static io.apicurio.deployment.RegistryDeploymentManager.kubernetesClient;
import static io.apicurio.deployment.RegistryDeploymentManager.prepareTestsInfra;

public class KubernetesOpsDeploymentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesOpsDeploymentManager.class);

    static void deployKubernetesOpsApp(String registryImage) throws Exception {
        LOGGER.info("Deploying test ConfigMaps for KubernetesOps storage...");

        // Deploy the test ConfigMaps first so the registry has data to load on startup
        kubernetesClient
                .load(KubernetesOpsDeploymentManager.class.getResourceAsStream(KUBERNETESOPS_CONFIGMAPS))
                .serverSideApply();

        LOGGER.info("Test ConfigMaps created in namespace {}", TEST_NAMESPACE);

        // Deploy the registry with kubernetesops storage (includes RBAC)
        prepareTestsInfra(null, APPLICATION_KUBERNETESOPS_RESOURCES, false, registryImage);
    }
}
