package io.apicurio.registry.operator.it;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.apicurio.registry.operator.Tags;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-tests the manifest transformation for the cluster-wide Strimzi install, without a cluster.
 * Fails fast on a Strimzi version bump if the upstream RBAC layout changed (see the README next to
 * the vendored manifest).
 */
// Tagged kafka so it runs in the kafka CI shard on PRs (untagged tests are excluded by -Dgroups).
@Tag(Tags.KAFKA)
class StrimziClusterWideInstallerTest {

    private static final String NS = "strimzi-test-ns";

    @Test
    void transformsManifestForClusterWideWatch() throws Exception {
        List<HasMetadata> resources = StrimziClusterWideInstaller.loadAndTransformManifest(NS);

        // 10 CRDs + 7 ClusterRoles + 7 bindings (1 RoleBinding + 6 ClusterRoleBindings, see below)
        // + ServiceAccount + ConfigMap + Deployment
        assertThat(resources).hasSize(27);
        assertThat(resources.stream().filter(r -> "CustomResourceDefinition".equals(r.getKind())))
                .hasSize(10);

        // STRIMZI_NAMESPACE must be a literal "*" (watch all namespaces), not the fieldRef default.
        var deployments = resources.stream().filter(Deployment.class::isInstance)
                .map(Deployment.class::cast).toList();
        assertThat(deployments).hasSize(1);
        var env = deployments.get(0).getSpec().getTemplate().getSpec().getContainers().stream()
                .filter(c -> "strimzi-cluster-operator".equals(c.getName())).findFirst().orElseThrow()
                .getEnv().stream().filter(e -> "STRIMZI_NAMESPACE".equals(e.getName())).findFirst()
                .orElseThrow();
        assertThat(env.getValue()).isEqualTo("*");
        assertThat(env.getValueFrom()).isNull();

        // Only the leader-election RoleBinding stays namespaced.
        var roleBindings = resources.stream().filter(RoleBinding.class::isInstance)
                .map(RoleBinding.class::cast).toList();
        assertThat(roleBindings).hasSize(1);
        assertThat(roleBindings.get(0).getMetadata().getName())
                .isEqualTo("strimzi-cluster-operator-leader-election");
        assertThat(roleBindings.get(0).getSubjects()).allSatisfy(
                s -> assertThat(s.getNamespace()).isEqualTo(NS));

        // 3 original ClusterRoleBindings + 3 converted from RoleBindings, per the Strimzi
        // watch-all-namespaces procedure. All subjects rebound to the install namespace.
        var clusterRoleBindings = resources.stream().filter(ClusterRoleBinding.class::isInstance)
                .map(ClusterRoleBinding.class::cast).toList();
        assertThat(clusterRoleBindings).map(crb -> crb.getMetadata().getName())
                .containsExactlyInAnyOrder("strimzi-cluster-operator",
                        "strimzi-cluster-operator-kafka-broker-delegation",
                        "strimzi-cluster-operator-kafka-client-delegation",
                        "strimzi-cluster-operator-namespaced", "strimzi-cluster-operator-watched",
                        "strimzi-cluster-operator-entity-operator-delegation");
        assertThat(clusterRoleBindings).allSatisfy(crb -> assertThat(crb.getSubjects())
                .allSatisfy(s -> assertThat(s.getNamespace()).isEqualTo(NS)));

        // Converted bindings must keep their original ClusterRole references.
        assertThat(clusterRoleBindings.stream()
                .filter(crb -> "strimzi-cluster-operator-namespaced".equals(crb.getMetadata().getName()))
                .findFirst().orElseThrow().getRoleRef().getName())
                .isEqualTo("strimzi-cluster-operator-namespaced");
        assertThat(clusterRoleBindings.stream()
                .filter(crb -> "strimzi-cluster-operator-entity-operator-delegation"
                        .equals(crb.getMetadata().getName()))
                .findFirst().orElseThrow().getRoleRef().getName()).isEqualTo("strimzi-entity-operator");
    }
}
