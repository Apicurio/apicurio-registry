package io.apicurio.deployment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static io.apicurio.deployment.Constants.REGISTRY_IMAGE;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_KUBERNETESOPS_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.KUBERNETESOPS_CONFIGMAPS;
import static io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE;
import static io.apicurio.deployment.RegistryDeploymentManager.kubernetesClient;

public class KubernetesOpsDeploymentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesOpsDeploymentManager.class);

    static void deployKubernetesOpsApp(String registryImage) throws Exception {
        LOGGER.info("Deploying test ConfigMaps for KubernetesOps storage...");

        // Deploy the test ConfigMaps first so the registry has data to load on startup
        kubernetesClient
                .load(KubernetesOpsDeploymentManager.class.getResourceAsStream(KUBERNETESOPS_CONFIGMAPS))
                .serverSideApply();

        LOGGER.info("Test ConfigMaps created in namespace {}", TEST_NAMESPACE);

        // Deploy the registry with kubernetesops storage (includes RBAC + Deployment + Service)
        InputStream resourceStream = KubernetesOpsDeploymentManager.class
                .getResourceAsStream(APPLICATION_KUBERNETESOPS_RESOURCES);
        assert resourceStream != null;

        String resources = IOUtils.toString(resourceStream, StandardCharsets.UTF_8.name());
        if (registryImage != null) {
            resources = resources.replace(REGISTRY_IMAGE, registryImage);
        }

        kubernetesClient
                .load(IOUtils.toInputStream(resources, StandardCharsets.UTF_8.name()))
                .serverSideApply();

        LOGGER.info("Registry resources applied, waiting for pod to be created...");

        // Wait for the Deployment to create at least one pod before waiting for readiness.
        // Without this, waitUntilReady can fail immediately if no pods exist yet.
        String podName = null;
        int maxWait = 120;
        for (int i = 0; i < maxWait; i++) {
            var pods = kubernetesClient.pods().inNamespace(TEST_NAMESPACE)
                    .withLabel("app", "apicurio-registry-kubernetesops").list();
            if (!pods.getItems().isEmpty()) {
                podName = pods.getItems().get(0).getMetadata().getName();
                LOGGER.info("Pod '{}' created, waiting for readiness...", podName);
                break;
            }
            Thread.sleep(1000);
        }

        if (podName == null) {
            throw new RuntimeException(
                    "No pod created for apicurio-registry-kubernetesops after " + maxWait + " seconds");
        }

        // Wait for the specific pod by name to avoid the name:[null] issue with label-based waits
        kubernetesClient.pods().inNamespace(TEST_NAMESPACE)
                .withName(podName)
                .waitUntilReady(600, TimeUnit.SECONDS);

        LOGGER.info("Registry pod '{}' is ready", podName);

        // Set up test networking so tests connect to the correct host
        RegistryDeploymentManager.setupTestNetworking();

        // Wait for the registry HTTP endpoint to be accessible
        RegistryDeploymentManager.waitForRegistryReady();
    }
}
