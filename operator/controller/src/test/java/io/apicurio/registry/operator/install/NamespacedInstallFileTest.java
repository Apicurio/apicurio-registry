package io.apicurio.registry.operator.install;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that the generated single-namespace manifest install file (install-namespaced.yaml) is
 * genuinely least-privilege and confined to the operator's own namespace.
 * <p>
 * This is the manifest-path counterpart to the OLM coverage in the olm-tests module: it closes the
 * "test both install modes" requirement of issue #9008 for the non-OLM path, deterministically and
 * without a cluster. The behavioral watch-confinement is separately exercised by
 * {@code RestrictedNamespaceITTest}; this test asserts the shipped install file has the right shape:
 * <ul>
 * <li>workload RBAC lives in a namespaced {@code Role} (+ {@code RoleBinding}), never a ClusterRole,
 * so a single-namespace deployer cannot act across the cluster;</li>
 * <li>the only cluster-scoped grant is read-only CR/CRD discovery ({@code get/list/watch} on the CR,
 * {@code get} on the CRD), never workload verbs;</li>
 * <li>the operator Deployment pins {@code APICURIO_OPERATOR_WATCHED_NAMESPACES} to its own namespace,
 * so the namespaced Role is sufficient and the operator does not 403 on cluster-scoped list calls;</li>
 * <li>the cluster-scoped RBAC objects are named per install namespace, so this file cannot overwrite
 * the cluster-wide install's ClusterRole/ClusterRoleBinding and two single-namespace installs can
 * coexist.</li>
 * </ul>
 * Combined with the CI freshness check (the file must match {@code make dist-install-file-namespaced}),
 * this guarantees the shipped file stays least-privilege.
 */
class NamespacedInstallFileTest {

    private static final Path INSTALL_NAMESPACED = Path.of("../install/install-namespaced.yaml");
    private static final Path INSTALL_CLUSTER_WIDE = Path.of("../install/install.yaml");

    private static final String CR_RESOURCE = "apicurioregistries3";
    private static final String CRD_RESOURCE = "customresourcedefinitions";
    private static final String WATCHED_NAMESPACES_ENV = "APICURIO_OPERATOR_WATCHED_NAMESPACES";

    private static List<HasMetadata> load() throws IOException {
        return load(INSTALL_NAMESPACED);
    }

    private static List<HasMetadata> load(Path installFile) throws IOException {
        return Serialization.unmarshal(Files.readString(installFile));
    }

    @Test
    void workloadRbacIsNamespaceScoped() throws IOException {
        var resources = load();

        var roles = resources.stream().filter(r -> r instanceof Role).map(r -> (Role) r).toList();
        assertThat(roles).as("a namespaced Role for workloads must be present").hasSize(1);

        Set<String> roleResources = roles.stream().flatMap(r -> r.getRules().stream())
                .flatMap(rule -> rule.getResources().stream()).collect(Collectors.toSet());
        assertThat(roleResources).as("workload resources must live in the namespaced Role")
                .contains("deployments", "services", "configmaps", "secrets");

        assertThat(resources.stream().anyMatch(r -> r instanceof RoleBinding))
                .as("the workload Role must be bound with a namespaced RoleBinding").isTrue();
    }

    @Test
    void clusterTierIsReadOnlyDiscoveryOnly() throws IOException {
        var clusterRoles = resourcesOfType(load(), ClusterRole.class);
        assertThat(clusterRoles).as("exactly one read-only discovery ClusterRole is expected").hasSize(1);

        for (ClusterRole cr : clusterRoles) {
            for (PolicyRule rule : cr.getRules()) {
                assertThat(rule.getVerbs())
                        .as("cluster-tier rule on %s must be read-only", rule.getResources())
                        .isSubsetOf("get", "list", "watch");
                assertThat(rule.getResources())
                        .as("cluster tier must only cover CR/CRD discovery")
                        .isSubsetOf(CR_RESOURCE, CR_RESOURCE + "/status", CRD_RESOURCE);
            }
        }
    }

    /**
     * ClusterRole and ClusterRoleBinding are cluster-scoped, so their names are global. If this
     * variant reused the names install.yaml ships, {@code kubectl apply} would overwrite those
     * objects rather than merge with them: {@code rules} and {@code subjects} are atomic lists.
     * Installing this file onto a cluster already running the all-namespaces install would strip
     * that install's ClusterRole down to read-only discovery and make the central operator 403 on
     * every reconcile, and a second single-namespace install would repoint the shared binding away
     * from the first operator's ServiceAccount. Both are silent at apply time, which is why this is
     * guarded here rather than left to documentation.
     * <p>
     * The CustomResourceDefinition is deliberately excluded: it is cluster-scoped too, but both
     * files ship the same CRD, so sharing that name is correct and applying either file is
     * idempotent for it.
     */
    @Test
    void clusterScopedRbacNamesDoNotCollideWithTheClusterWideInstall() throws IOException {
        var namespaced = load();
        var clusterWide = load(INSTALL_CLUSTER_WIDE);

        // Without this the comparison below would pass vacuously if install.yaml ever stopped
        // parsing or stopped shipping cluster-scoped RBAC.
        assertThat(clusterScopedRbacNames(clusterWide))
                .as("install.yaml is expected to ship a ClusterRole and a ClusterRoleBinding")
                .hasSize(2);

        assertThat(clusterScopedRbacNames(namespaced))
                .as("cluster-scoped RBAC names must not be shared with install.yaml, "
                        + "or applying one install file silently rewrites the other's permissions")
                .doesNotContainAnyElementsOf(clusterScopedRbacNames(clusterWide));

        var clusterRoles = resourcesOfType(namespaced, ClusterRole.class);
        var bindings = resourcesOfType(namespaced, ClusterRoleBinding.class);
        assertThat(clusterRoles).hasSize(1);
        assertThat(bindings).hasSize(1);
        assertThat(bindings.get(0).getRoleRef().getName())
                .as("the binding must grant this install's own ClusterRole, not the cluster-wide one")
                .isEqualTo(clusterRoles.get(0).getMetadata().getName());
    }

    @Test
    void operatorWatchesOnlyItsOwnNamespace() throws IOException {
        var deployments = resourcesOfType(load(), Deployment.class);
        assertThat(deployments).hasSize(1);

        var watched = deployments.get(0).getSpec().getTemplate().getSpec().getContainers().stream()
                .flatMap(c -> c.getEnv().stream())
                .filter(e -> WATCHED_NAMESPACES_ENV.equals(e.getName())).toList();
        assertThat(watched).as("%s must be set", WATCHED_NAMESPACES_ENV).hasSize(1);

        var valueFrom = watched.get(0).getValueFrom();
        assertThat(valueFrom).as("watched namespaces must come from the pod's own namespace, not a literal")
                .isNotNull();
        assertThat(valueFrom.getFieldRef()).isNotNull();
        assertThat(valueFrom.getFieldRef().getFieldPath()).isEqualTo("metadata.namespace");
    }

    private static <T> List<T> resourcesOfType(List<HasMetadata> resources, Class<T> type) {
        return resources.stream().filter(type::isInstance).map(type::cast).toList();
    }

    private static Set<String> clusterScopedRbacNames(List<HasMetadata> resources) {
        return resources.stream()
                .filter(r -> r instanceof ClusterRole || r instanceof ClusterRoleBinding)
                .map(r -> r.getKind() + "/" + r.getMetadata().getName()).collect(Collectors.toSet());
    }
}
