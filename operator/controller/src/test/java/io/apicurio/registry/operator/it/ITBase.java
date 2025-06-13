package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.Constants;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.testutils.Utils;
import io.apicurio.registry.utils.Cell;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.Updatable;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.TypeLiteral;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static io.apicurio.registry.operator.resource.Labels.getOperatorSelectorLabels;
import static io.apicurio.registry.operator.testutils.Utils.withRetries;
import static io.apicurio.registry.utils.Cell.cell;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class ITBase {

    private static final Logger log = LoggerFactory.getLogger(ITBase.class);

    public static final String DEPLOYMENT_TARGET = "test.operator.deployment-target";
    public static final String OPERATOR_DEPLOYMENT_PROP = "test.operator.deployment";
    public static final String INGRESS_HOST_PROP = "test.operator.ingress-host";
    public static final String INGRESS_SKIP_PROP = "test.operator.ingress-skip";
    public static final String CLEANUP = "test.operator.cleanup";
    public static final String CRD_FILE = "../model/target/classes/META-INF/fabric8/apicurioregistries3.registry.apicur.io-v1.yml";
    public static final String REMOTE_TESTS_INSTALL_FILE = "test.operator.install-file";

    public static final Duration POLL_INTERVAL_DURATION = ofSeconds(5);

    public static final Duration SHORT_DURATION = ofSeconds(30);
    public static final Duration MEDIUM_DURATION = ofSeconds(60);
    public static final Duration LONG_DURATION = ofSeconds(5 * 60);

    public static final int RETRIES = 3;

    public enum OperatorDeployment {
        local, remote
    }

    protected static final KubernetesSerialization serialization = new KubernetesSerialization();

    protected static OperatorDeployment operatorDeployment;
    protected static KubernetesClient client;
    protected static PodLogManager podLogManager;
    protected static PortForwardManager portForwardManager;
    protected static IngressManager ingressManager;
    protected static String deploymentTarget;
    protected static String namespace;
    protected static boolean cleanup;

    protected static boolean strimziInstalled = false;
    private static Operator operator;
    protected static JobManager jobManager;
    protected static HostAliasManager hostAliasManager;

    @BeforeAll
    public static void beforeAllBase() throws Exception {
        operatorDeployment = ConfigProvider.getConfig().getValue(OPERATOR_DEPLOYMENT_PROP,
                OperatorDeployment.class);
        deploymentTarget = ConfigProvider.getConfig().getValue(DEPLOYMENT_TARGET, String.class);
        cleanup = ConfigProvider.getConfig().getValue(CLEANUP, Boolean.class);

        setDefaultAwaitilityTimings();
        namespace = calculateNamespace();
        client = createK8sClient(namespace);
        createCRDs();
        createNamespace(client, namespace);

        portForwardManager = new PortForwardManager(client, namespace);
        ingressManager = new IngressManager(client, namespace);
        podLogManager = new PodLogManager(client);
        hostAliasManager = new HostAliasManager(client);
        jobManager = new JobManager(client, hostAliasManager);

        if (operatorDeployment == OperatorDeployment.remote) {
            createTestResources();
            startOperatorLogs();
        } else {
            startLocalOperator();
        }
    }

    @BeforeEach
    public void beforeEach(TestInfo testInfo) {
        String testClassName = testInfo.getTestClass().map(c -> c.getSimpleName() + ".").orElse("");
        log.info("""

                        ------- STARTING: {}{}
                        ------- Namespace: {}
                        ------- Mode: {}
                        ------- Deployment target: {}""",
                testClassName, testInfo.getDisplayName(),
                namespace,
                ((operatorDeployment == OperatorDeployment.remote) ? "remote" : "local"),
                deploymentTarget);
    }

    protected static void checkDeploymentExists(ApicurioRegistry3 primary, String component, int replicas) {
        await().atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(client.apps().deployments()
                    .withName(primary.getMetadata().getName() + "-" + component + "-deployment").get()
                    .getStatus().getReadyReplicas()).isEqualTo(replicas);
        });
    }

    protected static void checkDeploymentDoesNotExist(ApicurioRegistry3 primary, String component) {
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(client.apps().deployments()
                    .withName(primary.getMetadata().getName() + "-" + component + "-deployment").get())
                    .isNull();
        });
    }

    protected static void checkServiceExists(ApicurioRegistry3 primary, String component) {
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(client.services()
                    .withName(primary.getMetadata().getName() + "-" + component + "-service").get())
                    .isNotNull();
        });
    }

    protected static void checkServiceDoesNotExist(ApicurioRegistry3 primary, String component) {
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(client.services()
                    .withName(primary.getMetadata().getName() + "-" + component + "-service").get()).isNull();
        });
    }

    protected static void checkIngressExists(ApicurioRegistry3 primary, String component) {
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(client.network().v1().ingresses()
                    .withName(primary.getMetadata().getName() + "-" + component + "-ingress").get())
                    .isNotNull();
        });
    }

    protected static void checkIngressDoesNotExist(ApicurioRegistry3 primary, String component) {
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(client.network().v1().ingresses()
                    .withName(primary.getMetadata().getName() + "-" + component + "-ingress").get()).isNull();
        });
    }

    /**
     * Update the Kubernetes resource, and retry if the update fails because the object has been modified on the server.
     *
     * @param resource Resource to be updated.
     * @param updater  Reentrant function that updates the resource in-place.
     * @return The resource after it has been updated.
     */
    protected static <T extends HasMetadata> T updateWithRetries(T resource, Consumer<T> updater) {
        var rval = cell(resource);
        Utils.updateWithRetries(() -> {
            var r = rval.get();
            r = client.resource(r).get();
            updater.accept(r);
            r = client.resource(r).update();
            rval.set(r);
        });
        return rval.get();
    }

    protected static <T extends HasMetadata> T createOrReplaceWithRetries(T resource) {
        Cell<T> rval = cell();
        Utils.updateWithRetries(() -> {
            rval.set(client.resource(resource).createOr(Updatable::update));
        });
        return rval.get();
    }

    protected static PodDisruptionBudget checkPodDisruptionBudgetExists(ApicurioRegistry3 primary,
                                                                        String component) {
        final Cell<PodDisruptionBudget> rval = cell();
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            PodDisruptionBudget pdb = client.policy().v1().podDisruptionBudget()
                    .withName(primary.getMetadata().getName() + "-" + component + "-poddisruptionbudget")
                    .get();
            assertThat(pdb).isNotNull();
            rval.set(pdb);
        });

        return rval.get();
    }

    protected static NetworkPolicy checkNetworkPolicyExists(ApicurioRegistry3 primary, String component) {
        final Cell<NetworkPolicy> rval = cell();
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            NetworkPolicy networkPolicy = client.network().v1().networkPolicies()
                    .withName(primary.getMetadata().getName() + "-" + component + "-networkpolicy").get();
            assertThat(networkPolicy).isNotNull();
            rval.set(networkPolicy);
        });

        return rval.get();
    }

    static KubernetesClient createK8sClient(String namespace) {
        return new KubernetesClientBuilder()
                .withConfig(new ConfigBuilder(Config.autoConfigure(null)).withNamespace(namespace).build())
                .build();
    }

    private static List<HasMetadata> loadTestResources() throws IOException {
        var installFilePath = Path
                .of(ConfigProvider.getConfig().getValue(REMOTE_TESTS_INSTALL_FILE, String.class));
        var installFileRaw = Files.readString(installFilePath);
        // We're not editing the deserialized resources to replicate the user experience
        installFileRaw = installFileRaw.replace("PLACEHOLDER_NAMESPACE", namespace);
        return serialization.unmarshal(installFileRaw);
    }

    private static void createTestResources() throws Exception {
        log.info("Creating generated resources into Namespace {}", namespace);
        loadTestResources().forEach(r -> {
            if ("minikube".equals(deploymentTarget) && r instanceof Deployment d) {
                // See https://stackoverflow.com/a/46101923
                d.getSpec().getTemplate().getSpec().getContainers()
                        .forEach(c -> c.setImagePullPolicy("IfNotPresent"));
            }
            createOrReplaceWithRetries(r);
        });
    }

    private static void startOperatorLogs() {
        List<Pod> operatorPods = new ArrayList<>();
        await().atMost(SHORT_DURATION).untilAsserted(() -> {
            operatorPods.clear();
            operatorPods.addAll(client.pods().withLabels(getOperatorSelectorLabels()).list().getItems());
            assertThat(operatorPods).hasSize(1);
        });
        podLogManager.startPodLog(ResourceID.fromResource(operatorPods.get(0)));
    }

    private static void cleanTestResources() throws Exception {
        if (cleanup) {
            log.info("Deleting generated resources from Namespace {}", namespace);
            loadTestResources().forEach(r -> {
                client.resource(r).delete();
            });
        }
    }

    private static void createCRDs() {
        withRetries(() -> {
            var crd = serialization.unmarshal(new FileInputStream(CRD_FILE), CustomResourceDefinition.class);
            if (client.resource(crd).get() == null) {
                log.info("Creating CRD");
                client.resource(crd).create();
            }
            return null;
        }, RETRIES);
    }

    private static void startLocalOperator() {
        operator = new Operator(configurationServiceOverrider -> {
            configurationServiceOverrider.withKubernetesClient(client);
        });
        log.info("Registering reconcilers for operator : {} [{}]", operator, operatorDeployment);
        // @formatter:off
        for (Reconciler<?> reconciler : CDI.current().select(new TypeLiteral<Reconciler<?>>() {})) {
            // @formatter:on
            log.info("Registering reconciler {}", reconciler.getClass().getName());
            operator.register(reconciler);
        }
        operator.start();
    }

    static void applyStrimziResources() throws Exception {
        // TODO: IMPORTANT: Strimzi >0.45 only supports Kraft-based Kafka clusters. Migration needed.
        // var strimziClusterOperatorURL = new URL("https://strimzi.io/install/latest");
        var strimziClusterOperatorURL = new URI("https://github.com/strimzi/strimzi-kafka-operator/releases/download/0.45.0/strimzi-cluster-operator-0.45.0.yaml").toURL();
        try (BufferedInputStream in = new BufferedInputStream(strimziClusterOperatorURL.openStream())) {
            List<HasMetadata> resources = serialization.unmarshal(in);
            resources.forEach(r -> {
                if (r.getKind().equals("ClusterRoleBinding") && r instanceof ClusterRoleBinding) {
                    var crb = (ClusterRoleBinding) r;
                    crb.getSubjects().forEach(s -> s.setNamespace(namespace));
                } else if (r.getKind().equals("RoleBinding") && r instanceof RoleBinding) {
                    var crb = (RoleBinding) r;
                    crb.getSubjects().forEach(s -> s.setNamespace(namespace));
                }
                log.info("Creating Strimzi resource {} {}/{}", r.getKind(), namespace, r.getMetadata().getName());
                createOrReplaceWithRetries(r);
                await().atMost(MEDIUM_DURATION.multipliedBy(2)).untilAsserted(() -> {
                    assertThat(client.resource(r).get()).isNotNull();
                });
            });
        }
    }

    static void createNamespace(KubernetesClient client, String namespace) {
        log.info("Creating Namespace {}", namespace);
        client.resource(
                        new NamespaceBuilder().withNewMetadata().addToLabels("app", "apicurio-registry-operator-test")
                                .withName(namespace).endMetadata().build())
                .create();
    }

    static String calculateNamespace() {
        return "test-" + UUID.randomUUID().toString().substring(0, 7);
    }

    static void setDefaultAwaitilityTimings() {
        Awaitility.setDefaultPollInterval(POLL_INTERVAL_DURATION);
        Awaitility.setDefaultTimeout(LONG_DURATION);
    }

    static void createResources(List<HasMetadata> resources) {
        resources.forEach(r -> {
            log.info("Creating {} {}/{}", r.getKind(), namespace, r.getMetadata().getName());
            withRetries(() -> {
                client.resource(r).createOr(Updatable::update);
                await().atMost(SHORT_DURATION).untilAsserted(() -> {
                    assertThat(client.resource(r).get()).isNotNull();
                });
            }, RETRIES);
        });
    }

    @AfterEach
    public void afterEach() {
        if (cleanup) {
            log.info("Deleting CRs");
            client.resources(ApicurioRegistry3.class).delete();
            await().untilAsserted(() -> {
                var registryDeployments = client.apps().deployments().withLabels(Constants.BASIC_LABELS).list().getItems();
                assertThat(registryDeployments.size()).isZero();
            });
        }
    }

    @AfterAll
    public static void afterAll() throws Exception {
        podLogManager.stopAndWait();
        portForwardManager.stop();
        if (operatorDeployment == OperatorDeployment.local) {
            log.info("Stopping Operator");
            operator.stop();
        } else {
            cleanTestResources();
        }
        if (cleanup) {
            log.info("Deleting namespace {}", namespace);
            // Local operator closes the client.
            try (var client = createK8sClient(namespace)) {
                client.namespaces().withName(namespace).delete();
            }
        }
        client.close();
    }
}
