package io.apicurio.tests.serdes.apicurio.debezium;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ContainerConfig;
import io.fabric8.kubernetes.api.model.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE;
import static io.apicurio.deployment.RegistryDeploymentManager.kubernetesClient;

/**
 * Wrapper for MySQL that works in Kubernetes cluster mode.
 * Overrides container methods to return Kubernetes service connection details.
 */
public class KubernetesMySQLContainerWrapper extends MySQLContainer<KubernetesMySQLContainerWrapper> {

    private static final Logger log = LoggerFactory.getLogger(KubernetesMySQLContainerWrapper.class);

    private final String clusterIP;
    private final boolean isExternalService;
    private final int port = 3306;
    private final String database = "registry";
    private final String username = "mysqluser";
    private final String password = "mysqlpw";

    public KubernetesMySQLContainerWrapper(String serviceName) {
        super(DockerImageName.parse("quay.io/debezium/example-mysql:2.5")
                .asCompatibleSubstituteFor("mysql")); // Dummy image, won't be used

        this.isExternalService = serviceName.contains("-external");

        // Get the service (either ClusterIP or LoadBalancer)
        Service service = kubernetesClient.services()
                .inNamespace(TEST_NAMESPACE)
                .withName(serviceName)
                .get();

        if (service != null) {
            this.clusterIP = service.getSpec().getClusterIP();
            String serviceType = service.getSpec().getType();
            log.info("MySQL service {} (type: {}) found at {}", serviceName, serviceType, clusterIP);

            if (isExternalService) {
                log.info("Using external LoadBalancer service - will connect via localhost with minikube tunnel");
            }
        }
        else {
            throw new RuntimeException("MySQL service " + serviceName + " not found in namespace " + TEST_NAMESPACE);
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

        log.debug("Using ClusterIP {} for MySQL access", clusterIP);
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
        return "jdbc:mysql://" + getHost() + ":" + port + "/" + database;
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
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    public void start() {
        // No-op: Service already running in Kubernetes
        log.info("Skipping start() - MySQL already deployed in Kubernetes");
    }

    @Override
    public void stop() {
        // No-op: Service lifecycle managed by Kubernetes
        log.info("Skipping stop() - MySQL lifecycle managed by Kubernetes");
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
                                "MYSQL_DATABASE=" + database,
                                "MYSQL_USER=" + username,
                                "MYSQL_PASSWORD=" + password,
                                "MYSQL_ROOT_PASSWORD=debezium"
                        };
                    }
                };
            }
        };
    }
}
