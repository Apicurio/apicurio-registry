package io.apicurio.deployment;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.apicurio.deployment.Constants.REGISTRY_IMAGE;
import static io.apicurio.deployment.KubernetesTestResources.*;

public class RegistryDeploymentManager implements TestExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryDeploymentManager.class);

    public static KubernetesClient kubernetesClient;

    static List<LogWatch> logWatch;

    static String testLogsIdentifier;

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        TestExecutionListener.super.executionStarted(testIdentifier);
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            logWatch = streamPodLogs(testLogsIdentifier);
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        TestExecutionListener.super.executionFinished(testIdentifier, testExecutionResult);

        if (logWatch != null && !logWatch.isEmpty()) {
            logWatch.forEach(LogWatch::close);
        }
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {

            kubernetesClient = new KubernetesClientBuilder().build();

            try {
                handleInfraDeployment();
            } catch (Exception e) {
                LOGGER.error("Error starting registry deployment", e);
            }

            LOGGER.info("Test suite started ##################################################");
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        LOGGER.info("Test suite ended ##################################################");

        try {

            // Finally, once the testsuite is done, cleanup all the resources in the cluster
            if (kubernetesClient != null
                    && !(Boolean.parseBoolean(System.getProperty("preserveNamespace")))) {
                LOGGER.info("Closing test resources ##################################################");

                if (logWatch != null && !logWatch.isEmpty()) {
                    logWatch.forEach(LogWatch::close);
                }

                final Resource<Namespace> namespaceResource = kubernetesClient.namespaces()
                        .withName(TEST_NAMESPACE);

                namespaceResource.delete();

            }
        } catch (Exception e) {
            LOGGER.error("Exception closing test resources", e);
        } finally {
            if (kubernetesClient != null) {
                kubernetesClient.close();
            }
        }
    }

    private void handleInfraDeployment() throws Exception {
        // First, create the namespace used for the test.
        kubernetesClient.load(getClass().getResourceAsStream(E2E_NAMESPACE_RESOURCE)).serverSideApply();

        // Based on the configuration, deploy the appropriate variant
        if (Boolean.parseBoolean(System.getProperty("deployInMemory"))) {
            LOGGER.info(
                    "Deploying In Memory Registry Variant with image: {} ##################################################",
                    System.getProperty("registry-in-memory-image"));
            InMemoryDeploymentManager.deployInMemoryApp(System.getProperty("registry-in-memory-image"));
            testLogsIdentifier = "apicurio-registry-memory";
        } else if (Boolean.parseBoolean(System.getProperty("deploySql"))) {
            LOGGER.info(
                    "Deploying SQL Registry Variant with image: {} ##################################################",
                    System.getProperty("registry-sql-image"));
            SqlDeploymentManager.deploySqlApp(System.getProperty("registry-sql-image"));
            testLogsIdentifier = "apicurio-registry-sql";
        } else if (Boolean.parseBoolean(System.getProperty("deployKafka"))) {
            LOGGER.info(
                    "Deploying Kafka SQL Registry Variant with image: {} ##################################################",
                    System.getProperty("registry-kafkasql-image"));
            KafkaSqlDeploymentManager.deployKafkaApp(System.getProperty("registry-kafkasql-image"));
            testLogsIdentifier = "apicurio-registry-kafka";
        }

        // Deploy ALL Debezium infrastructure if requested (optimized for CI)
        // This deploys everything once: Kafka + PostgreSQL + MySQL + both Debezium Connect variants
        if (Boolean.parseBoolean(System.getProperty("deployAllDebezium"))) {
            LOGGER.info(
                    "Deploying ALL Debezium infrastructure (PostgreSQL + MySQL + both converter types) ##################################################");
            DebeziumDeploymentManager.deployAllDebeziumInfra();
        }
        // Deploy Debezium infrastructure if requested (for Debezium integration tests)
        // Note: With idempotent deployment, multiple calls will reuse already-deployed infrastructure
        else if (Boolean.parseBoolean(System.getProperty("deployDebezium"))) {
            LOGGER.info(
                    "Deploying Debezium PostgreSQL infrastructure ##################################################");
            boolean useLocalConverters = Boolean.parseBoolean(System.getProperty("deployDebeziumLocalConverters"));
            DebeziumDeploymentManager.deployDebeziumInfra(useLocalConverters);
        }
        // Deploy Debezium MySQL infrastructure if requested (for MySQL Debezium integration tests)
        // Note: With idempotent deployment, multiple calls will reuse already-deployed infrastructure
        else if (Boolean.parseBoolean(System.getProperty("deployDebeziumMySQL"))) {
            LOGGER.info(
                    "Deploying Debezium MySQL infrastructure ##################################################");
            boolean useLocalConverters = Boolean.parseBoolean(System.getProperty("deployDebeziumLocalConverters"));
            DebeziumDeploymentManager.deployDebeziumMySQLInfra(useLocalConverters);
        }
    }

    static void prepareTestsInfra(String externalResources, String registryResources, boolean startKeycloak,
            String registryImage) throws IOException {
        if (startKeycloak) {
            LOGGER.info("Deploying Keycloak resources ##################################################");
            deployResource(KEYCLOAK_RESOURCES);
        }

        if (externalResources != null) {
            LOGGER.info(
                    "Deploying external dependencies for Registry ##################################################");
            deployResource(externalResources);
        }

        final InputStream resourceAsStream = RegistryDeploymentManager.class
                .getResourceAsStream(registryResources);

        assert resourceAsStream != null;

        String registryLoadedResources = IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8.name());

        if (registryImage != null) {
            registryLoadedResources = registryLoadedResources.replace(REGISTRY_IMAGE, registryImage);
        }

        try {
            // Deploy all the resources associated to the registry variant
            kubernetesClient
                    .load(IOUtils.toInputStream(registryLoadedResources, StandardCharsets.UTF_8.name()))
                    .serverSideApply();
        } catch (Exception ex) {
            LOGGER.debug("Error creating registry resources:", ex);
        }

        // Wait for all the pods of the variant to be ready
        kubernetesClient.pods().inNamespace(TEST_NAMESPACE).waitUntilReady(360, TimeUnit.SECONDS);

        setupTestNetworking();

        // Wait for registry HTTP endpoint to be accessible via LoadBalancer
        waitForRegistryReady();
    }

    private static void setupTestNetworking() {
        // For openshift, a route to the application is created we use it to set up the networking needs.
        if (Boolean.parseBoolean(System.getProperty("openshift.resources"))) {

            OpenShiftClient openShiftClient = new DefaultOpenShiftClient();

            try {
                final Route registryRoute = openShiftClient.routes()
                        .load(RegistryDeploymentManager.class.getResourceAsStream(REGISTRY_OPENSHIFT_ROUTE))
                        .serverSideApply();

                System.setProperty("quarkus.http.test-host", registryRoute.getSpec().getHost());
                System.setProperty("quarkus.http.test-port", "80");
            } catch (Exception ex) {
                LOGGER.warn("The registry route already exists: ", ex);
            }

        } else {
            // If we're running the cluster tests but no external endpoint has been provided, set the value of
            // the load balancer.
            if (System.getProperty("quarkus.http.test-host").equals("localhost")
                    && !System.getProperty("os.name").contains("Mac OS")) {
                System.setProperty("quarkus.http.test-host",
                        kubernetesClient.services().inNamespace(TEST_NAMESPACE).withName(APPLICATION_SERVICE)
                                .get().getSpec().getClusterIP());
            }
        }
    }

    private static void deployResource(String resource) {
        // Deploy all the resources associated to the external requirements
        kubernetesClient.load(RegistryDeploymentManager.class.getResourceAsStream(resource))
                .serverSideApply();

        // Wait for all the external resources pods to be ready
        kubernetesClient.pods().inNamespace(TEST_NAMESPACE).waitUntilReady(360, TimeUnit.SECONDS);
    }

    /**
     * Waits for the Apicurio Registry to be ready by checking the REST API health endpoint.
     * Uses the external LoadBalancer service to check readiness via localhost.
     */
    private static void waitForRegistryReady() {
        LOGGER.info("Waiting for Apicurio Registry to be accessible via LoadBalancer ##################################################");

        try {
            // In minikube with tunnel, the service should be accessible via localhost
            // Let's wait a bit and then try to connect
            Thread.sleep(10000); // Give minikube tunnel time to set up the route

            // Try to connect to the Registry REST API
            String registryUrl = "http://" + System.getProperty("quarkus.http.test-host") + ":8080/health/ready";
            LOGGER.info("Checking Registry readiness at: {}", registryUrl);

            int maxAttempts = 30;
            int attempt = 0;
            boolean ready = false;

            while (attempt < maxAttempts && !ready) {
                try {
                    // Simple HTTP GET to check if service is responding
                    java.net.URL url = new java.net.URL(registryUrl);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        ready = true;
                        LOGGER.info("Apicurio Registry is ready!");
                    } else {
                        LOGGER.debug("Registry returned status code: {}", responseCode);
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    LOGGER.debug("Attempt {}/{}: Registry not ready yet: {}",
                            attempt + 1, maxAttempts, e.getMessage());
                    Thread.sleep(2000);
                }
                attempt++;
            }

            if (!ready) {
                throw new RuntimeException("Apicurio Registry did not become ready after " + maxAttempts + " attempts. " +
                        "Make sure 'minikube tunnel' is running and LoadBalancer services are accessible.");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for Registry", e);
        }
    }

    private static List<LogWatch> streamPodLogs(String container) {
        List<LogWatch> logWatchList = new ArrayList<>();

        PodList podList = kubernetesClient.pods().inNamespace(TEST_NAMESPACE).withLabel("app", container)
                .list();

        podList.getItems()
                .forEach(p -> logWatchList.add(kubernetesClient.pods().inNamespace(TEST_NAMESPACE)
                        .withName(p.getMetadata().getName()).inContainer(container).tailingLines(10)
                        .watchLog(System.out)));

        return logWatchList;
    }
}