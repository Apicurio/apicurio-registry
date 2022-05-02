package io.apicurio.registry.systemtest.framework;

public final class Environment {
    /** Environment variables */
    public static final String APICURIO_BUNDLE_SOURCE_PATH_ENV_VAR = "APICURIO_BUNDLE_SOURCE_PATH";
    public static final String APICURIO_OLM_CATALOG_SOURCE_IMAGE_ENV_VAR = "APICURIO_OLM_CATALOG_SOURCE_IMAGE";
    public static final String APICURIO_OLM_CATALOG_SOURCE_NAME_ENV_VAR = "APICURIO_OLM_CATALOG_SOURCE_NAME";
    public static final String APICURIO_OLM_CATALOG_SOURCE_NAMESPACE_ENV_VAR = "APICURIO_OLM_CATALOG_SOURCE_NAMESPACE";
    public static final String APICURIO_OLM_OPERATOR_GROUP_NAME_ENV_VAR = "APICURIO_OLM_OPERATOR_GROUP_NAME";
    public static final String APICURIO_OLM_SUBSCRIPTION_CHANNEL_ENV_VAR = "APICURIO_OLM_SUBSCRIPTION_CHANNEL";
    public static final String APICURIO_OLM_SUBSCRIPTION_NAME_ENV_VAR = "APICURIO_OLM_SUBSCRIPTION_NAME";
    public static final String APICURIO_OLM_SUBSCRIPTION_PKG_ENV_VAR = "APICURIO_OLM_SUBSCRIPTION_PKG";
    public static final String APICURIO_OLM_SUBSCRIPTION_PLAN_APPROVAL_ENV_VAR = "APICURIO_OLM_SUBSCRIPTION_PLAN_APPROVAL";
    public static final String APICURIO_OLM_SUBSCRIPTION_STARTING_CSV_ENV_VAR = "APICURIO_OLM_SUBSCRIPTION_STARTING_CSV";
    public static final String APICURIO_OPERATOR_NAMESPACE_ENV_VAR = "APICURIO_OPERATOR_NAMESPACE";
    public static final String CONVERTERS_SHA512SUM_ENV_VAR = "CONVERTERS_SHA512SUM";
    public static final String CONVERTERS_URL_ENV_VAR = "CONVERTERS_URL";
    public static final String KEYCLOAK_CATALOG_SOURCE_NAME_ENV_VAR = "KEYCLOAK_CATALOG_SOURCE_NAME";
    public static final String KEYCLOAK_OPERATOR_GROUP_NAME_ENV_VAR = "KEYCLOAK_OPERATOR_GROUP_NAME";
    public static final String KEYCLOAK_SUBSCRIPTION_NAME_ENV_VAR = "KEYCLOAK_SUBSCRIPTION_NAME";
    public static final String KEYCLOAK_SUBSCRIPTION_PKG_ENV_VAR = "KEYCLOAK_SUBSCRIPTION_PKG_NAME";
    public static final String KEYCLOAK_SUBSCRIPTION_PLAN_APPROVAL_ENV_VAR = "KEYCLOAK_SUBSCRIPTION_PLAN_APPROVAL";
    public static final String STRIMZI_OPERATOR_NAMESPACE_ENV_VAR = "STRIMZI_OPERATOR_NAMESPACE";
    public static final String STRIMZI_OPERATOR_SOURCE_PATH_ENV_VAR = "STRIMZI_OPERATOR_SOURCE_PATH";
    public static final String TESTSUITE_DIRECTORY_ENV_VAR = "TESTSUITE_DIRECTORY";

    /** Default values */
    public static final String APICURIO_OLM_CATALOG_SOURCE_NAME_DEFAULT = "apicurio-registry-catalog-source";
    public static final String APICURIO_OLM_CATALOG_SOURCE_NAMESPACE_DEFAULT = "openshift-marketplace";
    public static final String APICURIO_OLM_OPERATOR_GROUP_NAME_DEFAULT = "apicurio-registry-operator-group";
    public static final String APICURIO_OLM_SUBSCRIPTION_NAME_DEFAULT = "apicurio-registry-subscription";
    public static final String APICURIO_OLM_SUBSCRIPTION_PLAN_APPROVAL_DEFAULT = "Automatic";
    public static final String APICURIO_OPERATOR_NAMESPACE_DEFAULT = "apicurio-registry-operator-namespace";
    public static final String KEYCLOAK_CATALOG_SOURCE_NAME_DEFAULT = "community-operators";
    public static final String KEYCLOAK_OPERATOR_GROUP_NAME_DEFAULT = "keycloak-operator-group";
    public static final String KEYCLOAK_SUBSCRIPTION_NAME_DEFAULT = "keycloak-subscription";
    public static final String KEYCLOAK_SUBSCRIPTION_PKG_DEFAULT = "keycloak-operator";
    public static final String KEYCLOAK_SUBSCRIPTION_PLAN_APPROVAL_DEFAULT = "Automatic";
    public static final String STRIMZI_OPERATOR_NAMESPACE_DEFAULT = "strimzi-cluster-operator-namespace";
    public static final String APICURIO_BUNDLE_SOURCE_PATH_DEFAULT = "https://raw.githubusercontent.com/Apicurio/apicurio-registry-operator/master/install/apicurio-registry-operator-1.0.0-v2.0.0.final.yaml";
    public static final String APICURIO_OLM_CLUSTER_WIDE_NAMESPACE = "openshift-operators";
    public static final String APICURIO_OLM_DEPLOYMENT_NAME = "apicurio-registry-operator";
    public static final String KEYCLOAK_HTTP_SERVICE_NAME = "keycloak-http";
    public static final String STRIMZI_OPERATOR_SOURCE_PATH_DEFAULT = "https://strimzi.io/install/latest?namespace=strimzi-cluster-operator-namespace";

