package io.apicurio.registry.operator.install;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
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
 * so the namespaced Role is sufficient and the operator does not 403 on cluster-scoped list calls.</li>
 * </ul>
 * Combined with the CI freshness check (the file must match {@code make dist-install-file-namespaced}),
 * this guarantees the shipped file stays least-privilege.
 */
class NamespacedInstallFileTest {

    private static final Path INSTALL_NAMESPACED = Path.of("../install/install-namespaced.yaml");

    private static final String CR_RESOURCE = "apicurioregistries3";
    private static final String CRD_RESOURCE = "customresourcedefinitions";
    private static final String WATCHED_NAMESPACES_ENV = "APICURIO_OPERATOR_WATCHED_NAMESPACES";

    private static List<HasMetadata> load() throws IOException {
        return Serialization.unmarshal(Files.readString(INSTALL_NAMESPACED));
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
}
