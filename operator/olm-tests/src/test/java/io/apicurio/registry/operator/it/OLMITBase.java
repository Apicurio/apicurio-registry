package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.Constants;
import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;

public abstract class OLMITBase {

    private static final Logger log = LoggerFactory.getLogger(OLMITBase.class);

    public static final String PROJECT_VERSION_PROP = "registry.version";
    public static final String PROJECT_ROOT_PROP = "test.operator.project-root";
    public static final String CATALOG_IMAGE_PROP = "test.operator.catalog-image";

    protected static KubernetesClient client;
    protected static String namespace;
    protected static IngressManager ingressManager;
    protected static boolean cleanup;

    @BeforeAll
    public static void beforeAll() throws Exception {
        ITBase.setDefaultAwaitilityTimings();
        namespace = ITBase.calculateNamespace();
        client = ITBase.createK8sClient(namespace);
        ITBase.createNamespace(client, namespace);
        ingressManager = new IngressManager(client, namespace);
        cleanup = ConfigProvider.getConfig().getValue(ITBase.CLEANUP, Boolean.class);

        if (client.apiextensions().v1().customResourceDefinitions()
                    .withName("catalogsources.operators.coreos.com").get() == null) {
            throw new OperatorException("CatalogSource CRD is not available. Please install OLM.");
        }

        var projectVersion = ConfigProvider.getConfig().getValue(PROJECT_VERSION_PROP, String.class);
        var projectRoot = ConfigProvider.getConfig().getValue(PROJECT_ROOT_PROP, String.class);
        var catalogImage = ConfigProvider.getConfig().getValue(CATALOG_IMAGE_PROP, String.class);

        var testDeployDir = Paths.get(projectRoot, "operator/olm-tests/src/test/deploy");

        // Catalog Source

        var catalogSourceRaw = Files.readString(testDeployDir.resolve("catalog/catalog-source.yaml"));
        catalogSourceRaw = catalogSourceRaw.replace("${PLACEHOLDER_CATALOG_NAMESPACE}", namespace);
        catalogSourceRaw = catalogSourceRaw.replace("${PLACEHOLDER_CATALOG_IMAGE}", catalogImage);
        var catalogSource = client.resource(catalogSourceRaw);

        catalogSource.create();

        Awaitility.await().ignoreExceptions().until(() -> {
            return client.pods().inNamespace(namespace).list().getItems().stream().filter(
                            pod -> pod.getMetadata().getName().startsWith("apicurio-registry-operator-catalog"))
                    .anyMatch(pod -> pod.getStatus().getConditions().stream()
                            .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus())));
        });

        // Operator Group to allow deploying of the operator to the test namespace

        var operatorGroupRaw = Files.readString(testDeployDir.resolve("catalog/operator-group.yaml"));
        operatorGroupRaw = operatorGroupRaw.replace("${PLACEHOLDER_NAMESPACE}", namespace);
        var operatorGroup = client.resource(operatorGroupRaw);
        operatorGroup.create();

        // Subscription

        var subscriptionRaw = Files.readString(testDeployDir.resolve("catalog/subscription.yaml"));
        subscriptionRaw = subscriptionRaw.replace("${PLACEHOLDER_NAMESPACE}", namespace);
        subscriptionRaw = subscriptionRaw.replace("${PLACEHOLDER_CATALOG_NAMESPACE}", namespace);
        subscriptionRaw = subscriptionRaw.replace("${PLACEHOLDER_PACKAGE_NAME}", "apicurio-registry-3");
        subscriptionRaw = subscriptionRaw.replace("${PLACEHOLDER_PACKAGE}",
                "apicurio-registry-3.v" + projectVersion.toLowerCase());
        var subscription = client.resource(subscriptionRaw);
        subscription.create();
    }

    @AfterEach
    public void cleanup() {
        if (cleanup) {
            log.info("Deleting CRs");
            client.resources(ApicurioRegistry3.class).delete();
            Awaitility.await().untilAsserted(() -> {
                var registryDeployments = client.apps().deployments().inNamespace(namespace)
                        .withLabels(Constants.BASIC_LABELS).list().getItems();
                Assertions.assertThat(registryDeployments.size()).isZero();
            });
        }
    }

    @AfterAll
    public static void afterAll() {
        if (cleanup) {
            log.info("Deleting namespace : {}", namespace);
            Assertions.assertThat(client.namespaces().withName(namespace).delete()).isNotNull();
        }
        client.close();
    }
}
