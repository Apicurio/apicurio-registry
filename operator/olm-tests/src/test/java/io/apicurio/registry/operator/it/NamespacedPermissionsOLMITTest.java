package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.utils.RetryTest;
import io.fabric8.kubernetes.api.model.authorization.v1.SubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SubjectAccessReviewBuilder;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.Tags.OLM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Negative test for the SingleNamespace/OwnNamespace install mode.
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
 */
@QuarkusTest
@Tag(OLM)
public class NamespacedPermissionsOLMITTest extends OLMITBase {

    private static final Logger log = LoggerFactory.getLogger(NamespacedPermissionsOLMITTest.class);

    private static final String OPERATOR_SERVICE_ACCOUNT = "apicurio-registry-operator";

    @RetryTest
    void workloadPermissionsAreNamespaceScoped() {
        // This asserts the SingleNamespace (OLM v0) boundary. In OLM v1 the operator is installed
        // via the installer ClusterRole which grants workload access cluster-wide, so the
        // SubjectAccessReview would be allowed and this negative assertion would not hold.
        assumeTrue(getOlmVersion() == 0, "Namespace-scoped permission boundary test only applies to OLM v0");

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
            var serviceAccountUser = "system:serviceaccount:" + namespace + ":" + OPERATOR_SERVICE_ACCOUNT;

            // Allowed: creating a Deployment in the operator's own (target) namespace.
            assertThat(canCreateDeployment(serviceAccountUser, namespace))
                    .withFailMessage(
                            "Operator ServiceAccount should be allowed to create Deployments in its target namespace '%s'",
                            namespace)
                    .isTrue();

            // Forbidden: creating a Deployment in a namespace the operator does not target.
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

    private boolean canCreateDeployment(String user, String reviewNamespace) {
        SubjectAccessReview review = new SubjectAccessReviewBuilder()
                .withNewSpec()
                .withUser(user)
                .withNewResourceAttributes()
                .withNamespace(reviewNamespace)
                .withVerb("create")
                .withGroup("apps")
                .withResource("deployments")
                .endResourceAttributes()
                .endSpec()
                .build();
        var result = client.authorization().v1().subjectAccessReview().create(review);
        return Boolean.TRUE.equals(result.getStatus().getAllowed());
    }
}
