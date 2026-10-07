package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.apicurio.registry.operator.utils.RetryTest;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static io.apicurio.registry.operator.Tags.OLM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies the AllNamespaces install mode: with an OperatorGroup that has no targetNamespaces, the
 * operator watches all namespaces and can act on operands in a namespace other than its own, which
 * is only possible with cluster-wide workload permissions.
 * <p>
 * This works even though the workload verbs live in the CSV's namespace-scoped {@code permissions}
 * (from rbac/namespaced/role.yaml): in AllNamespaces mode the OperatorGroup targets all namespaces
 * ({@code *}), and OLM promotes each CSV {@code permissions} rule to a ClusterRole +
 * ClusterRoleBinding. In SingleNamespace mode the same rules are instead copied as Roles into the
 * target namespace(s). See the OLM OperatorGroup RBAC docs.
 * <p>
 * The RBAC promotion is asserted deterministically with a SubjectAccessReview (the operator
 * ServiceAccount may create Deployments in another namespace). A second assertion then confirms the
 * all-namespaces watch end to end by creating a CR in that namespace and checking the operator
 * reconciles the operand Deployment there. It asserts the Deployment is created rather than waiting
 * for its pods to pull the app image and become ready: that readiness wait is the heaviest and most
 * flake-prone part and is not what this RBAC test needs to prove.
 * <p>
 * Disabled entirely under OLM v1 (class-level, so {@code OLMITBase}'s {@code @BeforeAll} never runs
 * either): OperatorGroups, and the AllNamespaces install mode via an empty {@code targetNamespaces},
 * are an OLM v0 concept; in v1 mode {@code setupOLMResources()} ignores
 * {@link #getOperatorGroupResourcePath()} entirely. An earlier version skipped this with
 * {@code assumeTrue} inside the test method instead, but that let {@code @BeforeAll} start the
 * (asynchronous) v1 install, and the assumption then aborted before the CRD was registered;
 * {@code @RetryTest} retried the aborted test, and {@code OLMITBase.afterEach()} 404'd trying to
 * delete a CR of a type the cluster did not know about yet, which surfaced as a real test failure
 * instead of a clean skip.
 */
@QuarkusTest
@Tag(OLM)
@DisabledIfSystemProperty(named = OLMTestUtils.OLM_VERSION_PROP, matches = "1")
public class AllNamespacesOLMITTest extends OLMITBase {

    @Override
    protected String getOperatorGroupResourcePath() {
        return "olmv0/operator-group-all-namespaces.yaml";
    }

    @RetryTest
    void operandDeploysInAnotherNamespace() {
        // Wait for the operator to be ready first.
        var projectVersion = ConfigProvider.getConfig().getValue(PROJECT_VERSION_PROP, String.class);
        await().ignoreExceptions().untilAsserted(() -> {
            assertThat(client.apps().deployments()
                    .withName("apicurio-registry-operator-v" + projectVersion.toLowerCase()).get().getStatus()
                    .getReadyReplicas()).isEqualTo(1);
        });

        // Create a second namespace that is NOT the operator's install namespace.
        var otherNamespace = ITBase.calculateNamespace();
        ITBase.createNamespace(client, otherNamespace);
        try {
            // Deterministic RBAC signal: in AllNamespaces mode OLM promotes the workload
            // `permissions` to a ClusterRole + ClusterRoleBinding, so the operator ServiceAccount can
            // create workloads in a namespace it does not "own". Poll until the promoted grant
            // converges. This does not depend on the operand image.
            var serviceAccountUser = operatorServiceAccountUser();
            await().ignoreExceptions()
                    .untilAsserted(() -> assertThat(canCreateDeployment(serviceAccountUser, otherNamespace))
                            .withFailMessage(
                                    "In AllNamespaces mode the operator ServiceAccount should be allowed to create Deployments in another namespace '%s'",
                                    otherNamespace)
                            .isTrue());

            // End-to-end signal: the operator watches every namespace, so creating a CR in
            // otherNamespace drives it to reconcile the operand there. Assert the operand Deployment
            // is created (it exists) rather than waiting for readyReplicas, which depends on the app
            // image pull and pod readiness, not on RBAC.
            var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                    ApicurioRegistry3.class);
            registry.getMetadata().setNamespace(otherNamespace);
            registry.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
            registry.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

            client.resource(registry).create();

            await().ignoreExceptions().untilAsserted(() -> {
                assertThat(client.apps().deployments().inNamespace(otherNamespace)
                        .withName(registry.getMetadata().getName() + "-app-deployment").get()).isNotNull();
            });
        } finally {
            if (cleanup) {
                client.namespaces().withName(otherNamespace).delete();
            }
        }
    }
}
