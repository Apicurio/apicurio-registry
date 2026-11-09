package io.apicurio.deployment;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static io.apicurio.deployment.Constants.REGISTRY_IMAGE;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_GITOPS_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.GITOPS_INIT_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE;
import static io.apicurio.deployment.RegistryDeploymentManager.kubernetesClient;

public class GitOpsDeploymentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitOpsDeploymentManager.class);

    static void deployGitOpsApp(String registryImage) throws Exception {
        LOGGER.info("Deploying init ConfigMap with test data for GitOps storage...");

        kubernetesClient
                .load(GitOpsDeploymentManager.class.getResourceAsStream(GITOPS_INIT_RESOURCES))
                .serverSideApply();

        LOGGER.info("GitOps init ConfigMap created in namespace {}", TEST_NAMESPACE);

        InputStream resourceStream = GitOpsDeploymentManager.class
                .getResourceAsStream(APPLICATION_GITOPS_RESOURCES);
        if (resourceStream == null) {
            throw new IllegalStateException("GitOps resource file not found: " + APPLICATION_GITOPS_RESOURCES);
        }

        String resources = IOUtils.toString(resourceStream, StandardCharsets.UTF_8.name());
        if (registryImage != null) {
            resources = resources.replace(REGISTRY_IMAGE, registryImage);
        }

        kubernetesClient
                .load(IOUtils.toInputStream(resources, StandardCharsets.UTF_8.name()))
                .serverSideApply();

        LOGGER.info("Registry resources applied, waiting for pod to be created...");

        String podName = null;
        int maxWait = 120;
        for (int i = 0; i < maxWait; i++) {
            var pods = kubernetesClient.pods().inNamespace(TEST_NAMESPACE)
                    .withLabel("app", "apicurio-registry-gitops").list();
            if (!pods.getItems().isEmpty()) {
                podName = pods.getItems().get(0).getMetadata().getName();
                LOGGER.info("Pod '{}' created, waiting for readiness...", podName);
                break;
            }
            Thread.sleep(1000);
        }

        if (podName == null) {
            throw new RuntimeException(
                    "No pod created for apicurio-registry-gitops after " + maxWait + " seconds");
        }

        kubernetesClient.pods().inNamespace(TEST_NAMESPACE)
                .withName(podName)
                .waitUntilReady(600, TimeUnit.SECONDS);

        LOGGER.info("Registry pod '{}' is ready", podName);

        RegistryDeploymentManager.setupTestNetworking();
        RegistryDeploymentManager.waitForRegistryReady();
    }
}
