/*
 * Copyright 2024 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.tests.utils;

import io.apicurio.deployment.RegistryDeploymentManager;
import io.apicurio.deployment.TestConfiguration;
import io.apicurio.deployment.manual.ProxyKafkaRunner;
import io.apicurio.deployment.manual.ProxyRegistryRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for performing comprehensive health checks in KafkaSQL integration tests
 */
public class HealthCheckUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckUtils.class);

    /**
     * Performs all pre-test health checks for KafkaSQL integration tests
     */
    public static void performComprehensiveHealthChecks() {
        LOGGER.info("=== Starting Comprehensive Health Checks ===");
        
        try {
            // 1. Verify Kubernetes infrastructure if in cluster mode
            if (TestConfiguration.isClusterTests()) {
                LOGGER.info("Verifying Kubernetes infrastructure...");
                RegistryDeploymentManager.verifyPodsReady();
                RegistryDeploymentManager.verifyServiceEndpoints();
                RegistryDeploymentManager.verifyNetworkConnectivity();
            }
            
            LOGGER.info("=== All Health Checks Passed ===");
            
        } catch (Exception e) {
            LOGGER.error("=== Health Check Failed ===", e);
            RegistryDeploymentManager.collectDiagnosticInfo();
            throw new RuntimeException("Pre-test health checks failed", e);
        }
    }

    /**
     * Verifies that Kafka services are ready and accessible
     */
    public static void verifyKafkaServices(ProxyKafkaRunner kafka) {
        LOGGER.info("Verifying Kafka services...");
        
        if (kafka != null) {
            kafka.verifyKafkaConnectivity();
            kafka.verifyKafkaTopicOperations();
        } else {
            throw new IllegalStateException("Kafka runner is null - cannot verify services");
        }
    }

    /**
     * Verifies that Registry services are ready and accessible
     */
    public static void verifyRegistryServices(ProxyRegistryRunner registry) {
        LOGGER.info("Verifying Registry services...");
        
        if (registry != null) {
            registry.verifyRegistryHealth();
            registry.verifyRegistryApiOperations();
            registry.verifyRegistryReadiness();
        } else {
            throw new IllegalStateException("Registry runner is null - cannot verify services");
        }
    }

    /**
     * Performs a quick connectivity test for critical services
     */
    public static void performQuickConnectivityTest() {
        LOGGER.info("Performing quick connectivity test...");
        
        boolean kafkaReachable = RegistryDeploymentManager.testNetworkConnectivity("localhost", 19092, "Kafka");
        boolean registryReachable = RegistryDeploymentManager.testNetworkConnectivity("localhost", 8781, "Registry");
        
        if (!kafkaReachable) {
            throw new RuntimeException("Kafka is not reachable at localhost:19092");
        }
        
        if (!registryReachable) {
            throw new RuntimeException("Registry is not reachable at localhost:8781");
        }
        
        LOGGER.info("✓ Quick connectivity test passed");
    }

    /**
     * Logs detailed service status for debugging
     */
    public static void logServiceStatus(ProxyKafkaRunner kafka, ProxyRegistryRunner registry) {
        LOGGER.info("=== Service Status Report ===");
        
        if (kafka != null) {
            try {
                LOGGER.info("Kafka Bootstrap Servers: {}", kafka.getBootstrapServers());
                LOGGER.info("Kafka Test Client Bootstrap Servers: {}", kafka.getTestClientBootstrapServers());
                LOGGER.info("Kafka Status: Running");
            } catch (Exception e) {
                LOGGER.warn("Error getting Kafka status: {}", e.getMessage());
            }
        } else {
            LOGGER.warn("Kafka: Not initialized");
        }
        
        if (registry != null) {
            try {
                LOGGER.info("Registry Client URL: {}", registry.getClientURL());
                LOGGER.info("Registry Started: {}", registry.isStarted());
                LOGGER.info("Registry Stopped: {}", registry.isStopped());
                LOGGER.info("Registry Ready: {}", registry.isReady());
            } catch (Exception e) {
                LOGGER.warn("Error getting Registry status: {}", e.getMessage());
            }
        } else {
            LOGGER.warn("Registry: Not initialized");
        }
        
        LOGGER.info("=== End Service Status Report ===");
    }

    private HealthCheckUtils() {
        // Utility class
    }
}