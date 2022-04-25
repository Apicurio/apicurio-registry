package io.apicurio.registry.systemtest.framework;

public final class Constants {
    public static final String APICURIO_REGISTRY_BUNDLE_OPERATOR_SOURCE_PATH_ENV_VARIABLE = "APICURIO_REGISTRY_BUNDLE_OPERATOR_SOURCE_PATH";
    public static final String APICURIO_REGISTRY_BUNDLE_OPERATOR_SOURCE_PATH_DEFAULT_VALUE = "https://raw.githubusercontent.com/Apicurio/apicurio-registry-operator/master/install/apicurio-registry-operator-1.0.0-v2.0.0.final.yaml";
    public static final String APICURIO_REGISTRY_OPERATOR_NAMESPACE_ENV_VARIABLE = "APICURIO_REGISTRY_OPERATOR_NAMESPACE";
    public static final String APICURIO_REGISTRY_OPERATOR_NAMESPACE_DEFAULT_VALUE = "apicurio-registry-operator-namespace";
    public static final String APICURIO_REGISTRY_OLM_OPERATOR_DEPLOYMENT_NAME = "apicurio-registry-operator";
    public static final String KEYCLOAK_OLM_OPERATOR_DEPLOYMENT_NAME = "keycloak-operator";

    public static final String APICURIO_REGISTRY_OLM_OPERATOR_CLUSTER_WIDE_NAMESPACE = "openshift-operators";
    public static final String APICURIO_REGISTRY_OLM_OPERATOR_OPERATOR_GROUP_NAME_ENV_VARIABLE = "APICURIO_REGISTRY_OLM_OPERATOR_OPERATOR_GROUP_NAME";
    public static final String APICURIO_REGISTRY_OLM_OPERATOR_OPERATOR_GROUP_NAME_DEFAULT_VALUE = "apicurio-registry-operator-group";
    public static final String APICURIO_REGISTRY_OLM_OPERATOR_CATALOG_SOURCE_IMAGE_ENV_VARIABLE = "APICURIO_REGISTRY_OLM_OPERATOR_CATALOG_SOURCE_IMAGE";
    public static final String APICURIO_REGISTRY_OLM_OPERATOR_CATALOG_SOURCE_NAMESPACE_ENV_VARIABLE = "APICURIO_REGISTRY_OLM_OPERATOR_CATALOG_SOURCE_NAMESPACE";
    public static final String APICURIO_REGISTRY_OLM_OPERATOR_CATALOG_SOURCE_NAMESPACE_DEFAULT_VALUE = "openshift-marketplace";
    public static final String APICURIO_REGISTRY_OLM_OPERATOR_CATALOG_SOURCE_NAME_ENV_VARIABLE = "APICURIO_REGISTRY_OLM_OPERATOR_CATALOG_SOURCE_NAME";
    public static final String APICURIO_REGISTRY_OLM_OPERATOR_CATALOG_SOURCE_NAME_DEFAULT_VALUE = "apicurio-registry-catalog-source";
    public static final String APICURIO_REGISTRY_OLM_OPERATOR_SUBSCRIPTION_INSTALL_PLAN_APPROVAL_ENV_VARIABLE = "APICURIO_REGISTRY_OLM_OPERATOR_SUBSCRIPTION_INSTALL_PLAN_APPROVAL";
    public static final String APICURIO_REGISTRY_OLM_OPERATOR_SUBSCRIPTION_INSTALL_PLAN_APPROVAL_DEFAULT_VALUE = "Automatic";
    public static final String APICURIO_REGISTRY_OLM_OPERATOR_SUBSCRIPTION_CHANNEL_ENV_VARIABLE = "APICURIO_REGISTRY_OLM_OPERATOR_SUBSCRIPTION_CHANNEL";
    public static final String APICURIO_REGISTRY_OLM_OPERATOR_SUBSCRIPTION_STARTING_CSV_ENV_VARIABLE = "APICURIO_REGISTRY_OLM_OPERATOR_SUBSCRIPTION_STARTING_CSV";
    public static final String APICURIO_REGISTRY_OLM_OPERATOR_SUBSCRIPTION_PACKAGE_ENV_VARIABLE = "APICURIO_REGISTRY_OLM_OPERATOR_SUBSCRIPTION_PACKAGE";
    public static final String APICURIO_REGISTRY_OLM_OPERATOR_SUBSCRIPTION_NAME_ENV_VARIABLE = "APICURIO_REGISTRY_OLM_OPERATOR_SUBSCRIPTION_NAME";
    public static final String APICURIO_REGISTRY_OLM_OPERATOR_SUBSCRIPTION_NAME_DEFAULT_VALUE = "apicurio-registry-subscription";


    public static final String STRIMZI_CLUSTER_OPERATOR_SOURCE_PATH_ENV_VARIABLE = "STRIMZI_CLUSTER_OPERATOR_SOURCE_PATH";
    public static final String STRIMZI_CLUSTER_OPERATOR_SOURCE_PATH_DEFAULT_VALUE = "https://strimzi.io/install/latest?namespace=strimzi-cluster-operator-namespace";
    public static final String STRIMZI_CLUSTER_OPERATOR_NAMESPACE_ENV_VARIABLE = "STRIMZI_CLUSTER_OPERATOR_NAMESPACE";
    public static final String STRIMZI_CLUSTER_OPERATOR_NAMESPACE_DEFAULT_VALUE = "strimzi-cluster-operator-namespace";

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
