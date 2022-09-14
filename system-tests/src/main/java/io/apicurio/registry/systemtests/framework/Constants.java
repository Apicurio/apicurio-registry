package io.apicurio.registry.systemtests.framework;


public class Constants {
    public static final String CATALOG_NAME = "testsuite-operators";
    public static final String CATALOG_NAMESPACE = "openshift-marketplace";
    public static final String KAFKA_CONNECT = "kafka-connect-for-registry";
    public static final String KAFKA = "kafka-for-registry";
    public static final String KAFKA_USER = "kafka-user-for-registry";
    public static final String REGISTRY_OPERATOR_DEPLOYMENT = "apicurio-registry-operator"; // Default from catalog
    public static final String REGISTRY = "registry";
    public static final String SSO_ADMIN_CLIENT_ID = "admin-cli";
    public static final String SSO_ADMIN_USER = "registry-admin"; // Defined in kubefiles/keycloak-realm.yaml
    public static final String SSO_CLIENT_API = "registry-client-api"; // Defined in kubefiles/keycloak-realm.yaml
    public static final String SSO_CLIENT_UI = "registry-client-ui"; // Defined in kubefiles/keycloak-realm.yaml
    public static final String SSO_DEVELOPER_USER = "registry-developer"; // Defined in kubefiles/keycloak-realm.yaml
    public static final String SSO_HTTP_SERVICE = "sso-http";
    public static final String SSO_NAME = "registry-sso"; // Defined in kubefiles/keycloak.yaml
    public static final String SSO_NO_ROLE_USER = "registry-no-role-user"; // Defined in kubefiles/keycloak-realm.yaml
    public static final String SSO_READONLY_USER = "registry-user"; // Defined in kubefiles/keycloak-realm.yaml
    public static final String SSO_REALM = "registry"; // Defined in kubefiles/keycloak-realm.yaml
    public static final String SSO_REALM_ADMIN = "master"; // Default Keycloak admin realm name
    public static final String SSO_SCOPE = "user-attributes"; // Defined in configs/user-attribute-client-scope.json
    public static final String SSO_TEST_CLIENT_API = "test-client-api"; // Defined in kubefiles/keycloak-realm.yaml
    public static final String SSO_USER_PASSWORD = "changeme"; // Defined in kubefiles/keycloak-realm.yaml
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
