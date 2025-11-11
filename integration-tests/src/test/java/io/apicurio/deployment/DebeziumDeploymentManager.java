package io.apicurio.deployment;

import io.fabric8.kubernetes.api.model.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static io.apicurio.deployment.KubernetesTestResources.DEBEZIUM_CONNECT_LOCAL_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.DEBEZIUM_CONNECT_LOCAL_SERVICE;
import static io.apicurio.deployment.KubernetesTestResources.DEBEZIUM_CONNECT_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.DEBEZIUM_CONNECT_SERVICE;
import static io.apicurio.deployment.KubernetesTestResources.DEBEZIUM_MYSQL_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.DEBEZIUM_POSTGRES_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.KAFKA_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.MYSQL_DEBEZIUM_SERVICE;
import static io.apicurio.deployment.KubernetesTestResources.POSTGRESQL_DEBEZIUM_SERVICE;
import static io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE;
import static io.apicurio.deployment.RegistryDeploymentManager.kubernetesClient;

/**
 * Deployment manager for Debezium integration tests in Kubernetes.
 * Deploys Kafka, PostgreSQL, MySQL, and Debezium Kafka Connect to the test namespace.
 *
 * Supports idempotent deployment - infrastructure components are only deployed once
 * and reused across multiple test executions for improved CI performance.
 */
