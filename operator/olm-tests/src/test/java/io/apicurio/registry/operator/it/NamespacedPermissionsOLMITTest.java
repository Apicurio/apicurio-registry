package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.utils.RetryTest;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static io.apicurio.registry.operator.Tags.OLM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Permission-boundary test for the OwnNamespace install mode: the default OperatorGroup targets the
 * operator's own install namespace, so target == install namespace.
 * <p>
 * With the least-privilege split, workload permissions (e.g. creating Deployments) are granted via
 * a namespace-scoped Role + RoleBinding that OLM only creates in the operator's target namespace.
 * This test asserts the resulting boundary directly: the operator ServiceAccount is allowed to
 * create workload resources in its own namespace, but is forbidden from doing so in a different
 * namespace. Before the split this would have been allowed everywhere via the cluster-wide RBAC.
 * <p>
 * The permission boundary is checked with a SubjectAccessReview rather than by forcing the operator
 * to watch a foreign namespace, because under OLM the watched namespaces and the generated RBAC are
 * kept in sync by the OperatorGroup; the SubjectAccessReview isolates the RBAC scope deterministically
 * and does not depend on operand image readiness.
 * <p>
 * Scope note: this covers OwnNamespace (target == install namespace). The true SingleNamespace case
 * (target != install namespace), where OLM copies the Role into a separate target namespace, is not
 * exercised here.
 * <p>
 * Disabled entirely under OLM v1 (class-level, so {@code OLMITBase}'s {@code @BeforeAll} never runs
 * either): under v1 the installer ClusterRole grants workload access cluster-wide, so this negative
 * assertion would not hold. An earlier version skipped this with {@code assumeTrue} inside the test
 * method instead, but that let {@code @BeforeAll} start the (asynchronous) v1 install, and the
 * assumption then aborted before the CRD was registered; {@code @RetryTest} retried the aborted test,
 * and {@code OLMITBase.afterEach()} 404'd trying to delete a CR of a type the cluster did not know
 * about yet, which surfaced as a real test failure instead of a clean skip.
 */
@QuarkusTest
@Tag(OLM)
@DisabledIfSystemProperty(named = OLMTestUtils.OLM_VERSION_PROP, matches = "1")
public class NamespacedPermissionsOLMITTest extends OLMITBase {

    @RetryTest
    void workloadPermissionsAreNamespaceScoped() {
        // Wait for the operator to be ready first.
        var projectVersion = ConfigProvider.getConfig().getValue(PROJECT_VERSION_PROP, String.class);
        await().ignoreExceptions().untilAsserted(() -> {
            assertThat(client.apps().deployments()
                    .withName("apicurio-registry-operator-v" + projectVersion.toLowerCase()).get().getStatus()
                    .getReadyReplicas()).isEqualTo(1);
        });

        // A namespace the operator does not target, so OLM creates no Role/RoleBinding there.
        var foreignNamespace = ITBase.calculateNamespace();
        ITBase.createNamespace(client, foreignNamespace);
        try {
            var serviceAccountUser = operatorServiceAccountUser();

            // Allowed: creating a Deployment in the operator's own (target) namespace. Poll rather
            // than asserting once: OLM may create the RoleBinding a moment after the operator
            // Deployment reports ready, so the grant can converge slightly later.
            await().ignoreExceptions().untilAsserted(() -> assertThat(canCreateDeployment(serviceAccountUser, namespace))
                    .withFailMessage(
                            "Operator ServiceAccount should be allowed to create Deployments in its target namespace '%s'",
                            namespace)
                    .isTrue());

            // Forbidden: creating a Deployment in a namespace the operator does not target. This is
            // checked after the positive grant has converged above, so a false result here reflects
            // the least-privilege boundary rather than RBAC not yet being applied.
            assertThat(canCreateDeployment(serviceAccountUser, foreignNamespace))
                    .withFailMessage(
                            "Operator ServiceAccount should NOT be allowed to create Deployments in a non-target namespace '%s' with least-privilege RBAC",
                            foreignNamespace)
                    .isFalse();
        } finally {
            if (cleanup) {
                client.namespaces().withName(foreignNamespace).delete();
            }
        }
    }
}
