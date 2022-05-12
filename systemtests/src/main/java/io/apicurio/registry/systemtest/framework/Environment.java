package io.apicurio.registry.systemtest.framework;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class Environment {
    /** Environment variables */
    public static final String CATALOG_NAME_ENV_VAR = "CATALOG_NAME";
    public static final String CONVERTERS_SHA512SUM_ENV_VAR = "CONVERTERS_SHA512SUM";
    public static final String CONVERTERS_URL_ENV_VAR = "CONVERTERS_URL";
    public static final String KAFKA_BUNDLE_ENV_VAR = "KAFKA_BUNDLE";
    public static final String KAFKA_DEPLOYMENT_ENV_VAR = "KAFKA_DEPLOYMENT";
    public static final String KAFKA_PACKAGE_ENV_VAR = "KAFKA_PACKAGE";
    public static final String REGISTRY_BUNDLE_ENV_VAR = "REGISTRY_BUNDLE";
    public static final String REGISTRY_CATALOG_IMAGE_ENV_VAR = "REGISTRY_CATALOG_IMAGE";
    public static final String REGISTRY_CHANNEL_ENV_VAR = "REGISTRY_CHANNEL";
    public static final String REGISTRY_PACKAGE_ENV_VAR = "REGISTRY_PACKAGE";
    public static final String REGISTRY_STARTING_CSV_ENV_VAR = "REGISTRY_STARTING_CSV";
    public static final String SSO_PACKAGE_ENV_VAR = "SSO_PACKAGE";
    public static final String TESTSUITE_PATH_ENV_VAR = "TESTSUITE_PATH";
    public static final String TMP_PATH_ENV_VAR = "TMP_PATH";

    /** Default values of environment variables */
    public static final String CATALOG_NAME_DEFAULT = "community-operators";
    public static final String KAFKA_BUNDLE_DEFAULT =
            "https://strimzi.io/install/latest?namespace=" + Constants.TESTSUITE_NAMESPACE;
    public static final String KAFKA_DEPLOYMENT_DEFAULT = "strimzi-cluster-operator"; // Default from catalog
    public static final String KAFKA_PACKAGE_DEFAULT = "strimzi-kafka-operator"; // Default from catalog
    public static final String REGISTRY_BUNDLE_DEFAULT =
            "https://raw.githubusercontent.com/Apicurio/apicurio-registry-operator/master/install/"
            + "apicurio-registry-operator-1.0.0-v2.0.0.final.yaml";
    public static final String REGISTRY_CATALOG_IMAGE_DEFAULT = null;
    public static final String REGISTRY_PACKAGE_DEFAULT = "apicurio-registry"; // Default from catalog
    public static final String SSO_PACKAGE_DEFAULT = "keycloak-operator"; // Default from catalog
    public static final String TMP_PATH_DEFAULT = "/tmp";

    /** Collecting environment variables */
    public static final String CATALOG_NAME = getOrDefault(CATALOG_NAME_ENV_VAR, CATALOG_NAME_DEFAULT);
    public static final String CONVERTERS_SHA512SUM = get(CONVERTERS_SHA512SUM_ENV_VAR);
    public static final String CONVERTERS_URL = get(CONVERTERS_URL_ENV_VAR);
    public static final String KAFKA_BUNDLE = getOrDefault(KAFKA_BUNDLE_ENV_VAR, KAFKA_BUNDLE_DEFAULT);
    public static final String KAFKA_DEPLOYMENT = getOrDefault(KAFKA_DEPLOYMENT_ENV_VAR, KAFKA_DEPLOYMENT_DEFAULT);
    public static final String KAFKA_PACKAGE = getOrDefault(KAFKA_PACKAGE_ENV_VAR, KAFKA_PACKAGE_DEFAULT);
    public static final String REGISTRY_BUNDLE = getOrDefault(REGISTRY_BUNDLE_ENV_VAR, REGISTRY_BUNDLE_DEFAULT);
    public static final String REGISTRY_CATALOG_IMAGE = getOrDefault(
            REGISTRY_CATALOG_IMAGE_ENV_VAR,
            REGISTRY_CATALOG_IMAGE_DEFAULT
    );
    public static final String REGISTRY_CHANNEL = get(REGISTRY_CHANNEL_ENV_VAR);
    public static final String REGISTRY_PACKAGE = getOrDefault(REGISTRY_PACKAGE_ENV_VAR, REGISTRY_PACKAGE_DEFAULT);
    public static final String REGISTRY_STARTING_CSV = get(REGISTRY_STARTING_CSV_ENV_VAR);
    public static final String SSO_PACKAGE = getOrDefault(SSO_PACKAGE_ENV_VAR, SSO_PACKAGE_DEFAULT);
    public static final String TESTSUITE_PATH = get(TESTSUITE_PATH_ENV_VAR);
    public static final String TMP_PATH = getOrDefault(TMP_PATH_ENV_VAR, TMP_PATH_DEFAULT);

    private static String get(String key) {
        return System.getenv().get(key);
    }

    private static String getOrDefault(String key, String defaultValue) {
        return System.getenv().getOrDefault(key, defaultValue);
    }

    public static Path getTempPath(String filename) {
        return Paths.get(TMP_PATH, filename);
    }
}
