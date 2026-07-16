package io.apicurio.registry.operator.it;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.microprofile.config.ConfigProvider.getConfig;

/**
 * Installs the Strimzi cluster operator once per cluster, configured to watch ALL namespaces
 * ({@code STRIMZI_NAMESPACE=*}), into a dedicated namespace that test teardown never deletes.
 * <p>
 * This replaces the previous per-test-class install into the throwaway class namespace, which cost a
 * manifest download plus a full install per Kafka test class and rebound cluster-scoped RBAC subjects
 * on every install. With a cluster-wide watch, test classes only create their {@code Kafka} CRs in
 * their own namespaces and the single operator reconciles all of them.
 * <p>
 * The vendored release manifest (see {@code src/test/resources/strimzi/README.md}) is transformed in
 * code, following the Strimzi "watch all namespaces" deployment procedure: the {@code STRIMZI_NAMESPACE}
 * env var is set to {@code *}, and the namespace-scoped RoleBindings that delegate the
 * {@code strimzi-cluster-operator-namespaced}, {@code strimzi-cluster-operator-watched} and
 * {@code strimzi-entity-operator} ClusterRoles are converted to ClusterRoleBindings. The
 * leader-election RoleBinding stays namespaced in the operator's own namespace.
 * <p>
 * The install is idempotent: if a ready operator Deployment already exists in the dedicated namespace
 * (e.g. installed by a previous test group's JVM on the same cluster), installation is skipped.
 * Cleanup on dev clusters: {@code kubectl delete namespace <ns>} plus the Strimzi CRDs/ClusterRoles.
 */
final class StrimziClusterWideInstaller {

    private static final Logger log = LoggerFactory.getLogger(StrimziClusterWideInstaller.class);

    /** Dedicated, long-lived namespace for the Strimzi operator. Overridable for local setups. */
    static final String STRIMZI_NAMESPACE_PROP = "test.operator.strimzi-namespace";
    static final String STRIMZI_NAMESPACE_DEFAULT = "strimzi-cluster-operator";

    static final String MANIFEST_RESOURCE = "/strimzi/strimzi-cluster-operator-0.47.0.yaml";

    private static final String OPERATOR_DEPLOYMENT_NAME = "strimzi-cluster-operator";
    private static final String STRIMZI_NAMESPACE_ENV = "STRIMZI_NAMESPACE";
    private static final String LEADER_ELECTION_ROLE_BINDING = "strimzi-cluster-operator-leader-election";

    // Guarded by the synchronized ensureInstalled() — no volatile needed.
    private static boolean installedInThisJvm = false;

    private StrimziClusterWideInstaller() {
    }

    static String strimziNamespace() {
        return getConfig().getOptionalValue(STRIMZI_NAMESPACE_PROP, String.class)
                .orElse(STRIMZI_NAMESPACE_DEFAULT);
    }

    /**
     * Ensures the cluster-wide Strimzi operator is installed and ready. Safe to call from every test
     * class; only the first call in a JVM (against a cluster without a ready install) does work.
     */
    static synchronized void ensureInstalled(KubernetesClient client) throws IOException {
        if (installedInThisJvm) {
            return;
        }
        var namespace = strimziNamespace();
        if (isOperatorReady(client, namespace)) {
            log.info("Strimzi operator already ready in namespace {}, skipping install", namespace);
            installedInThisJvm = true;
            return;
        }
        log.info("Installing cluster-wide Strimzi operator into namespace {}", namespace);
        ensureNamespace(client, namespace);
        for (HasMetadata resource : loadAndTransformManifest(namespace)) {
            log.info("Creating Strimzi resource kind {} name {}", resource.getKind(),
                    resource.getMetadata().getName());
            client.resource(resource).inNamespace(namespace).createOrReplace();
        }
        waitForOperatorReady(client, namespace);
        installedInThisJvm = true;
    }

