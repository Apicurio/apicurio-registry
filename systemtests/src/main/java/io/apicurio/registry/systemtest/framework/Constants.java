package io.apicurio.registry.systemtest.framework;

public final class Constants {
    public static final String APICURIO_REGISTRY_OPERATOR_SOURCE_PATH_ENV_VARIABLE = "APICURIO_REGISTRY_OPERATOR_SOURCE_PATH";
    public static final String APICURIO_REGISTRY_OPERATOR_SOURCE_PATH_DEFAULT_VALUE = "https://raw.githubusercontent.com/Apicurio/apicurio-registry-operator/master/install/apicurio-registry-operator-1.0.0-v2.0.0.final.yaml";
    public static final String APICURIO_REGISTRY_OPERATOR_NAMESPACE_ENV_VARIABLE = "APICURIO_REGISTRY_OPERATOR_NAMESPACE";
    public static final String APICURIO_REGISTRY_OPERATOR_NAMESPACE_DEFAULT_VALUE = "apicurio-registry-operator-namespace";

    public static final String STRIMZI_CLUSTER_OPERATOR_SOURCE_PATH_ENV_VARIABLE = "STRIMZI_CLUSTER_OPERATOR_SOURCE_PATH";
    public static final String STRIMZI_CLUSTER_OPERATOR_SOURCE_PATH_DEFAULT_VALUE = "https://strimzi.io/install/latest?namespace=strimzi-cluster-operator-namespace";
    public static final String STRIMZI_CLUSTER_OPERATOR_NAMESPACE_ENV_VARIABLE = "STRIMZI_CLUSTER_OPERATOR_NAMESPACE";
    public static final String STRIMZI_CLUSTER_OPERATOR_NAMESPACE_DEFAULT_VALUE = "strimzi-cluster-operator-namespace";

    // TODO: Move waiting timeout and waiting check interval here, and other constants too.
    // PostgreSQL port
    // PostgreSQL username
    // PostgreSQL password
    // PostgreSQL admin password
    // PostgreSQL database name
}
