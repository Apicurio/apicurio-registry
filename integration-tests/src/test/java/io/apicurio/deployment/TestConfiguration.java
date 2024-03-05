package io.apicurio.deployment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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


    private TestConfiguration() {
    }
}