    private static boolean isOperatorReady(KubernetesClient client, String namespace) {
        var deployment = client.apps().deployments().inNamespace(namespace)
                .withName(OPERATOR_DEPLOYMENT_NAME).get();
        if (deployment == null || deployment.getStatus() == null) {
            return false;
        }
        var readyReplicas = deployment.getStatus().getReadyReplicas();
        return readyReplicas != null && readyReplicas > 0;
    }

    private static void ensureNamespace(KubernetesClient client, String namespace) {
        if (client.namespaces().withName(namespace).get() == null) {
            client.resource(new NamespaceBuilder().withNewMetadata()
                    .addToLabels("app", "apicurio-registry-operator-test-strimzi").withName(namespace)
                    .endMetadata().build()).create();
        }
    }

    // Package-private for unit testing (StrimziClusterWideInstallerTest).
    static List<HasMetadata> loadAndTransformManifest(String namespace) throws IOException {
        try (InputStream in = StrimziClusterWideInstaller.class.getResourceAsStream(MANIFEST_RESOURCE)) {
            if (in == null) {
                throw new IOException("Vendored Strimzi manifest not found on classpath: "
                        + MANIFEST_RESOURCE);
            }
            List<HasMetadata> resources = Serialization.unmarshal(in);
            return resources.stream().map(r -> transform(r, namespace)).toList();
        }
    }

    private static HasMetadata transform(HasMetadata resource, String namespace) {
        if (resource instanceof ClusterRoleBinding crb) {
            crb.getSubjects().forEach(s -> s.setNamespace(namespace));
            return crb;
        }
        if (resource instanceof RoleBinding rb) {
            rb.getSubjects().forEach(s -> s.setNamespace(namespace));
            if (LEADER_ELECTION_ROLE_BINDING.equals(rb.getMetadata().getName())) {
                // Leader election lease lives in the operator's own namespace; stays a RoleBinding.
                return rb;
            }
            return toClusterRoleBinding(rb);
        }
        if (resource instanceof Deployment deployment) {
            setWatchAllNamespaces(deployment);
            return deployment;
        }
        return resource;
    }

    /**
     * Converts a namespace-scoped delegation RoleBinding from the release manifest into the
     * ClusterRoleBinding the Strimzi "watch all namespaces" procedure requires. The RoleBinding named
     * {@code strimzi-cluster-operator} is renamed after its ClusterRole ({@code ...-namespaced}) to
     * avoid colliding with the manifest's existing {@code strimzi-cluster-operator} ClusterRoleBinding.
     */
    private static ClusterRoleBinding toClusterRoleBinding(RoleBinding rb) {
        var name = OPERATOR_DEPLOYMENT_NAME.equals(rb.getMetadata().getName())
                ? rb.getRoleRef().getName() : rb.getMetadata().getName();
        return new ClusterRoleBindingBuilder().withNewMetadata().withName(name)
                .withLabels(rb.getMetadata().getLabels()).endMetadata().withRoleRef(rb.getRoleRef())
                .withSubjects(rb.getSubjects()).build();
    }

    private static void setWatchAllNamespaces(Deployment deployment) {
        var containers = deployment.getSpec().getTemplate().getSpec().getContainers();
        var env = containers.stream().filter(c -> OPERATOR_DEPLOYMENT_NAME.equals(c.getName()))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Container " + OPERATOR_DEPLOYMENT_NAME + " not found in Strimzi Deployment"))
                .getEnv();
        EnvVar namespaceVar = env.stream().filter(e -> STRIMZI_NAMESPACE_ENV.equals(e.getName()))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        STRIMZI_NAMESPACE_ENV + " env var not found in Strimzi Deployment"));
        namespaceVar.setValueFrom(null);
        namespaceVar.setValue("*");
    }

    private static void waitForOperatorReady(KubernetesClient client, String namespace) {
        await().atMost(ITBase.MEDIUM_DURATION).ignoreExceptions()
                .untilAsserted(() -> assertThat(isOperatorReady(client, namespace)).isTrue());
    }
}
