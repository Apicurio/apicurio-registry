package io.apicurio.registry.systemtest.framework;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class Environment {
    /** Environment variables */
    public static final String APICURIO_BUNDLE_SOURCE_PATH_ENV_VAR = "APICURIO_BUNDLE_SOURCE_PATH";
    public static final String APICURIO_OLM_CATALOG_SOURCE_IMAGE_ENV_VAR = "APICURIO_OLM_CATALOG_SOURCE_IMAGE";
    public static final String APICURIO_OLM_CATALOG_SOURCE_NAME_ENV_VAR = "APICURIO_OLM_CATALOG_SOURCE_NAME";
    public static final String APICURIO_OLM_CATALOG_SOURCE_NAMESPACE_ENV_VAR = "APICURIO_OLM_CATALOG_SOURCE_NAMESPACE";
    public static final String APICURIO_OLM_SUBSCRIPTION_CHANNEL_ENV_VAR = "APICURIO_OLM_SUBSCRIPTION_CHANNEL";
    public static final String APICURIO_OLM_SUBSCRIPTION_NAME_ENV_VAR = "APICURIO_OLM_SUBSCRIPTION_NAME";
    public static final String APICURIO_OLM_SUBSCRIPTION_PKG_ENV_VAR = "APICURIO_OLM_SUBSCRIPTION_PKG";
    public static final String APICURIO_OLM_SUBSCRIPTION_STARTING_CSV_ENV_VAR =
            "APICURIO_OLM_SUBSCRIPTION_STARTING_CSV";
    public static final String APICURIO_OPERATOR_NAMESPACE_ENV_VAR = "APICURIO_OPERATOR_NAMESPACE";
    public static final String CONVERTERS_SHA512SUM_ENV_VAR = "CONVERTERS_SHA512SUM";
    public static final String CONVERTERS_URL_ENV_VAR = "CONVERTERS_URL";
    public static final String KEYCLOAK_CATALOG_SOURCE_NAME_ENV_VAR = "KEYCLOAK_CATALOG_SOURCE_NAME";
    public static final String KEYCLOAK_SUBSCRIPTION_NAME_ENV_VAR = "KEYCLOAK_SUBSCRIPTION_NAME";
    public static final String KEYCLOAK_SUBSCRIPTION_PKG_ENV_VAR = "KEYCLOAK_SUBSCRIPTION_PKG_NAME";
    public static final String STRIMZI_OLM_CATALOG_SOURCE_NAME_ENV_VAR = "STRIMZI_OLM_CATALOG_SOURCE_NAME";
    public static final String STRIMZI_OLM_CATALOG_SOURCE_NAMESPACE_ENV_VAR = "STRIMZI_OLM_CATALOG_SOURCE_NAMESPACE";
    public static final String STRIMZI_OLM_DEPLOYMENT_NAME_ENV_VAR = "STRIMZI_OLM_DEPLOYMENT_NAME";
    public static final String STRIMZI_OLM_SUBSCRIPTION_NAME_ENV_VAR = "STRIMZI_OLM_SUBSCRIPTION_NAME";
    public static final String STRIMZI_OLM_SUBSCRIPTION_PKG_ENV_VAR = "STRIMZI_OLM_SUBSCRIPTION_PKG";
    public static final String STRIMZI_OPERATOR_NAMESPACE_ENV_VAR = "STRIMZI_OPERATOR_NAMESPACE";
    public static final String STRIMZI_OPERATOR_SOURCE_PATH_ENV_VAR = "STRIMZI_OPERATOR_SOURCE_PATH";
    public static final String TEMP_PATH_ENV_VAR = "TEMP_PATH";
    public static final String TESTSUITE_DIRECTORY_ENV_VAR = "TESTSUITE_DIRECTORY";

    /** Default values */
    public static final String APICURIO_BUNDLE_SOURCE_PATH_DEFAULT =
            "https://raw.githubusercontent.com/Apicurio/apicurio-registry-operator/master/install/"
            + "apicurio-registry-operator-1.0.0-v2.0.0.final.yaml";
    public static final String APICURIO_OLM_CATALOG_SOURCE_IMAGE_DEFAULT = null;
    public static final String APICURIO_OLM_CATALOG_SOURCE_NAME_DEFAULT = "apicurio-registry-catalog-source";
    public static final String APICURIO_OLM_CATALOG_SOURCE_NAMESPACE_DEFAULT = "openshift-marketplace";
    public static final String APICURIO_OLM_DEPLOYMENT_NAME = "apicurio-registry-operator";
    public static final String APICURIO_OLM_SUBSCRIPTION_CHANNEL_DEFAULT = "2.x";
    public static final String APICURIO_OLM_SUBSCRIPTION_NAME_DEFAULT = "apicurio-registry-subscription";
    public static final String APICURIO_OLM_SUBSCRIPTION_PKG_DEFAULT = "apicurio-registry";
    public static final String APICURIO_OLM_SUBSCRIPTION_STARTING_CSV_DEFAULT =
            "apicurio-registry-operator.v1.0.0-v2.0.0.final";
    public static final String APICURIO_OPERATOR_NAMESPACE_DEFAULT = "apicurio-registry-operator-namespace";
    public static final String KEYCLOAK_CATALOG_SOURCE_NAME_DEFAULT = "community-operators";
    public static final String KEYCLOAK_HTTP_SERVICE_NAME = "keycloak-http";
    public static final String KEYCLOAK_SUBSCRIPTION_NAME_DEFAULT = "keycloak-subscription";
    public static final String KEYCLOAK_SUBSCRIPTION_PKG_DEFAULT = "keycloak-operator";
    public static final String OLM_CLUSTER_WIDE_NAMESPACE = "openshift-operators";
    public static final String STRIMZI_OLM_CATALOG_SOURCE_NAME_DEFAULT = "community-operators";
    public static final String STRIMZI_OLM_CATALOG_SOURCE_NAMESPACE_DEFAULT = "openshift-marketplace";
    public static final String STRIMZI_OLM_DEPLOYMENT_NAME_DEFAULT = "strimzi-cluster-operator";
    public static final String STRIMZI_OLM_SUBSCRIPTION_NAME_DEFAULT = "strimzi-subscription";
    public static final String STRIMZI_OLM_SUBSCRIPTION_PKG_DEFAULT = "strimzi-kafka-operator";
    public static final String STRIMZI_OPERATOR_NAMESPACE_DEFAULT = "strimzi-cluster-operator-namespace";
    public static final String STRIMZI_OPERATOR_SOURCE_PATH_DEFAULT =
            "https://strimzi.io/install/latest?namespace=strimzi-cluster-operator-namespace";
    public static final String TEMP_PATH_DEFAULT = "/tmp";

    /** Collecting variables */
    public static final String APICURIO_BUNDLE_SOURCE_PATH = System.getenv().getOrDefault(
            APICURIO_BUNDLE_SOURCE_PATH_ENV_VAR,
            APICURIO_BUNDLE_SOURCE_PATH_DEFAULT
    );
    public static final String APICURIO_OLM_CATALOG_SOURCE_IMAGE = System.getenv().getOrDefault(
            APICURIO_OLM_CATALOG_SOURCE_IMAGE_ENV_VAR,
            APICURIO_OLM_CATALOG_SOURCE_IMAGE_DEFAULT
    );
    public static final String APICURIO_OLM_CATALOG_SOURCE_NAME = System.getenv().getOrDefault(
            APICURIO_OLM_CATALOG_SOURCE_NAME_ENV_VAR,
            APICURIO_OLM_CATALOG_SOURCE_NAME_DEFAULT
    );
    public static final String APICURIO_OLM_CATALOG_SOURCE_NAMESPACE = System.getenv().getOrDefault(
            APICURIO_OLM_CATALOG_SOURCE_NAMESPACE_ENV_VAR,
            APICURIO_OLM_CATALOG_SOURCE_NAMESPACE_DEFAULT
    );
    public static final String APICURIO_OLM_SUBSCRIPTION_CHANNEL = System.getenv().getOrDefault(
            APICURIO_OLM_SUBSCRIPTION_CHANNEL_ENV_VAR,
            APICURIO_OLM_SUBSCRIPTION_CHANNEL_DEFAULT
    );
    public static final String APICURIO_OLM_SUBSCRIPTION_NAME = System.getenv().getOrDefault(
            APICURIO_OLM_SUBSCRIPTION_NAME_ENV_VAR,
            APICURIO_OLM_SUBSCRIPTION_NAME_DEFAULT
    );
    public static final String APICURIO_OLM_SUBSCRIPTION_PKG = System.getenv().getOrDefault(
            APICURIO_OLM_SUBSCRIPTION_PKG_ENV_VAR,
            APICURIO_OLM_SUBSCRIPTION_PKG_DEFAULT
    );
    public static final String APICURIO_OLM_SUBSCRIPTION_STARTING_CSV = System.getenv().getOrDefault(
            APICURIO_OLM_SUBSCRIPTION_STARTING_CSV_ENV_VAR,
            APICURIO_OLM_SUBSCRIPTION_STARTING_CSV_DEFAULT
    );
    public static final String APICURIO_OPERATOR_NAMESPACE = System.getenv().getOrDefault(
            APICURIO_OPERATOR_NAMESPACE_ENV_VAR,
            APICURIO_OPERATOR_NAMESPACE_DEFAULT
    );
    public static final String CONVERTERS_SHA_512_SUM = System.getenv().get(CONVERTERS_SHA512SUM_ENV_VAR);
    public static final String CONVERTERS_URL = System.getenv().get(CONVERTERS_URL_ENV_VAR);
    public static final String KEYCLOAK_CATALOG_SOURCE_NAME = System.getenv().getOrDefault(
            KEYCLOAK_CATALOG_SOURCE_NAME_ENV_VAR,
            KEYCLOAK_CATALOG_SOURCE_NAME_DEFAULT
    );
    public static final String KEYCLOAK_SUBSCRIPTION_NAME = System.getenv().getOrDefault(
            KEYCLOAK_SUBSCRIPTION_NAME_ENV_VAR,
            KEYCLOAK_SUBSCRIPTION_NAME_DEFAULT
    );
    public static final String KEYCLOAK_SUBSCRIPTION_PKG = System.getenv().getOrDefault(
            KEYCLOAK_SUBSCRIPTION_PKG_ENV_VAR,
            KEYCLOAK_SUBSCRIPTION_PKG_DEFAULT
    );
    public static final String STRIMZI_OLM_CATALOG_SOURCE_NAME = System.getenv().getOrDefault(
            STRIMZI_OLM_CATALOG_SOURCE_NAME_ENV_VAR,
            STRIMZI_OLM_CATALOG_SOURCE_NAME_DEFAULT
    );
    public static final String STRIMZI_OLM_CATALOG_SOURCE_NAMESPACE = System.getenv().getOrDefault(
            STRIMZI_OLM_CATALOG_SOURCE_NAMESPACE_ENV_VAR,
            STRIMZI_OLM_CATALOG_SOURCE_NAMESPACE_DEFAULT
    );
    public static final String STRIMZI_OLM_DEPLOYMENT_NAME = System.getenv().getOrDefault(
            STRIMZI_OLM_DEPLOYMENT_NAME_ENV_VAR,
            STRIMZI_OLM_DEPLOYMENT_NAME_DEFAULT
    );
    public static final String STRIMZI_OLM_SUBSCRIPTION_NAME = System.getenv().getOrDefault(
            STRIMZI_OLM_SUBSCRIPTION_NAME_ENV_VAR,
            STRIMZI_OLM_SUBSCRIPTION_NAME_DEFAULT
    );
    public static final String STRIMZI_OLM_SUBSCRIPTION_PKG = System.getenv().getOrDefault(
            STRIMZI_OLM_SUBSCRIPTION_PKG_ENV_VAR,
            STRIMZI_OLM_SUBSCRIPTION_PKG_DEFAULT
    );
    public static final String STRIMZI_NAMESPACE = System.getenv().getOrDefault(
            STRIMZI_OPERATOR_NAMESPACE_ENV_VAR,
            STRIMZI_OPERATOR_NAMESPACE_DEFAULT
    );
    public static final String TEMP_PATH = System.getenv().getOrDefault(
            TEMP_PATH_ENV_VAR,
            TEMP_PATH_DEFAULT
    );
    public static final String TESTSUITE_DIRECTORY = System.getenv().get(TESTSUITE_DIRECTORY_ENV_VAR);

    public static Path getTempPath(String filename) {
        return Paths.get(Environment.TEMP_PATH, filename);
    }
    // TODO: Move other constants here too.
    // PostgreSQL port
    // PostgreSQL username
    // PostgreSQL password
    // PostgreSQL admin password
    // PostgreSQL database name
    // PostgreSQL image
    // PostgreSQL volume size
    // Default KafkaSQL values
    // Path for temporary files
    // Catalog source display name
    // Catalog source publisher
    // Catalog source source type
    // Catalog source pod label
}
