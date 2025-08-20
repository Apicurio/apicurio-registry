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
        //First, create the namespace used for the test.
        try {
            kubernetesClient().load(getClass().getResourceAsStream(E2E_NAMESPACE_RESOURCE))
                    .create();
        }
        catch (KubernetesClientException ex) {
            LOGGER.warn("Could not create namespace (it may already exist).", ex);
        }

        //Based on the configuration, deploy the appropriate variant
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
        }
    }

    static void prepareTestsInfra(String externalResources, String registryResources, boolean startKeycloak, String
            registryImage, boolean startTenantManager) throws IOException {
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
            LOGGER.info("Deploying external dependencies for Registry ##################################################");
            deployResource(externalResources);
        }

        final InputStream resourceAsStream = RegistryDeploymentManager.class.getResourceAsStream(registryResources);

        assert resourceAsStream != null;

        String registryLoadedResources = IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8.name());

        if (registryImage != null) {
            registryLoadedResources = registryLoadedResources.replace(REGISTRY_IMAGE, registryImage);
        }

        try {
            //Deploy all the resources associated to the registry variant
            kubernetesClient().load(IOUtils.toInputStream(registryLoadedResources, StandardCharsets.UTF_8.name()))
                    .create();
        }
        catch (Exception ex) {
            LOGGER.warn("Error creating registry resources:", ex);
        }

        //Wait for all the pods of the variant to be ready
        kubernetesClient().pods()
                .inNamespace(TEST_NAMESPACE).waitUntilReady(360, TimeUnit.SECONDS);

        setupTestNetworking(startTenantManager);
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
        //Deploy all the resources associated to the external requirements
        kubernetesClient().load(RegistryDeploymentManager.class.getResourceAsStream(resource))
                .create();

        //Wait for all the external resources pods to be ready
        kubernetesClient().pods()
                .inNamespace(TEST_NAMESPACE).waitUntilReady(180, TimeUnit.SECONDS);
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