package io.apicurio.registry.systemtests;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.openshift.client.OpenShiftClient;
import io.strimzi.api.kafka.model.Kafka;
import org.awaitility.core.ConditionTimeoutException;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/**
 * Utilities used in test suite and tests.
 */
public class Utils {
    /**
     * Finds {@link Deployment} of operator in provided namespace.
     *
     * @param client {@link OpenShiftClient} instance to use for interaction with cluster.
     * @param namespace Name of namespace to search for operator {@link Deployment}.
     * @return {@link Deployment} of operator if {@link Deployment} was found; {@code null} otherwise.
     */
    public static Deployment findOperatorDeployment(OpenShiftClient client, String namespace, String name) {
        return client
                .apps()
                .deployments()
                .inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .filter(d -> d.getMetadata().getName().startsWith(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds {@link Deployment} of operator in client's namespace.
     *
     * @param client {@link OpenShiftClient} instance to use for interaction with cluster.
     * @return {@link Deployment} of operator if {@link Deployment} was found; {@code null} otherwise.
     */
    public static Deployment findOperatorDeployment(OpenShiftClient client, String name) {
        return findOperatorDeployment(client, client.getNamespace(), name);
    }

    /**
     * Waits for existence of {@link Deployment} in provided namespace for provided timeout.
     *
     * @param client {@link OpenShiftClient} instance to use for interaction with cluster.
     * @param namespace Name of namespace of {@link Deployment}.
     * @param name Name of {@link Deployment} to wait for.
     * @param timeUnit {@link TimeUnit} for waiting.
     * @param amount Amount of {@link TimeUnit}s to wait.
     * @return {@code true} if {@link Deployment} exists within provided timeout; {@code false} otherwise.
     */
    public static boolean waitDeploymentExists(OpenShiftClient client, String namespace, String name, TimeUnit timeUnit, long amount) {
        try {
            await().atMost(amount, timeUnit).until(() -> findOperatorDeployment(client, namespace, name) != null);
        } catch (KubernetesClientTimeoutException e) {
            LoggerFactory.getLogger(Utils.class).error(
                    "Deployment '" + name + "' in namespace '" + namespace + "' failed existence check within " + amount + " " + timeUnit.name() + "."
            );

            return false;
        }

        return true;
    }

    /**
     * Waits for existence of {@link Deployment} in client's namespace for pre-defined timeout 5 minutes.
     *
     * @param client {@link OpenShiftClient} instance to use for interaction with cluster.
     * @param name Name of {@link Deployment} to wait for.
     * @return {@code true} if {@link Deployment} exists within pre-defined timeout 5 minutes;
     * {@code false} otherwise.
     */
    public static boolean waitDeploymentExists(OpenShiftClient client, String name) {
        return waitDeploymentExists(client, client.getNamespace(), name, TimeUnit.MINUTES, 5);
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

    /**
     * Waits for readiness of {@link Kafka} in provided namespace for provided timeout.
     *
     * @param client {@link OpenShiftClient} instance to use for interaction with cluster.
     * @param namespace Name of namespace of {@link Kafka}.
     * @param name Name of {@link Kafka} to wait for.
     * @param timeUnit {@link TimeUnit} for waiting.
     * @param amount Amount of {@link TimeUnit}s to wait.
     * @return {@code true} if {@link Kafka} becomes ready within provided timeout; {@code false} otherwise.
     */
    public static boolean waitKafkaReady(OpenShiftClient client, String namespace, String name, TimeUnit timeUnit, long amount) {
        try {
            await().atMost(amount, timeUnit).until(() -> isKafkaReady(client, namespace, name));
        } catch (ConditionTimeoutException e) {
            LoggerFactory.getLogger(Utils.class).error(
                    "Kafka '" + name + "' in namespace '" + namespace + "' failed readiness check within " + amount + " " + timeUnit.name() + "."
            );

            return false;
        }

        return true;
    }

    /**
     * Waits for readiness of {@link Kafka} in client's namespace for pre-defined timeout 5 minutes.
     *
     * @param client {@link OpenShiftClient} instance to use for interaction with cluster.
     * @param name Name of {@link Kafka} to wait for.
     * @return {@code true} if {@link Kafka} becomes ready within pre-defined timeout 5 minutes;
     * {@code false} otherwise.
     */
    public static boolean waitKafkaReady(OpenShiftClient client, String name) {
        return waitKafkaReady(client, client.getNamespace(), name, TimeUnit.MINUTES, 5);
    }

    /**
     * Checks {@link Kafka} readiness in provided namespace.
     *
     * @param client {@link OpenShiftClient} instance to use for interaction with cluster.
     * @param namespace Name of namespace of {@link Kafka} to check.
     * @param name Name of {@link Kafka} to check.
     * @return {@code true} if {@link Kafka} is ready; {@code false} otherwise.
     */
    public static boolean isKafkaReady(OpenShiftClient client, String namespace, String name) {
        return client.resources(Kafka.class).inNamespace(namespace).withName(name).isReady();
    }
}
