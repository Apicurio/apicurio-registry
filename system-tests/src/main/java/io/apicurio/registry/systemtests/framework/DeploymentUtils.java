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

    public static boolean deploymentEnvVarExists(Deployment deployment, String envVarName) {
        return deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getEnv()
                .stream()
                .anyMatch(ev -> ev.getName().equals(envVarName));
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

    public static void removeDeploymentEnvVar(Deployment deployment, String envVarName) {
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getEnv()
                .remove(getDeploymentEnvVar(deployment, envVarName));
    }

    public static boolean waitDeploymentReady(String namespace, String name) {
        return waitDeploymentReady(namespace, name, TimeoutBudget.ofDuration(Duration.ofMinutes(5)));
    }

    public static boolean waitDeploymentReady(String namespace, String name, TimeoutBudget timeoutBudget) {
        while (!timeoutBudget.timeoutExpired()) {
            if (!Kubernetes.deploymentHasUnavailableReplicas(namespace, name)) {
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

    public static Deployment processChange(Deployment deployment) {
        String namespace = deployment.getMetadata().getNamespace();
        String name = deployment.getMetadata().getName();

        // Update deployment
        Kubernetes.createOrReplaceDeployment(namespace, deployment);

        // Wait for deployment reload
        Assertions.assertTrue(waitDeploymentHasUnavailableReplicas(namespace, name));

        // Wait for deployment readiness
        Assertions.assertTrue(waitDeploymentReady(namespace, name));

        // Return deployment
        return Kubernetes.getDeployment(namespace, name);
    }

    public static void deleteDeploymentEnvVar(Deployment deployment, String envVarName) {
        deleteDeploymentEnvVars(deployment, Collections.singletonList(envVarName));
    }

    public static void deleteDeploymentEnvVars(Deployment deployment, List<String> envVarNames) {
        // Get deployment name
        String dName = deployment.getMetadata().getName();
        // Flag to indicate if deployment was changed
        boolean changed = false;

        for (String evn : envVarNames) {
            // If environment variable already exists in deployment
            if (deploymentEnvVarExists(deployment, evn)) {
                // Log information about current action
                LOGGER.info("Deleting environment variable {} of deployment {}.", evn, dName);

                // Delete environment variable
                removeDeploymentEnvVar(deployment, evn);

                changed = true;
            } else {
                // Log information about current action
                LOGGER.info("Environment variable {} is not present in deployment {}.", evn, dName);
            }
        }

        if (changed) {
            // Process change and get deployment
            deployment = processChange(deployment);

            for (String evn : envVarNames) {
                // Check deletion of environment variable
                Assertions.assertNull(
                        getDeploymentEnvVar(deployment, evn),
                        MessageFormat.format("Environment variable {0} of deployment {1} was NOT deleted.", evn, dName)
                );
            }
        }
    }

    public static void createOrReplaceDeploymentEnvVar(Deployment deployment, EnvVar envVar) {
        createOrReplaceDeploymentEnvVars(deployment, Collections.singletonList(envVar));
    }

    public static void createOrReplaceDeploymentEnvVars(Deployment deployment, List<EnvVar> envVars) {
        // Get deployment name
        String dName = deployment.getMetadata().getName();
        // Flag to indicate if deployment was changed
        boolean changed = false;

        for (EnvVar ev : envVars) {
            String evName = ev.getName();
            String evValue = ev.getValue();

            // If environment variable does not exist
            if (!deploymentEnvVarExists(deployment, evName)) {
                // Log information about current action
                LOGGER.info("Adding environment variable {} with value {} to deployment {}.", evName, evValue, dName);

                addDeploymentEnvVar(deployment, new EnvVar(evName, evValue, null));

                changed = true;
            } else if (!getDeploymentEnvVar(deployment, evName).getValue().equals(evValue)) {
                // If environment variable exists, but has another value

                // Log information about current action
                LOGGER.info("Setting environment variable {} of deployment {} to {}.", evName, dName, evValue);

                // Set value of environment variable
                getDeploymentEnvVar(deployment, evName).setValue(evValue);

                changed = true;
            } else {
                // Log information about current action
                LOGGER.warn("Environment variable {} of deployment {} is already set to {}.", evName, dName, evValue);
            }
        }

        if (changed) {
            // Process change and get deployment
            deployment = processChange(deployment);

            for (EnvVar ev : envVars) {
                // Check value of environment variable
                Assertions.assertEquals(
                        getDeploymentEnvVar(deployment, ev.getName()).getValue(), ev.getValue(),
                        MessageFormat.format("Environment variable {0} of deployment {1} was NOT set to {2}.",
                                ev.getName(), dName, ev.getValue()
                        )
                );
            }
        }
    }
}
