package io.apicurio.tests.serdes.apicurio.debezium.mysql;

import io.apicurio.tests.serdes.apicurio.debezium.BaseDebeziumContainerResource;
import io.apicurio.tests.serdes.apicurio.debezium.SharedDebeziumInfrastructure;
import io.apicurio.tests.serdes.apicurio.debezium.KubernetesDebeziumContainerWrapper;
import io.debezium.testing.testcontainers.DebeziumContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;

/**
 * Test resource for Debezium MySQL integration tests using published converters.
 * In local mode, this uses SharedDebeziumInfrastructure to share containers across test classes.
 */
public class DebeziumMySQLContainerResource extends BaseDebeziumContainerResource {

    private static final Logger log = LoggerFactory.getLogger(DebeziumMySQLContainerResource.class);

    public static MySQLContainer<?> mysqlContainer;
    public static DebeziumContainer debeziumContainer;

    @Override
    protected String getDatabaseType() {
        return "MySQL";
    }

    @Override
    protected void createKubernetesWrappers() {
        // Create Kubernetes-aware wrapper containers for MySQL
        mysqlContainer = new KubernetesMySQLContainerWrapper(
                io.apicurio.deployment.KubernetesTestResources.MYSQL_DEBEZIUM_SERVICE_EXTERNAL);
        debeziumContainer = new KubernetesDebeziumContainerWrapper(
                io.apicurio.deployment.KubernetesTestResources.DEBEZIUM_CONNECT_SERVICE_EXTERNAL);
    }

    @Override
    protected void initializeSharedInfrastructure() {
        // Initialize MySQL infrastructure via SharedDebeziumInfrastructure
        SharedDebeziumInfrastructure.initializeMySQLInfrastructure();
    }

    @Override
    protected void setContainerReferences() {
        // Set references from SharedDebeziumInfrastructure
        mysqlContainer = SharedDebeziumInfrastructure.mysqlContainer;
        debeziumContainer = SharedDebeziumInfrastructure.debeziumContainerMysql;
    }
}