public class DebeziumDeploymentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebeziumDeploymentManager.class);

    // Flags to track what infrastructure has been deployed (for idempotency)
    private static volatile boolean kafkaDeployed = false;
    private static volatile boolean postgresqlDeployed = false;
    private static volatile boolean mysqlDeployed = false;
    private static volatile boolean debeziumPublishedDeployed = false;
    private static volatile boolean debeziumLocalDeployed = false;

    /**
     * Deploys the complete Debezium PostgreSQL test infrastructure to Kubernetes.
     * This includes: Kafka, PostgreSQL (with Debezium support), and Debezium Kafka Connect.
     *
     * This method is idempotent - it will skip deployment of components that are already deployed.
     *
     * @param useLocalConverters If true, uses locally-built Apicurio converters; if false, uses published converters
     * @throws IOException if deployment fails
     */
    public static synchronized void deployDebeziumInfra(boolean useLocalConverters) throws IOException {
        LOGGER.info("Deploying Debezium PostgreSQL test infrastructure (useLocalConverters={}) ##################################################", useLocalConverters);

        // Deploy Kafka (idempotent - only deploy if not already deployed)
        if (!kafkaDeployed) {
            LOGGER.info("Deploying Kafka ##################################################");
            deployResource(KAFKA_RESOURCES);
            kafkaDeployed = true;
        } else {
            LOGGER.info("Kafka already deployed, skipping ##################################################");
        }

        // Deploy PostgreSQL with Debezium support (idempotent)
        if (!postgresqlDeployed) {
            LOGGER.info("Deploying PostgreSQL with Debezium support ##################################################");
            deployResource(DEBEZIUM_POSTGRES_RESOURCES);
            postgresqlDeployed = true;
        } else {
            LOGGER.info("PostgreSQL already deployed, skipping ##################################################");
        }

        // Deploy Debezium Kafka Connect (published or local converters) - idempotent
        if (useLocalConverters) {
            if (!debeziumLocalDeployed) {
                LOGGER.info("Deploying Debezium Kafka Connect with local converters ##################################################");
                deployDebeziumWithLocalConverters();
                debeziumLocalDeployed = true;
            } else {
                LOGGER.info("Debezium with local converters already deployed, skipping ##################################################");
            }
        } else {
            if (!debeziumPublishedDeployed) {
                LOGGER.info("Deploying Debezium Kafka Connect with published converters ##################################################");
                deployResource(DEBEZIUM_CONNECT_RESOURCES);
                debeziumPublishedDeployed = true;
            } else {
                LOGGER.info("Debezium with published converters already deployed, skipping ##################################################");
            }
        }

        // Configure system properties for test access (always run to ensure properties are set)
        configureTestProperties(useLocalConverters, "postgresql");

        // Wait for Debezium Connect to be ready
        waitForDebeziumConnectReady(useLocalConverters);

        LOGGER.info("Debezium PostgreSQL infrastructure deployment complete ##################################################");
    }

    /**
     * Deploys the complete Debezium MySQL test infrastructure to Kubernetes.
     * This includes: Kafka, MySQL (with Debezium support), and Debezium Kafka Connect.
     *
     * This method is idempotent - it will skip deployment of components that are already deployed.
     *
     * @param useLocalConverters If true, uses locally-built Apicurio converters; if false, uses published converters
     * @throws IOException if deployment fails
     */
    public static synchronized void deployDebeziumMySQLInfra(boolean useLocalConverters) throws IOException {
        LOGGER.info("Deploying Debezium MySQL test infrastructure (useLocalConverters={}) ##################################################", useLocalConverters);

        // Deploy Kafka (idempotent - only deploy if not already deployed)
        if (!kafkaDeployed) {
            LOGGER.info("Deploying Kafka ##################################################");
            deployResource(KAFKA_RESOURCES);
            kafkaDeployed = true;
        } else {
            LOGGER.info("Kafka already deployed, skipping ##################################################");
        }

        // Deploy MySQL with Debezium support (idempotent)
        if (!mysqlDeployed) {
            LOGGER.info("Deploying MySQL with Debezium support ##################################################");
            deployResource(DEBEZIUM_MYSQL_RESOURCES);
            mysqlDeployed = true;
        } else {
            LOGGER.info("MySQL already deployed, skipping ##################################################");
        }

        // Deploy Debezium Kafka Connect (published or local converters) - idempotent
        if (useLocalConverters) {
            if (!debeziumLocalDeployed) {
                LOGGER.info("Deploying Debezium Kafka Connect with local converters ##################################################");
                deployDebeziumWithLocalConverters();
                debeziumLocalDeployed = true;
            } else {
                LOGGER.info("Debezium with local converters already deployed, skipping ##################################################");
            }
        } else {
            if (!debeziumPublishedDeployed) {
                LOGGER.info("Deploying Debezium Kafka Connect with published converters ##################################################");
                deployResource(DEBEZIUM_CONNECT_RESOURCES);
                debeziumPublishedDeployed = true;
            } else {
                LOGGER.info("Debezium with published converters already deployed, skipping ##################################################");
            }
        }

        // Configure system properties for test access (always run to ensure properties are set)
        configureTestProperties(useLocalConverters, "mysql");

        // Wait for Debezium Connect to be ready
        waitForDebeziumConnectReady(useLocalConverters);

        LOGGER.info("Debezium MySQL infrastructure deployment complete ##################################################");
    }

    /**
     * Deploys ALL Debezium test infrastructure to Kubernetes in one go.
     * This includes: Kafka, PostgreSQL, MySQL, and both published and local Debezium Connect instances.
     *
     * This method is designed for CI environments to deploy everything once and run all test suites
     * in a single Maven execution, significantly improving performance.
     *
     * @throws IOException if deployment fails
     */
    public static synchronized void deployAllDebeziumInfra() throws IOException {
        LOGGER.info("Deploying ALL Debezium test infrastructure (PostgreSQL + MySQL + both converter types) ##################################################");

        // Deploy shared Kafka infrastructure
        if (!kafkaDeployed) {
            LOGGER.info("Deploying Kafka ##################################################");
            deployResource(KAFKA_RESOURCES);
            kafkaDeployed = true;
        }

        // Deploy PostgreSQL
        if (!postgresqlDeployed) {
            LOGGER.info("Deploying PostgreSQL with Debezium support ##################################################");
            deployResource(DEBEZIUM_POSTGRES_RESOURCES);
            postgresqlDeployed = true;
        }

        // Deploy MySQL
        if (!mysqlDeployed) {
            LOGGER.info("Deploying MySQL with Debezium support ##################################################");
            deployResource(DEBEZIUM_MYSQL_RESOURCES);
            mysqlDeployed = true;
        }

        // Deploy Debezium Connect with published converters
        if (!debeziumPublishedDeployed) {
            LOGGER.info("Deploying Debezium Kafka Connect with published converters ##################################################");
            deployResource(DEBEZIUM_CONNECT_RESOURCES);
            debeziumPublishedDeployed = true;
        }

        // Deploy Debezium Connect with local converters
        if (!debeziumLocalDeployed) {
            LOGGER.info("Deploying Debezium Kafka Connect with local converters ##################################################");
            deployDebeziumWithLocalConverters();
            debeziumLocalDeployed = true;
        }

        // Configure system properties for both databases
        configureTestProperties(false, "postgresql");
        configureTestProperties(false, "mysql");
        configureTestProperties(true, "postgresql");  // Also configure for local converters

        // Wait for both Debezium Connect instances to be ready
        waitForDebeziumConnectReady(false);  // Published converters
        waitForDebeziumConnectReady(true);   // Local converters

        LOGGER.info("ALL Debezium infrastructure deployment complete ##################################################");
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
     * Sets bootstrap.servers, database JDBC URL, and Debezium Connect endpoint.
     * <p>
     * NOTE: Tests run on local machine and access services via minikube tunnel (localhost),
     * while Debezium Connect runs in Kubernetes and uses ClusterIP.
     */
    private static void configureTestProperties(boolean useLocalConverters, String databaseType) {
        // Get Kafka service ClusterIP and port
        Service kafkaService = kubernetesClient.services()
                .inNamespace(TEST_NAMESPACE)
                .withName("kafka-service")
                .get();


        if (kafkaService != null) {
            if (!System.getProperty("os.name").contains("Mac OS")) {
                // Linux/CI: Use ClusterIP with Pod IP listener (port 29093)
                String bootstrapServers = kafkaService.getSpec().getClusterIP() + ":29093";
                System.setProperty("bootstrap.servers", bootstrapServers);
                LOGGER.info("Kafka bootstrap servers (Linux/CI): {}", bootstrapServers);
            } else {
                // macOS: Use localhost with localhost listener (port 29092) and set cluster value
                String bootstrapServers = "localhost:29092";
                System.setProperty("bootstrap.servers", bootstrapServers);
                System.setProperty("cluster.bootstrap.servers", kafkaService.getSpec().getClusterIP() + ":29093");
                LOGGER.info("Kafka bootstrap servers (macOS): {}", bootstrapServers);
            }
        } else {
            LOGGER.error("Failed to get Kafka service");
        }

        // Configure database-specific properties
        if ("postgresql".equals(databaseType)) {
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
        } else if ("mysql".equals(databaseType)) {
            // Get MySQL service ClusterIP and port
            Service mysqlService = kubernetesClient.services()
                    .inNamespace(TEST_NAMESPACE)
                    .withName(MYSQL_DEBEZIUM_SERVICE)
                    .get();

            if (mysqlService != null) {
                String mysqlClusterIP = mysqlService.getSpec().getClusterIP();
                String mysqlJdbcUrl = "jdbc:mysql://" + mysqlClusterIP + ":3306/registry";
                System.setProperty("debezium.mysql.jdbc.url", mysqlJdbcUrl);
                System.setProperty("debezium.mysql.host", mysqlClusterIP);
                System.setProperty("debezium.mysql.port", "3306");
                System.setProperty("debezium.mysql.database", "registry");
                System.setProperty("debezium.mysql.username", "mysqluser");
                System.setProperty("debezium.mysql.password", "mysqlpw");
                LOGGER.info("MySQL JDBC URL: {}", mysqlJdbcUrl);
            } else {
                LOGGER.error("Failed to get MySQL service");
            }
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
     * Copies local converter files to the minikube node using 'minikube cp' command.
     * This is necessary because hostPath volumes reference paths inside the minikube VM,
     * not on the host machine.
     */
    private static void copyConvertersToMinikube(String localPath, String minikubePath) {
        LOGGER.info("Copying converters from {} to minikube:{}", localPath, minikubePath);

        try {
            // Create directory in minikube
            ProcessBuilder mkdirProcess = new ProcessBuilder(
                    "minikube", "ssh", "--", "mkdir", "-p", minikubePath
            );
            mkdirProcess.inheritIO();
            Process mkdir = mkdirProcess.start();
            int mkdirExitCode = mkdir.waitFor();

            if (mkdirExitCode != 0) {
                throw new RuntimeException("Failed to create directory in minikube with exit code: " + mkdirExitCode);
            }

            // Copy each file to minikube
            java.io.File localDir = new java.io.File(localPath);
            java.io.File[] files = localDir.listFiles();

            if (files != null) {
                for (java.io.File file : files) {
                    if (file.isFile()) {
                        LOGGER.info("Copying {} to minikube...", file.getName());

                        ProcessBuilder cpProcess = new ProcessBuilder(
                                "minikube", "cp", file.getAbsolutePath(), minikubePath + "/" + file.getName()
                        );
                        cpProcess.inheritIO();
                        Process cp = cpProcess.start();
                        int cpExitCode = cp.waitFor();

                        if (cpExitCode != 0) {
                            throw new RuntimeException("Failed to copy file " + file.getName() + " to minikube with exit code: " + cpExitCode);
                        }
                    }
                }
            }

            LOGGER.info("Successfully copied all converter files to minikube");

            // Verify the files are there
            ProcessBuilder lsProcess = new ProcessBuilder(
                    "minikube", "ssh", "--", "ls", "-lh", minikubePath
            );
            lsProcess.inheritIO();
            lsProcess.start().waitFor();

        } catch (Exception e) {
            LOGGER.error("Failed to copy converters to minikube", e);
            throw new RuntimeException("Failed to copy converters to minikube", e);
        }
    }

    /**
     * Deploys Debezium Connect with local converters using an InitContainer approach.
     * The manifest uses a hostPath volume to access converters from the host filesystem,
     * and an InitContainer copies them to an emptyDir before Kafka Connect starts.
     * <p>
     * This ensures converters are available when Kafka Connect scans for plugins.
     */
    private static void deployDebeziumWithLocalConverters() {
        LOGGER.info("Deploying Debezium Connect with local converters using InitContainer ##################################################");

        // Determine the path to local converters
        String projectDir = System.getProperty("user.dir");
        String convertersPath = projectDir + "/target/debezium-converters";
        java.io.File convertersDir = new java.io.File(convertersPath);

        if (!convertersDir.exists() || !convertersDir.isDirectory()) {
            String errorMsg = "Local converters not found at: " + convertersPath +
                    ". Please run 'mvn clean install -DskipTests' to build the converters.";
            LOGGER.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        java.io.File[] files = convertersDir.listFiles();
        if (files == null || files.length == 0) {
            String errorMsg = "Local converters directory exists but is empty: " + convertersPath;
            LOGGER.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        LOGGER.info("Found {} converter files in: {}", files.length, convertersPath);

        // Copy converters to minikube node since hostPath refers to paths inside minikube VM
        String minikubeConvertersPath = "/tmp/debezium-converters";
        copyConvertersToMinikube(convertersPath, minikubeConvertersPath);

        try {
            // Load the manifest template
            java.io.InputStream resourceStream = DebeziumDeploymentManager.class.getResourceAsStream(DEBEZIUM_CONNECT_LOCAL_RESOURCES);
            if (resourceStream == null) {
                throw new IllegalStateException("Could not load resource: " + DEBEZIUM_CONNECT_LOCAL_RESOURCES);
            }

            String manifestContent = new String(resourceStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

            // Replace the placeholder with the minikube path
            String modifiedManifest = manifestContent.replace("CONVERTERS_HOST_PATH_PLACEHOLDER", minikubeConvertersPath);

            LOGGER.info("Deploying Debezium Connect with hostPath (inside minikube): {}", minikubeConvertersPath);

            // Deploy the modified manifest
            kubernetesClient.load(new java.io.ByteArrayInputStream(
                    modifiedManifest.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )).serverSideApply();

            // Wait for all pods to be ready (including initContainer completion)
            LOGGER.info("Waiting for Debezium Connect pod to be ready (including initContainer)...");
            kubernetesClient.pods().inNamespace(TEST_NAMESPACE).waitUntilReady(360, java.util.concurrent.TimeUnit.SECONDS);

            LOGGER.info("Debezium Connect with local converters deployed successfully");

        } catch (Exception e) {
            LOGGER.error("Failed to deploy Debezium Connect with local converters", e);
            throw new RuntimeException("Failed to deploy Debezium with local converters", e);
        }
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

            String port = useLocalConverters ? "8084" : "8083";
            // Try to connect to the Debezium Connect REST API
            String connectUrl = "http://localhost:" + port;
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
                    Thread.sleep(5000);
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
