package io.apicurio.registry.operator.rbac;

import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against drift between the operator's runtime RBAC and the OLM v1 installer ClusterRole.
 * <p>
 * The operator's runtime permissions come from {@code rbac/namespaced} (a small cluster-scoped
 * ClusterRole for CR discovery plus a namespace-scoped Role for workloads). The OLM v1 installer
 * ClusterRole in {@code olm-tests/.../olmv1/cluster-role.yaml} duplicates these transitively and
 * must grant a superset of them, but there is no generation step keeping the two in sync (see
 * operator/README.md "RBAC"). This test fails if the operator gains a permission the installer
 * role does not also grant.
 * <p>
 * Comparison is on {@code (apiGroup, resource, verb)} tuples and ignores {@code resourceNames}: a
 * rule without {@code resourceNames} is a wildcard that is a superset of any named restriction, so
 * ignoring them cannot produce a false failure for the permissions this project grants.
 * <p>
 * The expected set also includes {@code namespaces get/list/watch}, which is not declared in
 * {@code rbac/namespaced} but is injected by OLM v1 at install time. In AllNamespaces mode (the only
 * mode this bundle currently resolves to under v1) the bundle renderer promotes the CSV
 * {@code permissions} to a ClusterRole and appends {@code namespaces get/list/watch}, mirroring OLM
 * v0's OperatorGroup promotion. The installer SA can only grant permissions it holds (escalation
 * prevention), so the installer role must carry that rule too or the promoted ClusterRole is
 * rejected and the operator's informers 403 at the cluster scope. Asserting it here guards the rule
 * deterministically, instead of leaving it to the live OLM v1 smoke run.
 */
class RbacInstallerSyncTest {

    private static final Path NAMESPACED_CLUSTER_ROLE = Path
            .of("src/main/deploy/rbac/namespaced/cluster-role.yaml");
    private static final Path NAMESPACED_ROLE = Path.of("src/main/deploy/rbac/namespaced/role.yaml");
    private static final Path OLM_V1_INSTALLER_CLUSTER_ROLE = Path
            .of("../olm-tests/src/test/deploy/olmv1/cluster-role.yaml");

    /**
     * Injected by OLM v1's AllNamespaces promotion (not declared in {@code rbac/namespaced}); the
     * installer ClusterRole must still hold it for escalation prevention to allow the promoted
     * ClusterRole.
     */
    private static final Set<String> OLM_V1_PROMOTION_INJECTED_TUPLES = Set.of("/namespaces/get",
            "/namespaces/list", "/namespaces/watch");

    @Test
    void olmV1InstallerIsSupersetOfOperatorPermissions() throws IOException {
        Set<String> operatorTuples = new TreeSet<>();
        operatorTuples.addAll(tuples(loadClusterRoleRules(NAMESPACED_CLUSTER_ROLE)));
        operatorTuples.addAll(tuples(loadRoleRules(NAMESPACED_ROLE)));
        operatorTuples.addAll(OLM_V1_PROMOTION_INJECTED_TUPLES);

        Set<String> installerTuples = tuples(loadClusterRoleRules(OLM_V1_INSTALLER_CLUSTER_ROLE));

        Set<String> missing = new TreeSet<>(operatorTuples);
        missing.removeAll(installerTuples);

        assertThat(missing).withFailMessage(
                "The OLM v1 installer ClusterRole (%s) is missing operator permissions granted in "
                        + "rbac/namespaced. Add these apiGroup/resource/verb tuples to keep them in sync:%n%s",
                OLM_V1_INSTALLER_CLUSTER_ROLE, String.join("\n", missing)).isEmpty();
    }

    private static List<PolicyRule> loadClusterRoleRules(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return Serialization.unmarshal(in, ClusterRole.class).getRules();
        }
    }

    private static List<PolicyRule> loadRoleRules(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return Serialization.unmarshal(in, Role.class).getRules();
        }
    }

    private static Set<String> tuples(List<PolicyRule> rules) {
        Set<String> tuples = new LinkedHashSet<>();
        for (PolicyRule rule : rules) {
            List<String> groups = rule.getApiGroups().isEmpty() ? List.of("") : rule.getApiGroups();
            for (String group : groups) {
                for (String resource : rule.getResources()) {
                    for (String verb : rule.getVerbs()) {
                        tuples.add(group + "/" + resource + "/" + verb);
                    }
                }
            }
        }
        return tuples;
    }
}
