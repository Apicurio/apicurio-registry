package io.apicurio.registry.systemtest.framework;

public final class Constants {
    /** Apicurio Registry */
    public static final String APICURIO_BUNDLE_SOURCE_PATH_DEFAULT = "https://raw.githubusercontent.com/Apicurio/apicurio-registry-operator/master/install/apicurio-registry-operator-1.0.0-v2.0.0.final.yaml";
    public static final String APICURIO_BUNDLE_SOURCE_PATH_ENV_VAR = "APICURIO_BUNDLE_SOURCE_PATH";
    public static final String APICURIO_OLM_CATALOG_SOURCE_IMAGE_ENV_VAR = "APICURIO_OLM_CATALOG_SOURCE_IMAGE";
    public static final String APICURIO_OLM_CATALOG_SOURCE_NAMESPACE_DEFAULT = "openshift-marketplace";
    public static final String APICURIO_OLM_CATALOG_SOURCE_NAMESPACE_ENV_VAR = "APICURIO_OLM_CATALOG_SOURCE_NAMESPACE";
    public static final String APICURIO_OLM_CATALOG_SOURCE_NAME_DEFAULT = "apicurio-registry-catalog-source";
    public static final String APICURIO_OLM_CATALOG_SOURCE_NAME_ENV_VAR = "APICURIO_OLM_CATALOG_SOURCE_NAME";
    public static final String APICURIO_OLM_CLUSTER_WIDE_NAMESPACE = "openshift-operators";
    public static final String APICURIO_OLM_DEPLOYMENT_NAME = "apicurio-registry-operator";
    public static final String APICURIO_OLM_OPERATOR_GROUP_NAME_DEFAULT = "apicurio-registry-operator-group";
    public static final String APICURIO_OLM_OPERATOR_GROUP_NAME_ENV_VAR = "APICURIO_OLM_OPERATOR_GROUP_NAME";
    public static final String APICURIO_OLM_SUBSCRIPTION_CHANNEL_ENV_VAR = "APICURIO_OLM_SUBSCRIPTION_CHANNEL";
    public static final String APICURIO_OLM_SUBSCRIPTION_NAME_DEFAULT = "apicurio-registry-subscription";
    public static final String APICURIO_OLM_SUBSCRIPTION_NAME_ENV_VAR = "APICURIO_OLM_SUBSCRIPTION_NAME";
    public static final String APICURIO_OLM_SUBSCRIPTION_PKG_ENV_VAR = "APICURIO_OLM_SUBSCRIPTION_PKG";
    public static final String APICURIO_OLM_SUBSCRIPTION_PLAN_APPROVAL_DEFAULT = "Automatic";
    public static final String APICURIO_OLM_SUBSCRIPTION_PLAN_APPROVAL_ENV_VAR = "APICURIO_OLM_SUBSCRIPTION_PLAN_APPROVAL";
    public static final String APICURIO_OLM_SUBSCRIPTION_STARTING_CSV_ENV_VAR = "APICURIO_OLM_SUBSCRIPTION_STARTING_CSV";
    public static final String APICURIO_OPERATOR_NAMESPACE_DEFAULT = "apicurio-registry-operator-namespace";
    public static final String APICURIO_OPERATOR_NAMESPACE_ENV_VAR = "APICURIO_OPERATOR_NAMESPACE";

    /** Converters */
    public static final String CONVERTERS_SHA512SUM_ENV_VAR = "CONVERTERS_SHA512SUM";
    public static final String CONVERTERS_URL_ENV_VAR = "CONVERTERS_URL";

    /** Keycloak */
    public static final String KEYCLOAK_CATALOG_SOURCE_NAME_DEFAULT = "community-operators";
    public static final String KEYCLOAK_CATALOG_SOURCE_NAME_ENV_VAR = "KEYCLOAK_CATALOG_SOURCE_NAME";
    public static final String KEYCLOAK_HTTP_SERVICE_NAME = "keycloak-http";
    public static final String KEYCLOAK_OPERATOR_GROUP_NAME_DEFAULT = "keycloak-operator-group";
    public static final String KEYCLOAK_OPERATOR_GROUP_NAME_ENV_VAR = "KEYCLOAK_OPERATOR_GROUP_NAME";
    public static final String KEYCLOAK_SUBSCRIPTION_NAME_DEFAULT = "keycloak-subscription";
    public static final String KEYCLOAK_SUBSCRIPTION_NAME_ENV_VAR = "KEYCLOAK_SUBSCRIPTION_NAME";
    public static final String KEYCLOAK_SUBSCRIPTION_PKG_DEFAULT = "keycloak-operator";
    public static final String KEYCLOAK_SUBSCRIPTION_PKG_ENV_VAR = "KEYCLOAK_SUBSCRIPTION_PKG_NAME";
    public static final String KEYCLOAK_SUBSCRIPTION_PLAN_APPROVAL_DEFAULT = "Automatic";
    public static final String KEYCLOAK_SUBSCRIPTION_PLAN_APPROVAL_ENV_VAR = "KEYCLOAK_SUBSCRIPTION_PLAN_APPROVAL";

    /** Strimzi */
    public static final String STRIMZI_OPERATOR_NAMESPACE_DEFAULT = "strimzi-cluster-operator-namespace";
    public static final String STRIMZI_OPERATOR_NAMESPACE_ENV_VAR = "STRIMZI_OPERATOR_NAMESPACE";
    public static final String STRIMZI_OPERATOR_SOURCE_PATH_DEFAULT = "https://strimzi.io/install/latest?namespace=strimzi-cluster-operator-namespace";
    public static final String STRIMZI_OPERATOR_SOURCE_PATH_ENV_VAR = "STRIMZI_OPERATOR_SOURCE_PATH";

    /** Test suite */
    public static final String TESTSUITE_DIRECTORY_ENV_VAR = "TESTSUITE_DIRECTORY";

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
