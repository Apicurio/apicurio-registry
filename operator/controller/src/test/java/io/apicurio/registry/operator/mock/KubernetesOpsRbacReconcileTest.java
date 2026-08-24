package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.KubernetesOpsSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageType;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Mock-server equivalents of {@code KubernetesOpsRbacITTest}.
 */
@QuarkusTest
public class KubernetesOpsRbacReconcileTest extends MockServerTestBase {

    @Test
    void rbacResourcesCreatedForKubernetesOps() {
        var registry = registryWithKubernetesOps("rbac-create");
        createRegistry(registry);

        String saName = registry.getMetadata().getName() + "-kubeops";

        // ServiceAccount
        awaitResourceExists(saName,
                () -> client.serviceAccounts().inNamespace(namespace).withName(saName).get());

        // Role with correct permissions
        var role = awaitResourceExists(saName,
                () -> client.rbac().roles().inNamespace(namespace).withName(saName).get());
        assertThat(role.getRules()).hasSize(1);
        assertThat(role.getRules().get(0).getApiGroups()).containsExactly("");
        assertThat(role.getRules().get(0).getResources()).containsExactly("configmaps");
        assertThat(role.getRules().get(0).getVerbs()).containsExactlyInAnyOrder("get", "list", "watch");

        // RoleBinding linking SA to Role
        var rb = awaitResourceExists(saName,
                () -> client.rbac().roleBindings().inNamespace(namespace).withName(saName).get());
        assertThat(rb.getRoleRef().getKind()).isEqualTo("Role");
        assertThat(rb.getRoleRef().getName()).isEqualTo(saName);
        assertThat(rb.getSubjects()).hasSize(1);
        assertThat(rb.getSubjects().get(0).getKind()).isEqualTo("ServiceAccount");
        assertThat(rb.getSubjects().get(0).getName()).isEqualTo(saName);
        assertThat(rb.getSubjects().get(0).getNamespace()).isEqualTo(namespace);

        // Deployment has serviceAccountName set
        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() -> {
            var deployment = client.apps().deployments().inNamespace(namespace)
                    .withName(deploymentName(registry, COMPONENT_APP)).get();
            assertThat(deployment).isNotNull();
            assertThat(deployment.getSpec().getTemplate().getSpec().getServiceAccountName())
                    .isEqualTo(saName);
        });
    }

    @Test
    void rbacResourcesNotCreatedWithoutKubernetesOps() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setName("rbac-absent");
        createRegistry(registry);

        awaitDeploymentExists(deploymentName(registry, COMPONENT_APP));

        String saName = registry.getMetadata().getName() + "-kubeops";
        assertThat(client.serviceAccounts().inNamespace(namespace).withName(saName).get()).isNull();
        assertThat(client.rbac().roles().inNamespace(namespace).withName(saName).get()).isNull();
        assertThat(client.rbac().roleBindings().inNamespace(namespace).withName(saName).get()).isNull();

        var deployment = client.apps().deployments().inNamespace(namespace)
                .withName(deploymentName(registry, COMPONENT_APP)).get();
        assertThat(deployment.getSpec().getTemplate().getSpec().getServiceAccountName())
                .isNotEqualTo(saName);
    }

    @Test
    void rbacResourcesCleanedUpOnStorageTypeChange() {
        var registry = registryWithKubernetesOps("rbac-cleanup");
        createRegistry(registry);

        String saName = registry.getMetadata().getName() + "-kubeops";

        // Wait for RBAC resources
        awaitResourceExists(saName,
                () -> client.serviceAccounts().inNamespace(namespace).withName(saName).get());
        awaitResourceExists(saName,
                () -> client.rbac().roles().inNamespace(namespace).withName(saName).get());
        awaitResourceExists(saName,
                () -> client.rbac().roleBindings().inNamespace(namespace).withName(saName).get());

        // Switch to in-memory storage
        updateRegistry(registry, r -> {
            r.getSpec().getApp().getStorage().setType(null);
            r.getSpec().getApp().getStorage().setKubernetesops(null);
        });

        // RBAC resources should be removed
        awaitResourceAbsent(() -> client.serviceAccounts().inNamespace(namespace).withName(saName).get());
        awaitResourceAbsent(() -> client.rbac().roles().inNamespace(namespace).withName(saName).get());
        awaitResourceAbsent(() -> client.rbac().roleBindings().inNamespace(namespace).withName(saName).get());
    }

    private ApicurioRegistry3 registryWithKubernetesOps(String name) {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setName(name);
        registry.getSpec().getApp().withStorage().setType(StorageType.KUBERNETESOPS);
        var k8sOps = new KubernetesOpsSpec();
        k8sOps.setRegistryId("test-registry");
        registry.getSpec().getApp().getStorage().setKubernetesops(k8sOps);
        return registry;
    }
}