    /** Collecting variables */
    public static final String apicurioOLMCatalogSourceImage = System.getenv().get(APICURIO_OLM_CATALOG_SOURCE_IMAGE_ENV_VAR);
    public static final String apicurioOLMCatalogSourceName = System.getenv().getOrDefault(APICURIO_OLM_CATALOG_SOURCE_NAME_ENV_VAR, APICURIO_OLM_CATALOG_SOURCE_NAME_DEFAULT);
    public static final String apicurioOLMCatalogSourceNamespace = System.getenv().getOrDefault(APICURIO_OLM_CATALOG_SOURCE_NAMESPACE_ENV_VAR, APICURIO_OLM_CATALOG_SOURCE_NAMESPACE_DEFAULT);
    public static final String apicurioOLMOperatorGroupName = System.getenv().getOrDefault(APICURIO_OLM_OPERATOR_GROUP_NAME_ENV_VAR, APICURIO_OLM_OPERATOR_GROUP_NAME_DEFAULT);
    public static final String apicurioOLMSubscriptionChannel = System.getenv().get(APICURIO_OLM_SUBSCRIPTION_CHANNEL_ENV_VAR);
    public static final String apicurioOLMSubscriptionName = System.getenv().getOrDefault(APICURIO_OLM_SUBSCRIPTION_NAME_ENV_VAR, APICURIO_OLM_SUBSCRIPTION_NAME_DEFAULT);
    public static final String apicurioOLMSubscriptionPkg = System.getenv().get(APICURIO_OLM_SUBSCRIPTION_PKG_ENV_VAR);
    public static final String apicurioOLMSubscriptionPlanApproval = System.getenv().getOrDefault(APICURIO_OLM_SUBSCRIPTION_PLAN_APPROVAL_ENV_VAR, APICURIO_OLM_SUBSCRIPTION_PLAN_APPROVAL_DEFAULT);
    public static final String apicurioOLMSubscriptionStartingCSV = System.getenv().get(APICURIO_OLM_SUBSCRIPTION_STARTING_CSV_ENV_VAR);
    public static final String apicurioOperatorNamespace = System.getenv().getOrDefault(APICURIO_OPERATOR_NAMESPACE_ENV_VAR, APICURIO_OPERATOR_NAMESPACE_DEFAULT);
    public static final String convertersSha512sum = System.getenv().get(CONVERTERS_SHA512SUM_ENV_VAR);
    public static final String convertersUrl = System.getenv().get(CONVERTERS_URL_ENV_VAR);
    public static final String keycloakCatalogSourceName = System.getenv().getOrDefault(KEYCLOAK_CATALOG_SOURCE_NAME_ENV_VAR, KEYCLOAK_CATALOG_SOURCE_NAME_DEFAULT);
    public static final String keycloakOperatorGroupName =  System.getenv().getOrDefault(KEYCLOAK_OPERATOR_GROUP_NAME_ENV_VAR, KEYCLOAK_OPERATOR_GROUP_NAME_DEFAULT);
    public static final String keycloakSubscriptionName = System.getenv().getOrDefault(KEYCLOAK_SUBSCRIPTION_NAME_ENV_VAR, KEYCLOAK_SUBSCRIPTION_NAME_DEFAULT);
    public static final String keycloakSubscriptionPkg = System.getenv().getOrDefault(KEYCLOAK_SUBSCRIPTION_PKG_ENV_VAR, KEYCLOAK_SUBSCRIPTION_PKG_DEFAULT);
    public static final String keycloakSubscriptionPlanApproval = System.getenv().getOrDefault(KEYCLOAK_SUBSCRIPTION_PLAN_APPROVAL_ENV_VAR, KEYCLOAK_SUBSCRIPTION_PLAN_APPROVAL_DEFAULT);
    public static final String strimziOperatorNamespace = System.getenv().getOrDefault(STRIMZI_OPERATOR_NAMESPACE_ENV_VAR, STRIMZI_OPERATOR_NAMESPACE_DEFAULT);

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
