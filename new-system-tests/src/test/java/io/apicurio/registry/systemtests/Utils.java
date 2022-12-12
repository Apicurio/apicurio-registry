package io.apicurio.registry.systemtests;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Utilities used in test suite and tests.
 */
public class Utils {
    /**
     * Finds {@link Deployment} of Apicurio Registry operator in provided namespace.
     *
     * @param client {@link OpenShiftClient} instance to use for interaction with cluster.
     * @param namespace Name of namespace to search for Apicurio Registry operator {@link Deployment}.
     * @return {@link Deployment} of Apicurio Registry operator if {@link Deployment} was found; {@code null} otherwise.
     */
    public static Deployment findRegistryOperatorDeployment(OpenShiftClient client, String namespace) {
        return client
                .apps()
                .deployments()
                .inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .filter(d -> d.getMetadata().getName().startsWith(Constants.REGISTRY_OPERATOR_NAME))
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds {@link Deployment} of Apicurio Registry operator in client's namespace.
     *
     * @param client {@link OpenShiftClient} instance to use for interaction with cluster.
     * @return {@link Deployment} of Apicurio Registry operator if {@link Deployment} was found; {@code null} otherwise.
     */
    public static Deployment findRegistryOperatorDeployment(OpenShiftClient client) {
        return findRegistryOperatorDeployment(client, client.getNamespace());
    }

    /**
     * Waits for readiness of {@link Deployment} in provided namespace for provided timeout.
     *
     * @param client {@link OpenShiftClient} instance to use for interaction with cluster.
     * @param namespace Name of namespace of {@link Deployment}.
     * @param name Name of {@link Deployment} to wait for.
     * @param timeUnit {@link TimeUnit} for waiting.
     * @param amount Amount of {@link TimeUnit}s to wait.
     * @return {@code true} if {@link Deployment} becomes ready within provided timeout; {@code false} otherwise.
     */
    public static boolean waitDeploymentReady(OpenShiftClient client, String namespace, String name, TimeUnit timeUnit, long amount) {
        try {
            client
                    .apps()
                    .deployments()
                    .inNamespace(namespace)
                    .withName(name)
                    .waitUntilReady(amount, timeUnit);
        } catch (KubernetesClientTimeoutException e) {
            LoggerFactory.getLogger(Utils.class).error(
                    "Deployment '" + name + "' in namespace '" + namespace + "' failed readiness check within " + amount + " " + timeUnit.name() + "."
            );

            return false;
        }

        return true;
    }

    /**
     * Waits for readiness of {@link Deployment} in client's namespace for pre-defined timeout 5 minutes.
     *
     * @param client {@link OpenShiftClient} instance to use for interaction with cluster.
     * @param name Name of {@link Deployment} to wait for.
     * @return {@code true} if {@link Deployment} becomes ready within pre-defined timeout 5 minutes;
     * {@code false} otherwise.
     */
    public static boolean waitDeploymentReady(OpenShiftClient client, String name) {
        return waitDeploymentReady(client, client.getNamespace(), name, TimeUnit.MINUTES, 5);
    }

    /**
     * Checks {@link Deployment} readiness in provided namespace.
     *
     * @param client {@link OpenShiftClient} instance to use for interaction with cluster.
     * @param namespace Name of namespace of {@link Deployment} to check.
     * @param name Name of {@link Deployment} to check.
     * @return {@code true} if {@link Deployment} is ready; {@code false} otherwise.
     */
    public static boolean isDeploymentReady(OpenShiftClient client, String namespace, String name) {
        return client
                .apps()
                .deployments()
                .inNamespace(namespace)
                .withName(name)
                .isReady();
    }

    /**
     * Checks {@link Deployment} readiness in client's namespace.
     *
     * @param client {@link OpenShiftClient} instance to use for interaction with cluster.
     * @param name Name of {@link Deployment} to check.
     * @return {@code true} if {@link Deployment} is ready; {@code false} otherwise.
     */
    public static boolean isDeploymentReady(OpenShiftClient client, String name) {
        return isDeploymentReady(client, client.getNamespace(), name);
    }
}
