package io.apicurio.registry.systemtests.framework;

import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.apicurio.registry.systemtests.time.TimeoutBudget;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class DeploymentUtils {

    private static final Logger LOGGER = LoggerUtils.getLogger();

    public static boolean deploymentEnvVarExists(Deployment deployment, EnvVar envVar) {
        return deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getEnv()
                .stream()
                .anyMatch(ev -> ev.getName().equals(envVar.getName()));
    }

    public static EnvVar getDeploymentEnvVar(Deployment deployment, String envVarName) {
        return deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getEnv()
                .stream()
                .filter(ev -> ev.getName().equals(envVarName))
                .findFirst()
                .orElse(null);
    }

    public static void addDeploymentEnvVar(Deployment deployment, EnvVar envVar) {
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getEnv()
                .add(envVar);
    }

    public static boolean waitDeploymentReady(String namespace, String name) {
        return waitDeploymentReady(namespace, name, TimeoutBudget.ofDuration(Duration.ofMinutes(5)));
    }

    public static boolean waitDeploymentReady(String namespace, String name, TimeoutBudget timeoutBudget) {
        while (!timeoutBudget.timeoutExpired()) {
            if (Kubernetes.isDeploymentReady(namespace, name)) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        if (!Kubernetes.isDeploymentReady(namespace, name)) {
            LOGGER.error("Deployment with name {} in namespace {} failed readiness check.", name, namespace);

            return false;
        }

        return true;
    }

    public static boolean waitDeploymentHasUnavailableReplicas(String namespace, String name) {
        return waitDeploymentHasUnavailableReplicas(namespace, name, TimeoutBudget.ofDuration(Duration.ofMinutes(3)));
    }

    public static boolean waitDeploymentHasUnavailableReplicas(
            String namespace,
            String name,
            TimeoutBudget timeoutBudget
    ) {
        while (!timeoutBudget.timeoutExpired()) {
            if (Kubernetes.deploymentHasUnavailableReplicas(namespace, name)) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        if (Kubernetes.deploymentHasUnavailableReplicas(namespace, name)) {
            LOGGER.error("Deployment {} in namespace {} failed unavailable replicas check.", name, namespace);

            return false;
        }

        return true;
    }

    public static void createOrReplaceDeploymentEnvVar(Deployment deployment, EnvVar envVar) {
        createOrReplaceDeploymentEnvVars(deployment, Collections.singletonList(envVar));
    }

    public static void createOrReplaceDeploymentEnvVars(Deployment deployment, List<EnvVar> envVars) {
        // Get deployment namespace
        String deploymentNamespace = deployment.getMetadata().getNamespace();
        // Get deployment name
        String deploymentName = deployment.getMetadata().getName();

        for (EnvVar ev : envVars) {
            // If environment variable already exists in deployment
            if (deploymentEnvVarExists(deployment, ev)) {
                // Log information about current action
                LOGGER.info("Setting environment variable {} of deployment {} to {}.",
                        ev.getName(), deploymentName, ev.getValue());

                // Set value of environment variable
                getDeploymentEnvVar(deployment, ev.getName()).setValue(ev.getValue());
            } else {
                // Log information about current action
                LOGGER.info("Adding environment variable {} with value {} to deployment {}.",
                        ev.getName(), ev.getValue(), deploymentName);

                // Add environment variable if it does not exist yet
                addDeploymentEnvVar(deployment, ev);
            }
        }

        // Update deployment
        Kubernetes.createOrReplaceDeployment(deploymentNamespace, deployment);

        // Wait for deployment reload
        Assertions.assertTrue(waitDeploymentHasUnavailableReplicas(deploymentNamespace, deploymentName));

        // Wait for deployment readiness
        Assertions.assertTrue(waitDeploymentReady(deploymentNamespace, deploymentName));

        // Get deployment
        deployment = Kubernetes.getDeployment(deploymentNamespace, deploymentName);

        for (EnvVar ev : envVars) {
            // Check value of environment variable
            Assertions.assertEquals(
                    getDeploymentEnvVar(deployment, ev.getName()).getValue(),
                    ev.getValue(),
                    MessageFormat.format("Environment variable {0} of deployment {1} was NOT set to {2}.",
                            ev.getName(), deploymentName, ev.getValue()
                    )
            );
        }
    }
}
