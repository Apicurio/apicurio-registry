package io.apicurio.registry.systemtests.framework;


public class Constants {
    public static final String CATALOG_NAME = "testsuite-operators";
    public static final String CATALOG_NAMESPACE = "openshift-marketplace";
    public static final String KAFKA_CONNECT = "kafka-connect-for-registry";
    public static final String KAFKA = "kafka-for-registry";
    public static final String KAFKA_USER = "kafka-user-for-registry";
    public static final String REGISTRY_OPERATOR_DEPLOYMENT = "apicurio-registry-operator"; // Default from catalog
    public static final String REGISTRY = "registry";
    public static final String SSO_CLIENT_API = "registry-client-api"; // Defined in kubefiles/keycloak
    public static final String SSO_CLIENT_UI = "registry-client-ui"; // Defined in kubefiles/keycloak
    public static final String SSO_HTTP_SERVICE = "sso-http";
    public static final String SSO_REALM = "registry"; // Defined in kubefiles/keycloak

    public static final String TESTSUITE_NAMESPACE = "testsuite-namespace";

    // TODO: Move other constants here too?
    // PostgreSQL port
    // PostgreSQL username
    // PostgreSQL password
    // PostgreSQL admin password
    // PostgreSQL database name
    // PostgreSQL image
    // PostgreSQL volume size
    // Default KafkaSQL values
    // Catalog source display name
    // Catalog source publisher
    // Catalog source source type
    // Catalog source pod label
}
