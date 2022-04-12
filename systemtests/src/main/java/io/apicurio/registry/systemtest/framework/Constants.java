package io.apicurio.registry.systemtest.framework;

public final class Constants {
    public static final String APICURIO_REGISTRY_OPERATOR_SOURCE_PATH_ENV_VARIABLE = "APICURIO_REGISTRY_OPERATOR_SOURCE_PATH";
    public static final String APICURIO_REGISTRY_OPERATOR_SOURCE_PATH_DEFAULT_VALUE = "/tmp/install.yaml";
    public static final String APICURIO_REGISTRY_OPERATOR_NAMESPACE_ENV_VARIABLE = "APICURIO_REGISTRY_OPERATOR_NAMESPACE";
    public static final String APICURIO_REGISTRY_OPERATOR_NAMESPACE_DEFAULT_VALUE = "apicurio-registry-test-namespace";

    // TODO: Move waiting timeout and waiting check interval here, and other constants too.
}
