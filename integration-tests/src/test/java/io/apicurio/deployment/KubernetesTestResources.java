package io.apicurio.deployment;

public class KubernetesTestResources {

    protected static final String E2E_NAMESPACE_RESOURCE = "/infra/e2e-namespace.yml";
    protected static final String REGISTRY_OPENSHIFT_ROUTE = "/infra/openshift/registry-route.yml";
    protected static final String APPLICATION_IN_MEMORY_RESOURCES = "/infra/in-memory/registry-in-memory.yml";
    protected static final String APPLICATION_SQL_RESOURCES = "/infra/sql/registry-sql.yml";
    protected static final String APPLICATION_KAFKA_RESOURCES = "/infra/kafka/registry-kafka.yml";
    protected static final String APPLICATION_IN_MEMORY_SECURED_RESOURCES = "/infra/in-memory/registry-in-memory-secured.yml";
    protected static final String APPLICATION_SQL_SECURED_RESOURCES = "/infra/sql/registry-sql-secured.yml";
    protected static final String APPLICATION_KAFKA_SECURED_RESOURCES = "/infra/kafka/registry-kafka-secured.yml";
    protected static final String KAFKA_RESOURCES = "/infra/kafka/kafka.yml";
    protected static final String DATABASE_RESOURCES = "/infra/sql/postgresql.yml";
    protected static final String KEYCLOAK_RESOURCES = "/infra/auth/keycloak.yml";
    protected static final String TEST_NAMESPACE = "apicurio-registry-e2e";
    protected static final String APPLICATION_SERVICE = "apicurio-registry-service";
    protected static final String APPLICATION_DEPLOYMENT = "apicurio-registry-deployment";
    protected static final String KEYCLOAK_SERVICE = "keycloak-service";
}
