package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.App;
import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.Labels;
import io.apicurio.registry.utils.Cell;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import jakarta.enterprise.inject.spi.CDI;
import org.awaitility.Awaitility;
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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.apicurio.registry.operator.resource.Labels.getOperatorManagedLabels;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;
import static io.apicurio.registry.utils.Cell.cell;
import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static org.apache.http.params.CoreConnectionPNames.CONNECTION_TIMEOUT;
import static org.apache.http.params.CoreConnectionPNames.SO_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.microprofile.config.ConfigProvider.getConfig;

public abstract class ITBase {

    private static final Logger log = LoggerFactory.getLogger(ITBase.class);

    public static final String DEPLOYMENT_TARGET = "test.operator.deployment-target";
    public static final String OPERATOR_DEPLOYMENT_PROP = "test.operator.deployment-type";
    public static final String INGRESS_HOST_PROP = "test.operator.ingress-host";
    public static final String INGRESS_SKIP_PROP = "test.operator.ingress-skip";
    public static final String REMOTE_DEBUG_PROP = "test.operator.remote-debug-enabled";
    public static final String CLEANUP = "test.operator.cleanup-enabled";
    public static final String CRD_FILE = "../model/target/classes/META-INF/fabric8/apicurioregistries3.registry.apicur.io-v1.yml";
    public static final String REMOTE_TESTS_INSTALL_FILE = "test.operator.install-file";

    public static final Duration POLL_INTERVAL_DURATION = ofSeconds(5);
    public static final Duration SHORT_DURATION = ofSeconds(30);
    // NOTE: When running remote tests, some extra time might be needed to pull an image before the pod can be run.
    // TODO: Consider changing the duration based on test type or the situation.
    public static final Duration MEDIUM_DURATION = ofSeconds(75);
    public static final Duration LONG_DURATION = ofSeconds(300);

    public enum OperatorDeployment {
        local, remote
    }

    protected static OperatorDeployment operatorDeployment;
    protected static KubernetesClient client;
    protected static PodLogManager podLogManager;
    protected static PortForwardManager portForwardManager;
    protected static IngressManager ingressManager;
    protected static String deploymentTarget;
    protected static String namespace;
    protected static boolean cleanup;
    protected static boolean strimziInstalled = false;
    private static App app;
    protected static JobManager jobManager;
    protected static HostAliasManager hostAliasManager;

    @BeforeAll
    public static void before() throws Exception {
        operatorDeployment = getConfig().getValue(OPERATOR_DEPLOYMENT_PROP,
                OperatorDeployment.class);
        deploymentTarget = getConfig().getValue(DEPLOYMENT_TARGET, String.class);
        cleanup = getConfig().getValue(CLEANUP, Boolean.class);

        setDefaultAwaitilityTimings();
        configureRestAssured();
        namespace = calculateNamespace();
        client = createK8sClient(namespace);
        createNamespace(client, namespace);

        portForwardManager = new PortForwardManager(namespace);
        ingressManager = new IngressManager(client, namespace);
        podLogManager = new PodLogManager(client);
        hostAliasManager = new HostAliasManager(client);
        jobManager = new JobManager(client, hostAliasManager);

        if (operatorDeployment == OperatorDeployment.remote) {
            createTestResources();
        } else {
            createCRDs();
            startOperator();
        }
        startOperatorPodLog();
    }

    @BeforeEach
    public void beforeEach(TestInfo testInfo) {
        String testClassName = testInfo.getTestClass().map(c -> c.getSimpleName() + ".").orElse("");
        log.info("\n" +
                        "------- STARTING: {}{}\n" +
                        "------- Namespace: {}\n" +
                        "------- Mode: {}\n" +
                        "------- Deployment target: {}",
                testClassName, testInfo.getDisplayName(),
                namespace,
                ((operatorDeployment == OperatorDeployment.remote) ? "remote" : "local"),
                deploymentTarget);
    }

    protected static void startOperatorPodLog() {
        if (operatorDeployment == OperatorDeployment.remote) {
            var operatorPod = waitOnOperatorPodReady();
            if (getConfig().getValue(REMOTE_DEBUG_PROP, Boolean.class)) {
                portForwardManager.startPodPortForward(operatorPod.getMetadata().getName(), 5005, 15005);
                log.info("Remote debugging enabled. Attach your debugger to port 15005.");
            }
            podLogManager.startPodLog(ResourceID.fromResource(operatorPod));
        } else {
            if (getConfig().getValue(REMOTE_DEBUG_PROP, Boolean.class)) {
                log.warn("Property {} has no effect on local deployment.", REMOTE_DEBUG_PROP);
            }
        }
    }

