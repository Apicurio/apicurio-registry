package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.operator.it.ITBase.MEDIUM_DURATION;
import static io.apicurio.registry.operator.it.ITBase.SHORT_DURATION;
import static io.apicurio.registry.operator.it.ITBase.setDefaultAwaitilityTimings;
import static io.apicurio.registry.operator.resource.Labels.getOperatorManagedLabels;
import static io.apicurio.registry.operator.utils.K8sCell.k8sCell;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class OLMITBase {

    private static final Logger log = LoggerFactory.getLogger(OLMITBase.class);

    public static final String PROJECT_VERSION_PROP = "registry.version";
    public static final String PROJECT_ROOT_PROP = "test.operator.project-root";
    public static final String CATALOG_IMAGE_PROP = "test.operator.catalog-image";
    public static final String OML_VERSION = "test.operator.olm-version";

    protected static KubernetesClient client;
    protected static String namespace;
    protected static IngressManager ingressManager;
    protected static boolean cleanup;

    @BeforeAll
    public static void beforeAll() throws Exception {
        setDefaultAwaitilityTimings();
        namespace = ITBase.calculateNamespace();
        client = ITBase.createK8sClient(namespace);
        ITBase.createNamespace(client, namespace);
        ingressManager = new IngressManager(client, namespace);
        cleanup = ConfigProvider.getConfig().getValue(ITBase.CLEANUP, Boolean.class);

        int olmVersion = ConfigProvider.getConfig().getOptionalValue(OML_VERSION, Integer.class).orElse(0);
        if (olmVersion == 0) {

            if (client.apiextensions().v1().customResourceDefinitions().withName("catalogsources.operators.coreos.com").get() == null) {
                throw new OperatorException("CatalogSource CRD is not available. Please install OLM v0/v1.");
            }

            createResource("olmv0/catalog-source.yaml");

            await().ignoreExceptions().until(() -> {
                return client.pods().inNamespace(namespace).list().getItems().stream().filter(
                                pod -> pod.getMetadata().getName().startsWith("apicurio-registry-operator-catalog"))
                        .anyMatch(pod -> pod.getStatus().getConditions().stream()
                                .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus())));
            });

            createResource("olmv0/operator-group.yaml");
            createResource("olmv0/subscription.yaml");
        } else if (olmVersion == 1) {

            if (client.apiextensions().v1().customResourceDefinitions().withName("clusterextensions.olm.operatorframework.io").get() == null) {
                throw new OperatorException("ClusterExtension CRD is not available. Please install OLM v1.");
            }

            // CRD must only be installed by OLM v1, so we have to delete it (if it exists) before installing.
            var crd = k8sCell(client, () -> client.apiextensions().v1().customResourceDefinitions().withName(ApicurioRegistry3.EMPTY.getFullResourceName()).get());
            if (crd.getOptional().isPresent()) {
                log.warn("When using OLM v1, ApicurioRegistry3 CRD must be created when installing the bundle. Deleting the existing CRD before we can continue.");

                await().atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() -> {
                    client.resources(ApicurioRegistry3.class).list().getItems().forEach(ar -> {
                        log.warn("Deleting ApicurioRegistry3 CR: {}", ResourceID.fromResource(ar));
                        client.resource(ar).delete();
                    });
                    assertThat(client.resources(ApicurioRegistry3.class).list().getItems()).isEmpty();
                });

                await().atMost(MEDIUM_DURATION).ignoreExceptions().until(() -> {
                    log.debug("Deleting ApicurioRegistry3 CRD.");
                    crd.getOptional().ifPresent(c -> {
                        client.resource(c).delete();
                    });
                    try {
                        await().atMost(SHORT_DURATION).ignoreExceptions().until(() -> {
                            var c = crd.getOptional();
                            if (c.isPresent()) {
                                log.debug("Waiting on ApicurioRegistry3 CRD to be deleted. Terminating condition: {}", c.get().getStatus().getConditions().stream()
                                        .filter(cond -> "Terminating".equals(cond.getType())).findFirst().orElse(null));
                                return false;
                            } else {
                                return true;
                            }
                        });
                        return true;
                    } catch (Exception ex) {
                        log.debug("Could not delete ApicurioRegistry3 CRD. Trying to force the deletion by deleting the finalizer.");
                        crd.update(r -> {
                            r.getMetadata().setFinalizers(List.of());
                        });
                        return false;
                    }
                });
            }

            createResource("olmv1/cluster-catalog.yaml");

            await().ignoreExceptions().until(() -> {
                var r = client.genericKubernetesResources("olm.operatorframework.io/v1", "ClusterCatalog")
                        .inNamespace(namespace)
                        .withName("apicurio-registry-operator-catalog")
                        .get();

                return ((Collection<Map<String, Object>>) r.get("status", "conditions")).stream().anyMatch(c -> {
                    return "Serving".equals(c.get("type")) && "True".equals(c.get("status"));
                });
            });

            createResource("olmv1/service-account.yaml");
            createResource("olmv1/cluster-role.yaml");
            createResource("olmv1/cluster-role-binding.yaml");
            createResource("olmv1/cluster-extension.yaml");
        } else {
            throw new IllegalArgumentException("Unknown OLM version '" + olmVersion + "'. Expected '0' (default) or '1'.");
        }
    }

    private static void createResource(String path) throws IOException {
        loadResource(path).create();
    }

    private static void deleteResource(String path) throws IOException {
        loadResource(path).delete();
    }

    private static NamespaceableResource<? extends HasMetadata> loadResource(String path) throws IOException {
        var projectRoot = ConfigProvider.getConfig().getValue(PROJECT_ROOT_PROP, String.class);
        var testDeployDir = Paths.get(projectRoot, "operator/olm-tests/src/test/deploy");
        var resourceRaw = Files.readString(testDeployDir.resolve(path));
        return client.resource(replaceVars(resourceRaw));
    }

    private static String replaceVars(String rawResource) {
        var projectVersion = ConfigProvider.getConfig().getValue(PROJECT_VERSION_PROP, String.class);
        var catalogImage = ConfigProvider.getConfig().getValue(CATALOG_IMAGE_PROP, String.class);
        rawResource = rawResource.replace("${PLACEHOLDER_NAMESPACE}", namespace);
        rawResource = rawResource.replace("${PLACEHOLDER_CATALOG_NAMESPACE}", namespace);
        rawResource = rawResource.replace("${PLACEHOLDER_CATALOG_IMAGE}", catalogImage);
        rawResource = rawResource.replace("${PLACEHOLDER_PACKAGE_NAME}", "apicurio-registry-3");
        rawResource = rawResource.replace("${PLACEHOLDER_PACKAGE}", "apicurio-registry-3.v" + projectVersion.toLowerCase());
        rawResource = rawResource.replace("${PLACEHOLDER_VERSION}", projectVersion);
        rawResource = rawResource.replace("${PLACEHOLDER_LC_VERSION}", projectVersion.toLowerCase());
        return rawResource;
    }

    @AfterEach
    public void afterEach() {
        if (cleanup) {
            log.info("Deleting CRs");
            client.resources(ApicurioRegistry3.class).delete();
            await().untilAsserted(() -> {
                // TODO: Check if this is even used?
                var registryDeployments = client.apps().deployments().inNamespace(namespace)
                        .withLabels(getOperatorManagedLabels()).list().getItems();
                assertThat(registryDeployments.size()).isZero();
            });
        }
    }

    @AfterAll
    public static void afterAll() throws IOException {
        if (cleanup) {
            int olmVersion = ConfigProvider.getConfig().getOptionalValue(OML_VERSION, Integer.class).orElse(0);
            if (olmVersion == 0) {
                deleteResource("olmv0/subscription.yaml");
                deleteResource("olmv0/operator-group.yaml");
                deleteResource("olmv0/catalog-source.yaml");
            } else if (olmVersion == 1) {
                deleteResource("olmv1/cluster-extension.yaml");
                deleteResource("olmv1/cluster-role-binding.yaml");
                deleteResource("olmv1/cluster-role.yaml");
                deleteResource("olmv1/service-account.yaml");
                deleteResource("olmv1/cluster-catalog.yaml");
            } else {
                throw new IllegalStateException("Unreachable.");
            }
            log.info("Deleting namespace : {}", namespace);
            assertThat(client.namespaces().withName(namespace).delete()).isNotNull();
        }
        client.close();
    }
}
