/*
 * Copyright 2023 Red Hat
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

package io.apicurio.deployment;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.apicurio.deployment.Constants.REGISTRY_IMAGE;
import static io.apicurio.deployment.KubernetesTestResources.*;
import static io.apicurio.deployment.k8s.K8sClientManager.kubernetesClient;
import static io.apicurio.deployment.k8s.PortForwardManager.closePortForwards;
import static io.apicurio.deployment.k8s.PortForwardManager.startPortForward;

public class RegistryDeploymentManager implements TestExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryDeploymentManager.class);

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        TestConfiguration.print();
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {

            try {
                handleInfraDeployment();
            }
            catch (Exception e) {
                LOGGER.error("Error starting registry deployment", e);
            }

            LOGGER.info("Test suite started ##################################################");
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        LOGGER.info("Test suite ended ##################################################");

        try {

            if (TestConfiguration.isClusterTests()) {
                if (!TestConfiguration.isPreserveNamespace()) {
                    LOGGER.info("Closing test resources ##################################################");

                    closePortForwards();

                    final Resource<Namespace> namespaceResource = kubernetesClient().namespaces()
                            .withName(TEST_NAMESPACE);

                    namespaceResource.delete();

                }
            }
        }
        catch (Exception e) {
            LOGGER.error("Exception closing test resources", e);
        }
        finally {
            if (kubernetesClient() != null) {
                kubernetesClient().close();
            }
        }
    }

    private void handleInfraDeployment() throws Exception {
        LOGGER.info("=== Starting Infrastructure Deployment ===");
        
        // Add comprehensive property diagnostics
        logDeploymentConfiguration();
        
        //First, create the namespace used for the test.
        LOGGER.info("Creating test namespace: {}", TEST_NAMESPACE);
        try {
            kubernetesClient().load(getClass().getResourceAsStream(E2E_NAMESPACE_RESOURCE))
                    .create();
            LOGGER.info("✓ Test namespace created successfully");
        }
        catch (KubernetesClientException ex) {
            LOGGER.warn("Could not create namespace (it may already exist): {}", ex.getMessage());
        }

        // Verify namespace exists
        verifyNamespaceExists();

        //Based on the configuration, deploy the appropriate variant
        LOGGER.info("Determining deployment variant based on system properties...");
        
        if (Boolean.parseBoolean(System.getProperty("deployInMemory"))) {
            LOGGER.info("Deploying In Memory Registry Variant with image: {} ##################################################",
                    System.getProperty("registry-in-memory-image"));
            InMemoryDeploymentManager.deployInMemoryApp(System.getProperty("registry-in-memory-image"));
        }
        else if (Boolean.parseBoolean(System.getProperty("deploySql"))) {
            LOGGER.info("Deploying SQL Registry Variant with image: {} ##################################################", System.getProperty("registry-sql-image"));
            SqlDeploymentManager.deploySqlApp(System.getProperty("registry-sql-image"));
        }
        else if (Boolean.parseBoolean(System.getProperty("deployKafka"))) {
            LOGGER.info("Deploying Kafka SQL Registry Variant with image: {} ##################################################",
                    System.getProperty("registry-kafkasql-image"));
            KafkaSqlDeploymentManager.deployKafkaApp(System.getProperty("registry-kafkasql-image"));
            
            // Verify KafkaSQL deployment was successful
            verifyKafkaSqlDeployment();
        } else {
            LOGGER.error("=== NO DEPLOYMENT VARIANT SELECTED ===");
            LOGGER.error("None of the deployment properties are set to true:");
            LOGGER.error("- deployInMemory: {}", System.getProperty("deployInMemory"));
            LOGGER.error("- deploySql: {}", System.getProperty("deploySql"));
            LOGGER.error("- deployKafka: {}", System.getProperty("deployKafka"));
            throw new IllegalStateException("No deployment variant selected - check system properties");
        }
        
        LOGGER.info("=== Infrastructure Deployment Complete ===");
    }

    static void prepareTestsInfra(String externalResources, String registryResources, boolean startKeycloak, String
            registryImage, boolean startTenantManager) throws IOException {
        LOGGER.info("=== Preparing Test Infrastructure ===");
        LOGGER.info("Parameters:");
        LOGGER.info("- externalResources: {}", externalResources);
        LOGGER.info("- registryResources: {}", registryResources);
        LOGGER.info("- startKeycloak: {}", startKeycloak);
        LOGGER.info("- registryImage: {}", registryImage);
        LOGGER.info("- startTenantManager: {}", startTenantManager);
        
        if (startKeycloak) {
            LOGGER.info("Deploying Keycloak resources ##################################################");
            deployResource(KEYCLOAK_RESOURCES);
        }

        if (startTenantManager) {
            LOGGER.info("Deploying Tenant Manager resources ##################################################");
            deployResource(TENANT_MANAGER_DATABASE);
            deployResource(TENANT_MANAGER_RESOURCES);
        }

        if (externalResources != null) {
            LOGGER.info("Deploying external dependencies for Registry: {} ##################################################", externalResources);
            deployResource(externalResources);
        } else {
            LOGGER.info("No external resources to deploy");
        }

        LOGGER.info("Deploying registry resources: {} ##################################################", registryResources);
        final InputStream resourceAsStream = RegistryDeploymentManager.class.getResourceAsStream(registryResources);

        if (resourceAsStream == null) {
            LOGGER.error("✗ Registry resource file '{}' not found in classpath", registryResources);
            throw new IllegalArgumentException("Registry resource file not found: " + registryResources);
        }

        String registryLoadedResources = IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8.name());
        LOGGER.info("✓ Registry resource file loaded successfully, {} characters", registryLoadedResources.length());

        if (registryImage != null) {
            LOGGER.info("Replacing placeholder '{}' with actual image '{}'", REGISTRY_IMAGE, registryImage);
            String originalResources = registryLoadedResources;
            registryLoadedResources = registryLoadedResources.replace(REGISTRY_IMAGE, registryImage);
            if (originalResources.equals(registryLoadedResources)) {
                LOGGER.warn("⚠ No placeholder replacement occurred - image may not be set correctly");
            } else {
                LOGGER.info("✓ Image placeholder replacement completed");
            }
        } else {
            LOGGER.warn("⚠ No registry image specified - using placeholder value");
        }

        try {
            LOGGER.info("Creating Kubernetes resources...");
            //Deploy all the resources associated to the registry variant
            var resourceList = kubernetesClient().load(IOUtils.toInputStream(registryLoadedResources, StandardCharsets.UTF_8.name()))
                    .create();
            LOGGER.info("✓ Successfully created {} Kubernetes resources", resourceList.size());
            
            // Log what was created
            resourceList.forEach(resource -> {
                LOGGER.info("  - Created: {} '{}'", 
                    resource.getKind(), 
                    resource.getMetadata().getName());
            });
        }
        catch (Exception ex) {
            LOGGER.error("✗ Error creating registry resources", ex);
            throw new RuntimeException("Failed to create registry resources", ex);
        }

        LOGGER.info("Waiting for pods to be ready (timeout: 360 seconds)...");
        try {
            //Wait for all the pods of the variant to be ready
            kubernetesClient().pods()
                    .inNamespace(TEST_NAMESPACE).waitUntilReady(360, TimeUnit.SECONDS);
            LOGGER.info("✓ All pods are ready");
        } catch (Exception ex) {
            LOGGER.error("✗ Timeout waiting for pods to be ready", ex);
            
            // Collect diagnostic information
            collectDetailedPodDiagnostics();
            throw new RuntimeException("Pods failed to become ready within timeout", ex);
        }

        LOGGER.info("Setting up test networking...");
        setupTestNetworking(startTenantManager);
        LOGGER.info("=== Test Infrastructure Preparation Complete ===");
    }

    private static void setupTestNetworking(boolean startTenantManager) {
        if (Constants.TEST_PROFILE.equals(Constants.UI)) {
            //In the UI tests we use a port forward to make the application available to the testsuite.
            startPortForward(APPLICATION_SERVICE, 8080);
        }
        else {

            //For openshift, a route to the application is created we use it to set up the networking needs.
            if (Boolean.parseBoolean(System.getProperty("openshift.resources"))) {

                try (OpenShiftClient openShiftClient = new DefaultOpenShiftClient()) {

                    try {
                        final Route registryRoute = openShiftClient.routes()
                                .load(RegistryDeploymentManager.class.getResourceAsStream(REGISTRY_OPENSHIFT_ROUTE))
                                .create();
                        System.setProperty("quarkus.http.test-host", registryRoute.getSpec().getHost());
                        System.setProperty("quarkus.http.test-port", "80");

                    }
                    catch (Exception ex) {
                        LOGGER.warn("The registry route already exists: ", ex);
                    }

                    try {
                        final Route tenantManagerRoute = openShiftClient.routes()
                                .load(RegistryDeploymentManager.class.getResourceAsStream(TENANT_MANAGER_OPENSHIFT_ROUTE))
                                .create();

                        System.setProperty("tenant.manager.external.endpoint", tenantManagerRoute.getSpec().getHost());
                    }
                    catch (Exception ex) {
                        LOGGER.warn("The tenant manger route already exists: ", ex);
                    }
                }

            }
            else {
                //If we're running the cluster tests but no external endpoint has been provided, set the value of the load balancer.
                if (System.getProperty("quarkus.http.test-host").equals("localhost") && !System.getProperty("os.name").contains("Mac OS")) {
                    System.setProperty("quarkus.http.test-host",
                            kubernetesClient().services().inNamespace(TEST_NAMESPACE).withName(APPLICATION_SERVICE).get().getSpec().getClusterIP());
                }

                //If we're running the cluster tests but no external endpoint has been provided, set the value of the load balancer.
                if (startTenantManager && System.getProperty("tenant.manager.external.endpoint") == null) {
                    System.setProperty("tenant.manager.external.endpoint",
                            kubernetesClient().services().inNamespace(TEST_NAMESPACE).withName(TENANT_MANAGER_SERVICE).get().getSpec().getClusterIP());
                }
            }
        }
    }

    private static void deployResource(String resource) {
        LOGGER.info("Deploying resource: {}", resource);
        
        try {
            var resourceStream = RegistryDeploymentManager.class.getResourceAsStream(resource);
            if (resourceStream == null) {
                LOGGER.error("✗ Resource file '{}' not found in classpath", resource);
                throw new IllegalArgumentException("Resource file not found: " + resource);
            }
            
            //Deploy all the resources associated to the external requirements
            var resourceList = kubernetesClient().load(resourceStream).create();
            LOGGER.info("✓ Successfully deployed {} resources from '{}'", resourceList.size(), resource);
            
            resourceList.forEach(res -> {
                LOGGER.info("  - Deployed: {} '{}'", res.getKind(), res.getMetadata().getName());
            });

            LOGGER.info("Waiting for pods from '{}' to be ready (timeout: 180 seconds)...", resource);
            //Wait for all the external resources pods to be ready
            kubernetesClient().pods()
                    .inNamespace(TEST_NAMESPACE).waitUntilReady(180, TimeUnit.SECONDS);
            LOGGER.info("✓ All pods from '{}' are ready", resource);
            
        } catch (Exception ex) {
            LOGGER.error("✗ Failed to deploy resource '{}': {}", resource, ex.getMessage(), ex);
            throw new RuntimeException("Failed to deploy resource: " + resource, ex);
        }
    }

    // === Health Check Methods

    /**
     * Verifies that all pods in the test namespace are ready
     */
    public static void verifyPodsReady() {
        LOGGER.info("Verifying all pods are ready...");
        
        var timeout = Duration.ofSeconds(300);
        Awaitility.await("All pods to be ready")
            .atMost(timeout)
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> {
                var pods = kubernetesClient().pods()
                    .inNamespace(TEST_NAMESPACE)
                    .list()
                    .getItems();
                    
                var notReady = pods.stream()
                    .filter(pod -> !isPodReady(pod))
                    .toList();
                    
                if (!notReady.isEmpty()) {
                    LOGGER.warn("Pods not ready: {}", 
                        notReady.stream()
                            .map(p -> p.getMetadata().getName() + " (phase: " + 
                                     p.getStatus().getPhase() + ")")
                            .collect(Collectors.joining(", ")));
                    return false;
                }
                
                LOGGER.info("✓ All {} pods are ready", pods.size());
                return true;
            });
    }

    /**
     * Checks if a specific pod is ready
     */
    private static boolean isPodReady(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getConditions() == null) {
            return false;
        }
        
        var conditions = pod.getStatus().getConditions();
        return conditions.stream()
            .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
    }

    /**
     * Verifies that all required services have endpoints
     */
    public static void verifyServiceEndpoints() {
        LOGGER.info("Verifying service endpoints...");
        
        // Get all services in the test namespace
        var services = kubernetesClient().services()
            .inNamespace(TEST_NAMESPACE)
            .list()
            .getItems()
            .stream()
            .map(s -> s.getMetadata().getName())
            .filter(name -> !name.equals("kubernetes")) // Skip default kubernetes service
            .collect(Collectors.toList());
        
        for (var serviceName : services) {
            Awaitility.await("Service " + serviceName + " to have endpoints")
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofSeconds(3))
                .until(() -> {
                    var endpoints = kubernetesClient().endpoints()
                        .inNamespace(TEST_NAMESPACE)
                        .withName(serviceName)
                        .get();
                        
                    if (endpoints == null || endpoints.getSubsets() == null || endpoints.getSubsets().isEmpty()) {
                        LOGGER.warn("Service {} has no endpoints", serviceName);
                        return false;
                    }
                    
                    var readyAddresses = endpoints.getSubsets().stream()
                        .mapToInt(subset -> subset.getAddresses() != null ? subset.getAddresses().size() : 0)
                        .sum();
                        
                    if (readyAddresses == 0) {
                        LOGGER.warn("Service {} has no ready addresses", serviceName);
                        return false;
                    }
                    
                    LOGGER.info("✓ Service {} has {} ready endpoints", serviceName, readyAddresses);
                    return true;
                });
        }
    }

    /**
     * Verifies basic network connectivity to critical services
     */
    public static void verifyNetworkConnectivity() {
        LOGGER.info("Verifying network connectivity...");
        
        var checks = List.of(
            new NetworkCheck("Kafka", "localhost", 19092),
            new NetworkCheck("Registry", "localhost", 8781)
        );
        
        for (var check : checks) {
            Awaitility.await(check.name + " port to be reachable")
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(3))
                .until(() -> {
                    return testNetworkConnectivity(check.host, check.port, check.name);
                });
        }
    }

    /**
     * Tests network connectivity to a specific host and port
     */
    public static boolean testNetworkConnectivity(String host, int port, String serviceName) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            LOGGER.info("✓ {} is reachable at {}:{}", serviceName, host, port);
            return true;
        } catch (Exception e) {
            LOGGER.warn("{} not reachable at {}:{}: {}", 
                       serviceName, host, port, e.getMessage());
            return false;
        }
    }

    /**
     * Collects and logs diagnostic information when health checks fail
     */
    public static void collectDiagnosticInfo() {
        LOGGER.error("=== Collecting Diagnostic Information ===");
        
        try {
            // Log pod statuses
            LOGGER.error("--- Pod Status Information ---");
            kubernetesClient().pods()
                .inNamespace(TEST_NAMESPACE)
                .list()
                .getItems()
                .forEach(pod -> {
                    var status = pod.getStatus();
                    var phase = status != null ? status.getPhase() : "Unknown";
                    var ready = isPodReady(pod);
                    
                    LOGGER.error("Pod {}: phase={}, ready={}", 
                        pod.getMetadata().getName(), phase, ready);
                        
                    // Log pod conditions for more detail
                    if (status != null && status.getConditions() != null) {
                        status.getConditions().forEach(condition -> {
                            LOGGER.error("  Condition {}: status={}, reason={}", 
                                condition.getType(), condition.getStatus(), condition.getReason());
                        });
                    }
                });
                
            // Log service endpoints
            LOGGER.error("--- Service Endpoint Information ---");
            kubernetesClient().endpoints()
                .inNamespace(TEST_NAMESPACE)
                .list()
                .getItems()
                .forEach(endpoint -> {
                    var addresses = 0;
                    if (endpoint.getSubsets() != null) {
                        addresses = endpoint.getSubsets().stream()
                            .mapToInt(subset -> subset.getAddresses() != null ? subset.getAddresses().size() : 0)
                            .sum();
                    }
                    LOGGER.error("Service {}: {} ready addresses", 
                        endpoint.getMetadata().getName(), addresses);
                });
                
        } catch (Exception e) {
            LOGGER.error("Error collecting diagnostic information", e);
        }
        
        // Test basic network connectivity
        LOGGER.error("--- Network Connectivity Tests ---");
        testNetworkConnectivity("localhost", 19092, "Kafka");
        testNetworkConnectivity("localhost", 8781, "Registry");
    }

    // === Deployment Diagnostic Methods

    /**
     * Logs comprehensive deployment configuration for debugging
     */
    private void logDeploymentConfiguration() {
        LOGGER.info("=== Deployment Configuration Diagnostics ===");
        LOGGER.info("System Properties:");
        LOGGER.info("- deployInMemory: '{}'", System.getProperty("deployInMemory"));
        LOGGER.info("- deploySql: '{}'", System.getProperty("deploySql"));
        LOGGER.info("- deployKafka: '{}'", System.getProperty("deployKafka"));
        LOGGER.info("- registry-kafkasql-image: '{}'", System.getProperty("registry-kafkasql-image"));
        LOGGER.info("- registry-in-memory-image: '{}'", System.getProperty("registry-in-memory-image"));
        LOGGER.info("- registry-sql-image: '{}'", System.getProperty("registry-sql-image"));
        LOGGER.info("- groups (TEST_PROFILE): '{}'", System.getProperty("groups"));
        LOGGER.info("- cluster.tests: '{}'", System.getProperty("cluster.tests"));
        
        LOGGER.info("Environment:");
        LOGGER.info("- TEST_NAMESPACE: '{}'", TEST_NAMESPACE);
        LOGGER.info("- TEST_PROFILE: '{}'", io.apicurio.deployment.Constants.TEST_PROFILE);
        
        // Boolean parsing diagnostics
        LOGGER.info("Boolean Parsing Results:");
        LOGGER.info("- Boolean.parseBoolean(deployInMemory): {}", Boolean.parseBoolean(System.getProperty("deployInMemory")));
        LOGGER.info("- Boolean.parseBoolean(deploySql): {}", Boolean.parseBoolean(System.getProperty("deploySql")));
        LOGGER.info("- Boolean.parseBoolean(deployKafka): {}", Boolean.parseBoolean(System.getProperty("deployKafka")));
        LOGGER.info("=== End Configuration Diagnostics ===");
    }

    /**
     * Verifies that the test namespace exists and is accessible
     */
    private void verifyNamespaceExists() {
        try {
            var namespace = kubernetesClient().namespaces().withName(TEST_NAMESPACE).get();
            if (namespace != null) {
                LOGGER.info("✓ Test namespace '{}' exists and is accessible", TEST_NAMESPACE);
                LOGGER.info("  - Creation timestamp: {}", namespace.getMetadata().getCreationTimestamp());
                LOGGER.info("  - Status: {}", namespace.getStatus().getPhase());
            } else {
                LOGGER.error("✗ Test namespace '{}' does not exist", TEST_NAMESPACE);
            }
        } catch (Exception e) {
            LOGGER.error("✗ Error accessing test namespace '{}': {}", TEST_NAMESPACE, e.getMessage());
        }
    }

    /**
     * Verifies that KafkaSQL deployment was successful
     */
    private void verifyKafkaSqlDeployment() {
        LOGGER.info("=== Verifying KafkaSQL Deployment ===");
        
        try {
            // Check pods in namespace
            var pods = kubernetesClient().pods().inNamespace(TEST_NAMESPACE).list();
            LOGGER.info("Found {} pods in namespace '{}':", pods.getItems().size(), TEST_NAMESPACE);
            
            pods.getItems().forEach(pod -> {
                var name = pod.getMetadata().getName();
                var phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown";
                LOGGER.info("  - Pod '{}': phase={}", name, phase);
            });
            
            // Check services in namespace
            var services = kubernetesClient().services().inNamespace(TEST_NAMESPACE).list();
            LOGGER.info("Found {} services in namespace '{}':", services.getItems().size(), TEST_NAMESPACE);
            
            services.getItems().forEach(service -> {
                var name = service.getMetadata().getName();
                var type = service.getSpec().getType();
                LOGGER.info("  - Service '{}': type={}", name, type);
            });
            
            // Check deployments in namespace
            var deployments = kubernetesClient().apps().deployments().inNamespace(TEST_NAMESPACE).list();
            LOGGER.info("Found {} deployments in namespace '{}':", deployments.getItems().size(), TEST_NAMESPACE);
            
            deployments.getItems().forEach(deployment -> {
                var name = deployment.getMetadata().getName();
                var replicas = deployment.getSpec().getReplicas();
                var readyReplicas = deployment.getStatus() != null ? deployment.getStatus().getReadyReplicas() : 0;
                LOGGER.info("  - Deployment '{}': replicas={}, ready={}", name, replicas, readyReplicas);
            });
            
            if (pods.getItems().isEmpty() && services.getItems().isEmpty() && deployments.getItems().isEmpty()) {
                LOGGER.error("✗ KafkaSQL deployment verification FAILED - no resources found in namespace");
            } else {
                LOGGER.info("✓ KafkaSQL deployment verification completed - resources found");
            }
            
        } catch (Exception e) {
            LOGGER.error("✗ Error verifying KafkaSQL deployment: {}", e.getMessage(), e);
        }
        
        LOGGER.info("=== End KafkaSQL Deployment Verification ===");
    }

    /**
     * Collects detailed diagnostics when pods fail to start
     */
    private static void collectDetailedPodDiagnostics() {
        LOGGER.error("=== Detailed Pod Diagnostics ===");
        
        try {
            var pods = kubernetesClient().pods().inNamespace(TEST_NAMESPACE).list();
            LOGGER.error("Found {} pods in namespace '{}':", pods.getItems().size(), TEST_NAMESPACE);
            
            for (var pod : pods.getItems()) {
                var name = pod.getMetadata().getName();
                var phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown";
                
                LOGGER.error("Pod '{}': phase={}", name, phase);
                
                // Log pod conditions
                if (pod.getStatus() != null && pod.getStatus().getConditions() != null) {
                    pod.getStatus().getConditions().forEach(condition -> {
                        LOGGER.error("  - Condition {}: status={}, reason={}, message={}", 
                            condition.getType(), 
                            condition.getStatus(), 
                            condition.getReason(),
                            condition.getMessage());
                    });
                }
                
                // Log container statuses
                if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
                    pod.getStatus().getContainerStatuses().forEach(containerStatus -> {
                        LOGGER.error("  - Container '{}': ready={}, restartCount={}", 
                            containerStatus.getName(),
                            containerStatus.getReady(),
                            containerStatus.getRestartCount());
                            
                        if (containerStatus.getState() != null) {
                            if (containerStatus.getState().getWaiting() != null) {
                                LOGGER.error("    State: Waiting - reason='{}', message='{}'",
                                    containerStatus.getState().getWaiting().getReason(),
                                    containerStatus.getState().getWaiting().getMessage());
                            } else if (containerStatus.getState().getTerminated() != null) {
                                LOGGER.error("    State: Terminated - reason='{}', message='{}'",
                                    containerStatus.getState().getTerminated().getReason(),
                                    containerStatus.getState().getTerminated().getMessage());
                            } else if (containerStatus.getState().getRunning() != null) {
                                LOGGER.error("    State: Running since {}",
                                    containerStatus.getState().getRunning().getStartedAt());
                            }
                        }
                    });
                }
                
                // Try to get pod events for more context
                try {
                    var events = kubernetesClient().v1().events()
                        .inNamespace(TEST_NAMESPACE)
                        .withField("involvedObject.name", name)
                        .list();
                        
                    if (!events.getItems().isEmpty()) {
                        LOGGER.error("  Events for pod '{}':", name);
                        events.getItems().forEach(event -> {
                            LOGGER.error("    - {}: {} ({})", 
                                event.getType(),
                                event.getMessage(),
                                event.getReason());
                        });
                    }
                } catch (Exception e) {
                    LOGGER.error("  Could not retrieve events for pod '{}': {}", name, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("Error collecting pod diagnostics: {}", e.getMessage(), e);
        }
        
        LOGGER.error("=== End Detailed Pod Diagnostics ===");
    }

    /**
     * Class for network connectivity checks
     */
    public static class NetworkCheck {
        public final String name;
        public final String host;
        public final int port;
        
        public NetworkCheck(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }
    }
}