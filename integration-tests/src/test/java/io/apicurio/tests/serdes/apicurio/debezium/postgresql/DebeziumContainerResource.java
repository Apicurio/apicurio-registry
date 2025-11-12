package io.apicurio.tests.serdes.apicurio.debezium.postgresql;

import io.apicurio.tests.serdes.apicurio.debezium.BaseDebeziumContainerResource;
import io.apicurio.tests.serdes.apicurio.debezium.KubernetesDebeziumContainerWrapper;
import io.apicurio.tests.serdes.apicurio.debezium.SharedDebeziumInfrastructure;
import io.debezium.testing.testcontainers.DebeziumContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Container resource for Debezium PostgreSQL integration tests.
 * In local mode, this uses SharedDebeziumInfrastructure to share containers across test classes.
 */
public class DebeziumContainerResource extends BaseDebeziumContainerResource {

    private static final Logger log = LoggerFactory.getLogger(DebeziumContainerResource.class);

    public static PostgreSQLContainer<?> postgresContainer;
    public static DebeziumContainer debeziumContainer;

    @Override
    protected String getDatabaseType() {
        return "PostgreSQL";
    }

    @Override
    protected void createKubernetesWrappers() {
        // Create Kubernetes-aware wrapper containers for PostgreSQL
        postgresContainer = new KubernetesPostgreSQLContainerWrapper(
                io.apicurio.deployment.KubernetesTestResources.POSTGRESQL_DEBEZIUM_SERVICE_EXTERNAL);
        debeziumContainer = new KubernetesDebeziumContainerWrapper(
                io.apicurio.deployment.KubernetesTestResources.DEBEZIUM_CONNECT_SERVICE_EXTERNAL);
    }

    @Override
    protected void initializeSharedInfrastructure() {
        // Initialize PostgreSQL infrastructure via SharedDebeziumInfrastructure
        SharedDebeziumInfrastructure.initializePostgreSQLInfrastructure();
    }

    @Override
    protected void setContainerReferences() {
        // Set references from SharedDebeziumInfrastructure
        postgresContainer = SharedDebeziumInfrastructure.postgresContainer;
        debeziumContainer = SharedDebeziumInfrastructure.debeziumContainerPostgres;
    }
}
