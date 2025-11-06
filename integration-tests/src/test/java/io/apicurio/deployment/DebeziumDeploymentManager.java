package io.apicurio.deployment;

import io.fabric8.kubernetes.api.model.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static io.apicurio.deployment.KubernetesTestResources.*;
import static io.apicurio.deployment.RegistryDeploymentManager.kubernetesClient;

/**
 * Deployment manager for Debezium integration tests in Kubernetes.
 * Deploys Kafka, PostgreSQL, and Debezium Kafka Connect to the test namespace.
 */
public class DebeziumDeploymentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebeziumDeploymentManager.class);

    /**
     * Deploys the complete Debezium test infrastructure to Kubernetes.
     * This includes: Kafka, PostgreSQL (with Debezium support), and Debezium Kafka Connect.
     *
     * @param useLocalConverters If true, uses locally-built Apicurio converters; if false, uses published converters
     * @throws IOException if deployment fails
     */
    public static void deployDebeziumInfra(boolean useLocalConverters) throws IOException {
        LOGGER.info("Deploying Debezium test infrastructure (useLocalConverters={}) ##################################################", useLocalConverters);

        // Deploy Kafka (reuse existing Kafka resources)
        LOGGER.info("Deploying Kafka ##################################################");
        deployResource(KAFKA_RESOURCES);

        // Deploy PostgreSQL with Debezium support
        LOGGER.info("Deploying PostgreSQL with Debezium support ##################################################");
        deployResource(DEBEZIUM_POSTGRES_RESOURCES);

        // Deploy Debezium Kafka Connect (published or local converters)
        if (useLocalConverters) {
            LOGGER.info("Deploying Debezium Kafka Connect with local converters ##################################################");
            LOGGER.warn("NOTE: Local converters in Kubernetes mode require additional setup. " +
                       "Consider using published converters for cluster tests or Testcontainers for local development.");
            deployResource(DEBEZIUM_CONNECT_LOCAL_RESOURCES);
        } else {
            LOGGER.info("Deploying Debezium Kafka Connect with published converters ##################################################");
            deployResource(DEBEZIUM_CONNECT_RESOURCES);
        }

        // Configure system properties for test access
        configureTestProperties(useLocalConverters);

        // Wait for Debezium Connect to be ready
        waitForDebeziumConnectReady(useLocalConverters);

        LOGGER.info("Debezium infrastructure deployment complete ##################################################");
    }

    /**
     * Deploys a Kubernetes resource from the classpath and waits for pods to be ready.
     */
    private static void deployResource(String resourcePath) {
        kubernetesClient.load(DebeziumDeploymentManager.class.getResourceAsStream(resourcePath))
                .serverSideApply();

        // Wait for all pods to be ready (6 minute timeout)
        kubernetesClient.pods().inNamespace(TEST_NAMESPACE).waitUntilReady(360, java.util.concurrent.TimeUnit.SECONDS);
    }

    /**
     * Configures system properties so tests can access the deployed services.
     * Sets bootstrap.servers, postgres JDBC URL, and Debezium Connect endpoint.
     *
     * NOTE: Tests run on local machine and access services via minikube tunnel (localhost),
     *       while Debezium Connect runs in Kubernetes and uses ClusterIP.
     */
    private static void configureTestProperties(boolean useLocalConverters) {
        // Get Kafka service ClusterIP and port
        Service kafkaService = kubernetesClient.services()
                .inNamespace(TEST_NAMESPACE)
                .withName("kafka-service")
                .get();

        if (kafkaService != null) {
            String bootstrapServers = "localhost:29092";
            System.setProperty("bootstrap.servers", bootstrapServers);
            LOGGER.info("Kafka bootstrap servers: {}", bootstrapServers);
        } else {
            LOGGER.error("Failed to get Kafka service");
        }

        // Get PostgreSQL service ClusterIP and port
        Service postgresService = kubernetesClient.services()
                .inNamespace(TEST_NAMESPACE)
                .withName(POSTGRESQL_DEBEZIUM_SERVICE)
                .get();

        if (postgresService != null) {
            String postgresClusterIP = postgresService.getSpec().getClusterIP();
            String postgresJdbcUrl = "jdbc:postgresql://" + postgresClusterIP + ":5432/registry";
            System.setProperty("debezium.postgres.jdbc.url", postgresJdbcUrl);
            System.setProperty("debezium.postgres.host", postgresClusterIP);
            System.setProperty("debezium.postgres.port", "5432");
            System.setProperty("debezium.postgres.database", "registry");
            System.setProperty("debezium.postgres.username", "postgres");
            System.setProperty("debezium.postgres.password", "postgres");
            LOGGER.info("PostgreSQL JDBC URL: {}", postgresJdbcUrl);
        } else {
            LOGGER.error("Failed to get PostgreSQL service");
        }

        // Get Debezium Connect service ClusterIP and port
        String debeziumServiceName = useLocalConverters ? DEBEZIUM_CONNECT_LOCAL_SERVICE : DEBEZIUM_CONNECT_SERVICE;
        Service debeziumService = kubernetesClient.services()
                .inNamespace(TEST_NAMESPACE)
                .withName(debeziumServiceName)
                .get();

        if (debeziumService != null) {
            String debeziumClusterIP = debeziumService.getSpec().getClusterIP();
            String debeziumConnectUrl = "http://" + debeziumClusterIP + ":8083";
            System.setProperty("debezium.connect.url", debeziumConnectUrl);
            LOGGER.info("Debezium Connect URL: {}", debeziumConnectUrl);
        } else {
            LOGGER.error("Failed to get Debezium Connect service");
        }

        // Set flag indicating we're in cluster mode
        System.setProperty("debezium.cluster.mode", "true");
    }

    /**
     * Waits for Debezium Connect to be ready by checking the REST API health endpoint.
     * Uses the external LoadBalancer service to check readiness via localhost.
     */
    private static void waitForDebeziumConnectReady(boolean useLocalConverters) {
        String serviceName = useLocalConverters ? DEBEZIUM_CONNECT_LOCAL_SERVICE : DEBEZIUM_CONNECT_SERVICE;
        String externalServiceName = useLocalConverters ?
            "debezium-connect-local-service-external" : "debezium-connect-service-external";

        LOGGER.info("Waiting for Debezium Connect service {} to be ready ##################################################", serviceName);

        // Get the LoadBalancer service
        Service service = kubernetesClient.services()
                .inNamespace(TEST_NAMESPACE)
                .withName(externalServiceName)
                .get();

        if (service == null) {
            LOGGER.error("Debezium Connect LoadBalancer service {} not found", externalServiceName);
            throw new RuntimeException("Debezium Connect service " + externalServiceName + " not found");
        }

        // Wait for LoadBalancer to get an external IP (minikube tunnel assigns it)
        LOGGER.info("Waiting for LoadBalancer external IP to be assigned (requires minikube tunnel)...");
        try {
            // In minikube with tunnel, the service should be accessible via localhost
            // Let's wait a bit and then try to connect
            Thread.sleep(10000); // Give minikube tunnel time to set up the route

            // Try to connect to the Debezium Connect REST API
            String connectUrl = "http://localhost:8083";
            LOGGER.info("Checking Debezium Connect readiness at: {}", connectUrl);

            int maxAttempts = 30;
            int attempt = 0;
            boolean ready = false;

            while (attempt < maxAttempts && !ready) {
                try {
                    // Simple HTTP GET to check if service is responding
                    java.net.URL url = new java.net.URL(connectUrl);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        ready = true;
                        LOGGER.info("Debezium Connect is ready!");
                    } else {
                        LOGGER.debug("Debezium Connect returned status code: {}", responseCode);
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    LOGGER.debug("Attempt {}/{}: Debezium Connect not ready yet: {}",
                                attempt + 1, maxAttempts, e.getMessage());
                    Thread.sleep(2000);
                }
                attempt++;
            }

            if (!ready) {
                throw new RuntimeException("Debezium Connect did not become ready after " + maxAttempts + " attempts. " +
                                         "Make sure 'minikube tunnel' is running and LoadBalancer services are accessible.");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for Debezium Connect", e);
        }
    }
}
