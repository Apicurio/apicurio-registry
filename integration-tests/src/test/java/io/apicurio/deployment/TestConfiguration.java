package io.apicurio.deployment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

import static io.apicurio.deployment.TestConfiguration.Constants.*;
import static java.lang.Boolean.parseBoolean;
import static java.lang.System.getProperty;

public class TestConfiguration {

    public interface Constants {

        String CLUSTER_TESTS = "cluster.tests";
        String PRESERVE_NAMESPACE = "preserveNamespace";
        String REGISTRY_KAFKASQL_IMAGE = "registry-kafkasql-image";
    }

    private static Logger log = LoggerFactory.getLogger(TestConfiguration.class);

    private static List<String> keys;

    static {
        keys = List.of(
                CLUSTER_TESTS,
                PRESERVE_NAMESPACE,
                REGISTRY_KAFKASQL_IMAGE
        );
    }


    public static void print() {
        log.info("#".repeat(20));
        log.info("Configuration properties:");
        for (String key : keys) {
            var value = getProperty(key);
            if (value != null) {
                log.info("{} = '{}'", key, value);
            }
        }
        log.info("#".repeat(20));
    }


    public static String getConfigString(String key) {
        return getProperty(key);
    }


    public static boolean isClusterTests() {
        return parseBoolean(getConfigString(CLUSTER_TESTS));
    }


    public static boolean isPreserveNamespace() {
        return parseBoolean(getConfigString(PRESERVE_NAMESPACE));
    }

    // === Timeout Configuration Methods

    /**
     * Gets the health check timeout with cluster-specific multiplier
     */
    public static Duration getHealthCheckTimeout() {
        var multiplier = isClusterTests() ? 3 : 1;
        return Duration.ofSeconds(60 * multiplier);
    }

    /**
     * Gets the startup timeout with cluster-specific multiplier
     */
    public static Duration getStartupTimeout() {
        var multiplier = isClusterTests() ? 5 : 1;
        return Duration.ofSeconds(120 * multiplier);
    }

    /**
     * Gets the network connectivity timeout with cluster-specific multiplier
     */
    public static Duration getNetworkTimeout() {
        var multiplier = isClusterTests() ? 3 : 1;
        return Duration.ofSeconds(30 * multiplier);
    }

    /**
     * Gets the Kafka connectivity timeout with cluster-specific multiplier
     */
    public static Duration getKafkaTimeout() {
        var multiplier = isClusterTests() ? 6 : 2;
        return Duration.ofSeconds(60 * multiplier);
    }

    /**
     * Gets the Registry API timeout with cluster-specific multiplier
     */
    public static Duration getRegistryTimeout() {
        var multiplier = isClusterTests() ? 4 : 2;
        return Duration.ofSeconds(90 * multiplier);
    }

    /**
     * Gets the Kubernetes resource readiness timeout
     */
    public static Duration getKubernetesTimeout() {
        return Duration.ofSeconds(isClusterTests() ? 600 : 300);
    }

    /**
     * Gets the test execution timeout with cluster-specific multiplier
     */
    public static Duration getTestTimeout() {
        var multiplier = isClusterTests() ? 10 : 3;
        return Duration.ofSeconds(180 * multiplier);
    }

    /**
     * Gets the poll interval for health checks with cluster-specific adjustment
     */
    public static Duration getPollInterval() {
        return Duration.ofSeconds(isClusterTests() ? 5 : 2);
    }


    private TestConfiguration() {
    }
}
