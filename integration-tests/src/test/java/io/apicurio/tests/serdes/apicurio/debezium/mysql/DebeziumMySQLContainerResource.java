package io.apicurio.tests.serdes.apicurio.debezium.mysql;

import io.apicurio.tests.serdes.apicurio.debezium.BaseDebeziumContainerResource;
import io.apicurio.tests.serdes.apicurio.debezium.postgresql.KubernetesDebeziumContainerWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test resource for Debezium MySQL integration tests using published converters.
 */
public class DebeziumMySQLContainerResource extends BaseDebeziumContainerResource {

    private static final Logger log = LoggerFactory.getLogger(DebeziumMySQLContainerResource.class);

    public static MySQLContainer<?> mysqlContainer;

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
    protected void createLocalContainers() {
        // Create local Testcontainers for MySQL
        mysqlContainer = createMySQLContainer();
        debeziumContainer = createDebeziumContainer();
    }

    @Override
    protected GenericContainer<?> getDatabaseContainer() {
        return mysqlContainer;
    }

    /**
     * Creates MySQL container with appropriate network configuration.
     */
    protected static MySQLContainer<?> createMySQLContainer() {
        MySQLContainer<?> container = new MySQLContainer<>(
                DockerImageName.parse("quay.io/debezium/example-mysql:2.5").asCompatibleSubstituteFor("mysql"))
                .withDatabaseName("registry")
                .withUsername("mysqluser")
                .withPassword("mysqlpw")
                .withUrlParam("allowPublicKeyRetrieval", "true")
                .withUrlParam("useSSL", "false")
                .withUrlParam("connectTimeout", "10000")
                .withUrlParam("socketTimeout", "10000")
                .withUrlParam("autoReconnect", "true");

        if (shouldUseHostNetwork()) {
            log.info("Using host network mode for MySQL container (Linux/CI environment)");
            container.withNetworkMode("host");
            container.withStartupTimeout(java.time.Duration.ofSeconds(60))
                    .waitingFor(new org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy()
                            .withRegEx(".*ready for connections.*")
                            .withTimes(2)
                            .withStartupTimeout(java.time.Duration.ofSeconds(60)));
        } else {
            log.info("Using bridge network mode for MySQL container");
            container.withNetwork(network).withNetworkAliases("mysql");
        }

        return container;
    }
}
