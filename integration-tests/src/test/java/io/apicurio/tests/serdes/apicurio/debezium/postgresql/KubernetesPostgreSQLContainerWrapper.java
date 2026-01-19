package io.apicurio.tests.serdes.apicurio.debezium.postgresql;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ContainerConfig;
import io.fabric8.kubernetes.api.model.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE;
import static io.apicurio.deployment.RegistryDeploymentManager.kubernetesClient;

/**
 * Wrapper for PostgreSQL that works in Kubernetes cluster mode.
 * Overrides container methods to return Kubernetes service connection details.
 */
public class KubernetesPostgreSQLContainerWrapper extends PostgreSQLContainer<KubernetesPostgreSQLContainerWrapper> {

    private static final Logger log = LoggerFactory.getLogger(KubernetesPostgreSQLContainerWrapper.class);

    private final String clusterIP;
    private final boolean isExternalService;
    private final int port = 5432;
    private final String database = "registry";
    private final String username = "postgres";
    private final String password = "postgres";

    public KubernetesPostgreSQLContainerWrapper(String serviceName) {
        super(DockerImageName.parse("quay.io/debezium/postgres:15")
                .asCompatibleSubstituteFor("postgres")); // Dummy image, won't be used

        this.isExternalService = serviceName.contains("-external");

        // Get the service (either ClusterIP or LoadBalancer)
        Service service = kubernetesClient.services()
                .inNamespace(TEST_NAMESPACE)
                .withName(serviceName)
                .get();

        if (service != null) {
            this.clusterIP = service.getSpec().getClusterIP();
            String serviceType = service.getSpec().getType();
            log.info("PostgreSQL service {} (type: {}) found at {}", serviceName, serviceType, clusterIP);

            if (isExternalService) {
                log.info("Using external LoadBalancer service - will connect via localhost with minikube tunnel");
            }
        }
        else {
            throw new RuntimeException("PostgreSQL service " + serviceName + " not found in namespace " + TEST_NAMESPACE);
        }
    }

    @Override
    public String getHost() {
        // On Mac with minikube tunnel: external services accessible via localhost
        // On Linux/CI: use ClusterIP even for external services (tunnel doesn't expose on localhost)
        // Internal ClusterIP services: always use ClusterIP

        if (isExternalService && isMacOS()) {
            log.debug("Using localhost for external service on macOS (minikube tunnel)");
            return "localhost";
        }

        log.debug("Using ClusterIP {} for PostgreSQL access", clusterIP);
        return clusterIP;
    }

    /**
     * Checks if running on macOS.
     * On Mac, minikube tunnel exposes LoadBalancer services on localhost.
     * On Linux/CI, services must be accessed via ClusterIP.
     */
    private boolean isMacOS() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return osName.contains("mac os");
    }

    @Override
    public Integer getMappedPort(int originalPort) {
        // In Kubernetes, we access via ClusterIP:port directly (no port mapping)
        return port;
    }

    @Override
    public String getJdbcUrl() {
        return "jdbc:postgresql://" + getHost() + ":" + port + "/" + database;
    }

    @Override
    public String getDatabaseName() {
        return database;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getDriverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    public void start() {
        // No-op: Service already running in Kubernetes
        log.info("Skipping start() - PostgreSQL already deployed in Kubernetes");
    }

    @Override
    public void stop() {
        // No-op: Service lifecycle managed by Kubernetes
        log.info("Skipping stop() - PostgreSQL lifecycle managed by Kubernetes");
    }

    @Override
    public InspectContainerResponse getContainerInfo() {
        // Return a mock InspectContainerResponse for Kubernetes mode
        // This is needed by ConnectorConfiguration.forJdbcContainer()
        // IMPORTANT: The connector runs INSIDE Kubernetes and must use ClusterIP, not localhost
        return new InspectContainerResponse() {
            @Override
            public ContainerConfig getConfig() {
                return new ContainerConfig() {
                    @Override
                    public String getHostName() {
                        // Connector needs ClusterIP for in-cluster communication
                        // Tests use getJdbcUrl() which uses getHost() (localhost)
                        return clusterIP;
                    }

                    @Override
                    public String[] getEnv() {
                        return new String[]{
                                "POSTGRES_DB=" + database,
                                "POSTGRES_USER=" + username,
                                "POSTGRES_PASSWORD=" + password
                        };
                    }
                };
            }
        };
    }
}
