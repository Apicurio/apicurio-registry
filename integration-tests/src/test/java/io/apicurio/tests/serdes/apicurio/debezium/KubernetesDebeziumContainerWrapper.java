package io.apicurio.tests.serdes.apicurio.debezium;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ContainerConfig;
import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.debezium.testing.testcontainers.DebeziumContainer;
import io.fabric8.kubernetes.api.model.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.OutputFrame;

import java.util.function.Consumer;

import static io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE;
import static io.apicurio.deployment.RegistryDeploymentManager.kubernetesClient;

/**
 * Wrapper for Debezium Kafka Connect that works in Kubernetes cluster mode.
 * Overrides container methods to interact with Kubernetes-deployed Debezium Connect service.
 */
public class KubernetesDebeziumContainerWrapper extends DebeziumContainer {

    private static final Logger log = LoggerFactory.getLogger(KubernetesDebeziumContainerWrapper.class);

    private final boolean isExternalService;
    private final String serviceName;
    private final String clusterIP;
    private final int port = 8083;

    public KubernetesDebeziumContainerWrapper(String serviceName) {
        super("quay.io/debezium/connect"); // Dummy image, won't be used
        this.serviceName = serviceName;

        this.isExternalService = serviceName.contains("-external");

        // Get the service ClusterIP
        Service service = kubernetesClient.services()
                .inNamespace(TEST_NAMESPACE)
                .withName(serviceName)
                .get();

        if (service != null) {
            this.clusterIP = service.getSpec().getClusterIP();
            log.info("Debezium Connect service {} found at {}", serviceName, clusterIP);
        }
        else {
            throw new RuntimeException("Debezium Connect service " + serviceName + " not found in namespace " + TEST_NAMESPACE);
        }
    }

    @Override
    public String getHost() {
        // For external LoadBalancer services, return localhost (minikube tunnel exposes them there)
        // For internal ClusterIP services, return the cluster IP
        return isExternalService ? "localhost" : clusterIP;
    }

    @Override
    public Integer getMappedPort(int originalPort) {
        // In Kubernetes, we access via ClusterIP:port directly (no port mapping)
        return port;
    }

    @Override
    public void registerConnector(String connectorName, ConnectorConfiguration configuration) {
        // Build the connector URL explicitly for Kubernetes mode
        // The parent class's HTTP client may not be properly initialized since we skip start()
        String connectUrl = "http://" + getHost() + ":" + getMappedPort(8083) + "/connectors";

        log.info("Registering connector '{}' to Debezium Connect at: {}", connectorName, connectUrl);
        log.info("Connector configuration: {}", configuration.asProperties());

        try {
            // Use the parent class method which handles the HTTP request
            super.registerConnector(connectorName, configuration);
            log.info("Successfully registered connector '{}'", connectorName);
        } catch (RuntimeException e) {
            log.error("Failed to register connector '{}' at {}. Error: {}",
                     connectorName, connectUrl, e.getMessage());
            log.error("Make sure Debezium Connect is running and accessible at {}", connectUrl);
            log.error("Check that minikube tunnel is running and LoadBalancer services are ready");
            throw e;
        }
    }

    @Override
    public void deleteConnector(String connectorName) {
        // Use Debezium's HTTP client to delete connector
        super.deleteConnector(connectorName);
    }

    @Override
    public void followOutput(Consumer<OutputFrame> consumer) {
        // Stream Kubernetes pod logs instead of Docker container logs
        log.info("Streaming Kubernetes pod logs for Debezium Connect");
        try {
            var pods = kubernetesClient.pods()
                    .inNamespace(TEST_NAMESPACE)
                    .withLabel("app", serviceName.replace("-service", ""))
                    .list();

            if (!pods.getItems().isEmpty()) {
                String podName = pods.getItems().get(0).getMetadata().getName();
                log.info("Found Debezium pod: {}", podName);

                // Stream logs to logger (simplified version - full implementation would pipe to consumer)
                kubernetesClient.pods()
                        .inNamespace(TEST_NAMESPACE)
                        .withName(podName)
                        .tailingLines(100)
                        .watchLog(System.out);
            }
            else {
                log.warn("No Debezium pods found with label app={}", serviceName.replace("-service", ""));
            }
        }
        catch (Exception e) {
            log.warn("Failed to stream Kubernetes pod logs: {}", e.getMessage());
        }
    }

    @Override
    public void start() {
        // No-op: Service already running in Kubernetes
        log.info("Skipping start() - Debezium Connect already deployed in Kubernetes");
    }

    @Override
    public void stop() {
        // No-op: Service lifecycle managed by Kubernetes
        log.info("Skipping stop() - Debezium Connect lifecycle managed by Kubernetes");
    }

    @Override
    public InspectContainerResponse getContainerInfo() {
        // Return a mock InspectContainerResponse for Kubernetes mode
        // This is needed by ConnectorConfiguration.forJdbcContainer()
        return new InspectContainerResponse() {
            @Override
            public ContainerConfig getConfig() {
                return new ContainerConfig() {
                    @Override
                    public String getHostName() {
                        return getHost();
                    }
                };
            }
        };
    }
}
