package io.apicurio.tests.serdes.apicurio.debezium;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * Base class for Debezium container test resources.
 * Provides common infrastructure for both PostgreSQL and MySQL Debezium tests,
 * supporting both local Testcontainers mode and Kubernetes cluster mode.
 *
 * In local mode, this uses SharedDebeziumInfrastructure to share containers
 * across all test classes for improved CI performance.
 */
public abstract class BaseDebeziumContainerResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(BaseDebeziumContainerResource.class);
    @Override
    public Map<String, String> start() {
        // Check if running in Kubernetes cluster mode
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            log.info("cluster.tests=true detected, creating Kubernetes service wrappers");
            log.info("Debezium {} infrastructure should already be deployed by RegistryDeploymentManager",
                    getDatabaseType());

            // Create Kubernetes-aware wrapper containers
            createKubernetesWrappers();

            log.info("Debezium {} service wrappers created for cluster mode", getDatabaseType());
        } else {
            // Local mode: Use SharedDebeziumInfrastructure for container reuse
            log.info("cluster.tests=false, using SharedDebeziumInfrastructure for Debezium {} tests",
                    getDatabaseType());

            // Initialize the shared infrastructure for this database type
            initializeSharedInfrastructure();

            // Get references to the shared containers
            setContainerReferences();

            log.info("Debezium {} infrastructure ready (using shared containers)", getDatabaseType());
        }

        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        // In cluster mode, resources are cleaned up by RegistryDeploymentManager
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            log.info("cluster.tests=true, skipping container stop");
            return;
        }

        // In local mode with shared infrastructure, containers are managed by
        // SharedDebeziumInfrastructure and stopped via JVM shutdown hook
        log.info("Containers managed by SharedDebeziumInfrastructure, will be stopped on JVM exit");
    }

    // ==================== Abstract Methods for Database-Specific Behavior ====================

    /**
     * Returns the database type name for logging purposes.
     */
    protected abstract String getDatabaseType();

    /**
     * Creates Kubernetes wrapper containers for cluster mode.
     * Implementations should set the database container and Debezium container fields.
     */
    protected abstract void createKubernetesWrappers();

    /**
     * Initializes the shared infrastructure for this database type.
     * Implementations should call the appropriate initialization method on SharedDebeziumInfrastructure.
     */
    protected abstract void initializeSharedInfrastructure();

    /**
     * Sets container references from SharedDebeziumInfrastructure to the static fields.
     * Implementations should copy references from SharedDebeziumInfrastructure to their own static fields.
     */
    protected abstract void setContainerReferences();
}
