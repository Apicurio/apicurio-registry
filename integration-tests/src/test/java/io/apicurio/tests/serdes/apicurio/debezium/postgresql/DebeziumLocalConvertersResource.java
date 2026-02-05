package io.apicurio.tests.serdes.apicurio.debezium.postgresql;

import io.apicurio.tests.serdes.apicurio.debezium.DebeziumLocalConvertersUtil;
import io.apicurio.tests.serdes.apicurio.debezium.KubernetesDebeziumContainerWrapper;
import io.apicurio.tests.serdes.apicurio.debezium.SharedDebeziumInfrastructure;
import io.debezium.testing.testcontainers.DebeziumContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Collections;
import java.util.Map;

/**
 * Container resource for Debezium integration tests that uses locally built
 * Apicurio converters instead of downloading them from Maven Central.
 * This ensures tests run against the current SNAPSHOT build of the converters library.
 */
public class DebeziumLocalConvertersResource extends DebeziumContainerResource {

    private static final Logger log = LoggerFactory.getLogger(DebeziumLocalConvertersResource.class);

    // Shadow parent's static fields to avoid pollution between regular and local-converter tests
    public static PostgreSQLContainer postgresContainer;
    public static DebeziumContainer debeziumContainer;

    @Override
    public Map<String, String> start() {
        // Check if running in Kubernetes cluster mode
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            log.info("cluster.tests=true detected for LOCAL CONVERTERS test");
            log.info("Debezium infrastructure with local converters should already be deployed");

            // Create wrappers using the LOCAL converters service
            postgresContainer = new KubernetesPostgreSQLContainerWrapper(
                    io.apicurio.deployment.KubernetesTestResources.POSTGRESQL_DEBEZIUM_SERVICE_EXTERNAL);
            debeziumContainer = new KubernetesDebeziumContainerWrapper(
                    io.apicurio.deployment.KubernetesTestResources.DEBEZIUM_CONNECT_LOCAL_SERVICE_EXTERNAL);

            log.info("Debezium service wrappers created for cluster mode using LOCAL converters");
            return Collections.emptyMap();
        }

        // Local mode: Use SharedDebeziumInfrastructure with local converters
        log.info("cluster.tests=false, using SharedDebeziumInfrastructure with local converters");

        // Initialize shared infrastructure
        SharedDebeziumInfrastructure.initializePostgreSQLInfrastructure();

        // Get references from shared infrastructure
        postgresContainer = SharedDebeziumInfrastructure.postgresContainer;
        debeziumContainer = SharedDebeziumInfrastructure.debeziumContainerPostgres;

        // Mount local converters on the shared Debezium container
        // This should only be done once when the container is first created
        DebeziumLocalConvertersUtil.mountLocalConverters(debeziumContainer);

        log.info("Debezium PostgreSQL infrastructure ready with local converters (using shared containers)");

        return Collections.emptyMap();
    }
}
