package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Mock-server coverage for the CR delete/finalizer lifecycle, which is not exercised by any test
 * migrated in the mock-server test tier.
 *
 * <p>
 * {@code ApicurioRegistry3Reconciler} implements JOSDK's {@code Cleaner}, so a finalizer is
 * registered on the primary CR and {@code cleanup()} must run to completion (removing the
 * finalizer) before the API server will actually delete the CR. That sequencing, and the
 * ownerReference contract dependent resources are stamped with, are both observable against the
 * Fabric8 CRUD mock server without a real cluster.
 *
 * <p>
 * <b>Not covered here:</b> actual cascade deletion of dependent resources when the primary is
 * removed. Dependent resources in this operator rely on Kubernetes' owner-reference garbage
 * collector (kube-controller-manager) to be cleaned up, and the Fabric8 CRUD mock server does not
 * implement a garbage collector - it only stores/serves objects. This test asserts that
 * explicitly: the Deployment is still present immediately after the primary CR is gone. Real
 * cascade cleanup remains covered by the real-cluster IT suite.
 */
@QuarkusTest
public class DeleteLifecycleReconcileTest extends MockServerTestBase {

    @Test
    void finalizerAddedOnCreate() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setName("delete-finalizer");
        createRegistry(registry);

        awaitDeploymentExists(deploymentName(registry, COMPONENT_APP));

        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() -> {
            var cr = client.resources(ApicurioRegistry3.class).inNamespace(namespace)
                    .withName("delete-finalizer").get();
            assertThat(cr).isNotNull();
            assertThat(cr.getMetadata().getFinalizers())
                    .as("JOSDK's Cleaner contract must register a finalizer on the primary CR")
                    .isNotEmpty()
                    .allSatisfy(f -> assertThat(f).endsWith("/finalizer"));
        });
    }

    @Test
    void crDeletionCompletesOnlyAfterCleanup() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setName("delete-cleanup");
        createRegistry(registry);

        String deploymentName = deploymentName(registry, COMPONENT_APP);
        awaitDeploymentExists(deploymentName);

        client.resources(ApicurioRegistry3.class).inNamespace(namespace).withName("delete-cleanup").delete();

        // The CR can only disappear from the API once JOSDK's cleanup control loop has run
        // cleanup() and removed the finalizer - so eventual absence here is proof cleanup
        // completed, not just that a delete request was accepted.
        awaitResourceAbsent(() -> client.resources(ApicurioRegistry3.class).inNamespace(namespace)
                .withName("delete-cleanup").get());

        // The mock server has no garbage collector: dependent resources are not cascade-deleted
        // just because the primary is gone. This is expected and is why cascade-cleanup coverage
        // stays in the real-cluster IT suite.
        assertThat(client.apps().deployments().inNamespace(namespace).withName(deploymentName).get())
                .as("the CRUD mock server does not run a garbage collector, so dependents are not "
                        + "cascade-deleted by primary CR deletion; real cascade cleanup is covered "
                        + "by the IT suite")
                .isNotNull();
    }

    @Test
    void dependentResourcesCarryCompleteOwnerReference() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setName("delete-owner-ref");
        createRegistry(registry);

        Deployment deployment = awaitResourceExists(deploymentName(registry, COMPONENT_APP),
                () -> client.apps().deployments().inNamespace(namespace)
                        .withName(deploymentName(registry, COMPONENT_APP)).get());
        var createdRegistry = client.resources(ApicurioRegistry3.class).inNamespace(namespace)
                .withName("delete-owner-ref").get();

        assertOwnerReference(deployment, createdRegistry);

        var service = awaitResourceExists(serviceName(registry, COMPONENT_APP),
                () -> client.services().inNamespace(namespace)
                        .withName(serviceName(registry, COMPONENT_APP)).get());
        assertOwnerReference(service, createdRegistry);

        String networkPolicyName = registry.getMetadata().getName() + "-" + COMPONENT_APP + "-networkpolicy";
        var networkPolicy = awaitResourceExists(networkPolicyName,
                () -> client.network().v1().networkPolicies().inNamespace(namespace)
                        .withName(networkPolicyName).get());
        assertOwnerReference(networkPolicy, createdRegistry);
    }

    /**
     * Asserts the ownerReference fields this operator actually populates. JOSDK's
     * {@code KubernetesDependentResource#addReferenceHandlingMetadata} delegates to Fabric8's
     * default {@code HasMetadata#addOwnerReference(HasMetadata)}, which only sets
     * {@code apiVersion}/{@code kind}/{@code name}/{@code uid} - it does not set
     * {@code controller} or {@code blockOwnerDeletion} (both are left {@code null}), which this
     * test confirms empirically rather than assumes.
     */
    private static void assertOwnerReference(HasMetadata dependent, ApicurioRegistry3 owner) {
        assertThat(dependent.getMetadata().getOwnerReferences()).singleElement()
                .satisfies((OwnerReference ref) -> {
                    assertThat(ref.getApiVersion()).isEqualTo(owner.getApiVersion());
                    assertThat(ref.getKind()).isEqualTo(owner.getKind());
                    assertThat(ref.getName()).isEqualTo(owner.getMetadata().getName());
                    assertThat(ref.getUid()).isEqualTo(owner.getMetadata().getUid());
                    assertThat(ref.getController()).isNull();
                    assertThat(ref.getBlockOwnerDeletion()).isNull();
                });
    }
}
