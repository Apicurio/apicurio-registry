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
import org.junit.platform.launcher.TestExecutionListener;
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

    static KubernetesClient kubernetesClient;

    static List<LogWatch> logWatch;

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
            logWatch = streamPodLogs("apicurio-registry-memory");
        } else if (Boolean.parseBoolean(System.getProperty("deploySql"))) {
            LOGGER.info(
                    "Deploying SQL Registry Variant with image: {} ##################################################",
                    System.getProperty("registry-sql-image"));
            SqlDeploymentManager.deploySqlApp(System.getProperty("registry-sql-image"));
            logWatch = streamPodLogs("apicurio-registry-sql");
        } else if (Boolean.parseBoolean(System.getProperty("deployKafka"))) {
            LOGGER.info(
                    "Deploying Kafka SQL Registry Variant with image: {} ##################################################",
                    System.getProperty("registry-kafkasql-image"));
            KafkaSqlDeploymentManager.deployKafkaApp(System.getProperty("registry-kafkasql-image"));
            logWatch = streamPodLogs("apicurio-registry-kafka");
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