    protected static void checkDeploymentExists(ApicurioRegistry3 primary, String component, int replicas) {
        await().atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(client.apps().deployments()
                    .inNamespace(ofNullable(primary.getMetadata().getNamespace()).orElse(namespace))
                    .withName(primary.getMetadata().getName() + "-" + component + "-deployment").get()
                    .getStatus().getReadyReplicas()).isEqualTo(replicas);
        });
    }

    protected static void checkDeploymentDoesNotExist(ApicurioRegistry3 primary, String component) {
        Runnable check = () -> {
            assertThat(client.apps().deployments()
                    .inNamespace(ofNullable(primary.getMetadata().getNamespace()).orElse(namespace))
                    .withName(primary.getMetadata().getName() + "-" + component + "-deployment").get())
                    .isNull();
        };
        await().during(SHORT_DURATION.dividedBy(2)).atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(check::run);
        check.run();
    }

    protected static void checkServiceExists(ApicurioRegistry3 primary, String component) {
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(client.services()
                    .inNamespace(ofNullable(primary.getMetadata().getNamespace()).orElse(namespace))
                    .withName(primary.getMetadata().getName() + "-" + component + "-service").get())
                    .isNotNull();
        });
    }

    protected static void checkServiceDoesNotExist(ApicurioRegistry3 primary, String component) {
        Runnable check = () -> {
            assertThat(client.services()
                    .inNamespace(ofNullable(primary.getMetadata().getNamespace()).orElse(namespace))
                    .withName(primary.getMetadata().getName() + "-" + component + "-service").get()).isNull();
        };
        await().during(SHORT_DURATION.dividedBy(2)).atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(check::run);
        check.run();
    }

    protected static void checkIngressExists(ApicurioRegistry3 primary, String component) {
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(client.network().v1().ingresses()
                    .inNamespace(ofNullable(primary.getMetadata().getNamespace()).orElse(namespace))
                    .withName(primary.getMetadata().getName() + "-" + component + "-ingress").get())
                    .isNotNull();
        });
    }

    protected static void checkIngressDoesNotExist(ApicurioRegistry3 primary, String component) {
        Runnable check = () -> {
            assertThat(client.network().v1().ingresses()
                    .inNamespace(ofNullable(primary.getMetadata().getNamespace()).orElse(namespace))
                    .withName(primary.getMetadata().getName() + "-" + component + "-ingress").get()).isNull();
        };
        await().during(SHORT_DURATION.dividedBy(2)).atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(check::run);
        check.run();
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

    private static void configureRestAssured() {
        RestAssured.config = RestAssured.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        // Helps with port-forwarded connection issues.
                        .setParam(CONNECTION_TIMEOUT, 10 * 1000)
                        .setParam(SO_TIMEOUT, 5 * 1000)
                );
    }

    static KubernetesClient createK8sClient(String namespace) {
        return new KubernetesClientBuilder()
                .withConfig(new ConfigBuilder(Config.autoConfigure(null)).withNamespace(namespace).build())
                .build();
    }

    private static List<HasMetadata> loadTestResources() throws IOException {
        var installFilePath = Path
                .of(getConfig().getValue(REMOTE_TESTS_INSTALL_FILE, String.class));
        try {
            var installFileRaw = Files.readString(installFilePath);
            // We're not editing the deserialized resources to replicate the user experience
            installFileRaw = installFileRaw.replace("PLACEHOLDER_NAMESPACE", namespace);
            return Serialization.unmarshal(installFileRaw);
        } catch (NoSuchFileException ex) {
            throw new OperatorException("Remote tests require an install file to be generated. " +
                    "Please run `make INSTALL_FILE=\"" + installFilePath + "\" dist-install-file` first, " +
                    "or see the README for more information.", ex);
        }
    }

    private static void createTestResources() throws Exception {
        log.info("Creating generated resources into Namespace {}", namespace);
        loadTestResources().forEach(r -> {
            log.info("Creating resource kind {} with name {} in namespace {}", r.getKind(), r.getMetadata().getName(), namespace);
            if ("minikube".equals(deploymentTarget) && r instanceof Deployment d) {
                // See https://stackoverflow.com/a/46101923
                d.getSpec().getTemplate().getSpec().getContainers()
                        .forEach(c -> c.setImagePullPolicy("IfNotPresent"));
            }
            await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
                client.resource(r).inNamespace(namespace).createOrReplace();
                assertThat(client.resource(r).inNamespace(namespace).get()).isNotNull();
            });
        });
    }

    protected static Deployment getOperatorDeployment() {
        List<Deployment> operatorDeployments = new ArrayList<>();
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            operatorDeployments.clear();
            operatorDeployments.addAll(
                    client.apps().deployments()
                            .withLabels(Labels.getOperatorSelectorLabels())
                            .list().getItems()
            );
            assertThat(operatorDeployments).hasSize(1);
        });
        return operatorDeployments.get(0);
    }

    protected static Pod waitOnOperatorPodReady() {
        Cell<Pod> pod = cell();
        // Wait until the operator pod name remains stable, we're occasionally having timeout when trying to access pod logs.
        // TODO: Handle pod restarts/redeployments.
        // TODO: Allow configuring wait time dilatation.
        await().atMost(MEDIUM_DURATION.multipliedBy(2)).during(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            var operatorPods = client.pods()
                    .withLabels(Labels.getOperatorSelectorLabels())
                    .list().getItems();
            assertThat(operatorPods)
                    .withFailMessage("Expected exactly one operator pod, but found: %s", operatorPods.stream().map(ResourceID::fromResource).toList())
                    .hasSize(1);
            if (pod.get() != null) {
                assertThat(ResourceID.fromResource(operatorPods.get(0)))
                        .withFailMessage("Operator pod changed: was %s, now %s", ResourceID.fromResource(pod.get()), ResourceID.fromResource(operatorPods.get(0)))
                        .isEqualTo(ResourceID.fromResource(pod.get()));
            }
            pod.set(operatorPods.get(0));
            assertThat(client.resource(pod.get()).isReady())
                    .withFailMessage("Operator pod %s is not ready. Conditions:\n%s", ResourceID.fromResource(pod.get()), toYAML(pod.get().getStatus().getConditions()))
                    .isTrue();
        });
        return pod.get();
    }

    private static void cleanTestResources() throws Exception {
        if (cleanup) {
            log.info("Deleting generated resources from Namespace {}", namespace);
            loadTestResources().forEach(r -> {
                client.resource(r).inNamespace(namespace).delete();
            });
        }
    }

    private static void createCRDs() {
        log.info("Creating CRDs");
        try {
            var crd = client.load(new FileInputStream(CRD_FILE));
            crd.createOrReplace();
            await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
                crd.resources().forEach(r -> assertThat(r.get()).isNotNull());
            });
        } catch (Exception e) {
            log.warn("Failed to create the CRD, retrying", e);
            createCRDs();
        }
    }

    private static void startOperator() {
        app = CDI.current().select(App.class).get();
        app.start(configOverride -> {
            configOverride.withKubernetesClient(client);
            configOverride.withUseSSAToPatchPrimaryResource(false);
        });
    }

    static void applyStrimziResources() throws IOException {
        // TODO: IMPORTANT: Strimzi >0.45 only supports Kraft-based Kafka clusters. Migration needed.
        // var strimziClusterOperatorURL = new URL("https://strimzi.io/install/latest");
        var strimziClusterOperatorURL = new URL("https://github.com/strimzi/strimzi-kafka-operator/releases/download/0.45.1/strimzi-cluster-operator-0.45.1.yaml");
        try (BufferedInputStream in = new BufferedInputStream(strimziClusterOperatorURL.openStream())) {
            List<HasMetadata> resources = Serialization.unmarshal(in);
            resources.forEach(r -> {
                if (r.getKind().equals("ClusterRoleBinding") && r instanceof ClusterRoleBinding) {
                    var crb = (ClusterRoleBinding) r;
                    crb.getSubjects().forEach(s -> s.setNamespace(namespace));
                } else if (r.getKind().equals("RoleBinding") && r instanceof RoleBinding) {
                    var crb = (RoleBinding) r;
                    crb.getSubjects().forEach(s -> s.setNamespace(namespace));
                }
                log.info("Creating Strimzi resource kind {} in namespace {}", r.getKind(), namespace);
                client.resource(r).inNamespace(namespace).createOrReplace();
                await().atMost(Duration.ofMinutes(2)).ignoreExceptions().until(() -> {
                    assertThat(client.resource(r).inNamespace(namespace).get()).isNotNull();
                    return true;
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

    static void createResources(List<HasMetadata> resources, String resourceType) {
        resources.forEach(r -> {
            log.info("Creating {} resource kind {} in namespace {}", resourceType, r.getKind(), namespace);
            client.resource(r).inNamespace(namespace).createOrReplace();
            await().ignoreExceptions().until(() -> {
                assertThat(client.resource(r).inNamespace(namespace).get()).isNotNull();
                return true;
            });
        });
    }

    @AfterEach
    void afterEach() {
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
    static void afterAll() throws Exception {
        portForwardManager.close();
        if (operatorDeployment == OperatorDeployment.local) {
            app.stop();
            log.info("Creating new K8s Client");
            // create a new client bc operator has closed the old one
            client = createK8sClient(namespace);
        } else {
            cleanTestResources();
        }
        podLogManager.stopAndWait();
        if (cleanup) {
            log.info("Deleting namespace : {}", namespace);
            assertThat(client.namespaces().withName(namespace).delete()).isNotNull();
        }
        client.close();
    }
}
