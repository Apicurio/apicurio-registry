package io.apicurio.registry.systemtest.operator.types;

public interface OperatorKind {
    String APICURIO_REGISTRY_BUNDLE_OPERATOR = "ApicurioRegistryBundleOperator";
    String APICURIO_REGISTRY_OLM_OPERATOR = "ApicurioRegistryOLMOperator";
    String STRIMZI_CLUSTER_BUNDLE_OPERATOR = "StrimziClusterBundleOperator";
    String KEYCLOAK_OLM_OPERATOR = "KeycloakOLMOperator";
}
