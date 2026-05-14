package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.KubernetesOpsSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageType;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.Tags.FEATURE;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.utils.K8sCell.k8sCellCreate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Tag(FEATURE)
public class KubernetesOpsRbacITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(KubernetesOpsRbacITTest.class);

    @Test
    void testRbacResourcesCreatedForKubernetesOps() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
        registry.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

        // Configure KubernetesOps storage
        registry.getSpec().getApp().withStorage().setType(StorageType.KUBERNETESOPS);
        var k8sOps = new KubernetesOpsSpec();
        k8sOps.setRegistryId("test-registry");
        registry.getSpec().getApp().getStorage().setKubernetesops(k8sOps);

        client.resource(registry).create();

        var saName = registry.getMetadata().getName() + "-kubeops";

        // Verify ServiceAccount is created
        await().ignoreExceptions().untilAsserted(() -> {
            ServiceAccount sa = client.serviceAccounts().inNamespace(namespace)
                    .withName(saName).get();
            assertThat(sa).isNotNull();
        });

        // Verify Role is created with correct permissions
        await().ignoreExceptions().untilAsserted(() -> {
            Role role = client.rbac().roles().inNamespace(namespace)
                    .withName(saName).get();
            assertThat(role).isNotNull();
            assertThat(role.getRules()).hasSize(1);
            assertThat(role.getRules().get(0).getApiGroups()).containsExactly("");
            assertThat(role.getRules().get(0).getResources()).containsExactly("configmaps");
            assertThat(role.getRules().get(0).getVerbs()).containsExactlyInAnyOrder("get", "list", "watch");
        });

        // Verify RoleBinding is created linking SA to Role
        await().ignoreExceptions().untilAsserted(() -> {
            RoleBinding rb = client.rbac().roleBindings().inNamespace(namespace)
                    .withName(saName).get();
            assertThat(rb).isNotNull();
            assertThat(rb.getRoleRef().getKind()).isEqualTo("Role");
            assertThat(rb.getRoleRef().getName()).isEqualTo(saName);
            assertThat(rb.getSubjects()).hasSize(1);
            assertThat(rb.getSubjects().get(0).getKind()).isEqualTo("ServiceAccount");
            assertThat(rb.getSubjects().get(0).getName()).isEqualTo(saName);
            assertThat(rb.getSubjects().get(0).getNamespace()).isEqualTo(namespace);
        });

        // Verify Deployment has serviceAccountName set
        await().ignoreExceptions().untilAsserted(() -> {
            var deployment = client.apps().deployments().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-deployment").get();
            assertThat(deployment).isNotNull();
            assertThat(deployment.getSpec().getTemplate().getSpec().getServiceAccountName())
                    .isEqualTo(saName);
        });
    }

    @Test
    void testRbacResourcesNotCreatedWithoutKubernetesOps() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
        registry.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

        client.resource(registry).create();

        // Wait for deployment to be ready (in-memory storage)
        checkDeploymentExists(registry, COMPONENT_APP, 1);

        var saName = registry.getMetadata().getName() + "-kubeops";

        // Verify RBAC resources are NOT created
        assertThat(client.serviceAccounts().inNamespace(namespace).withName(saName).get()).isNull();
        assertThat(client.rbac().roles().inNamespace(namespace).withName(saName).get()).isNull();
        assertThat(client.rbac().roleBindings().inNamespace(namespace).withName(saName).get()).isNull();

        // Verify Deployment does NOT have serviceAccountName set to kubeops
        var deployment = client.apps().deployments().inNamespace(namespace)
                .withName(registry.getMetadata().getName() + "-app-deployment").get();
        assertThat(deployment.getSpec().getTemplate().getSpec().getServiceAccountName())
                .isNotEqualTo(saName);
    }

    @Test
    void testRbacResourcesCleanedUpOnStorageTypeChange() {
        final var registry = k8sCellCreate(client, () -> {
            var r = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                    ApicurioRegistry3.class);
            r.getMetadata().setNamespace(namespace);
            r.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
            r.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

            // Start with KubernetesOps storage
            r.getSpec().getApp().withStorage().setType(StorageType.KUBERNETESOPS);
            var k8sOps = new KubernetesOpsSpec();
            k8sOps.setRegistryId("test-registry");
            r.getSpec().getApp().getStorage().setKubernetesops(k8sOps);

            return r;
        });

        var saName = registry.getCached().getMetadata().getName() + "-kubeops";

        // Wait for RBAC resources to exist
        await().ignoreExceptions().untilAsserted(() -> {
            assertThat(client.serviceAccounts().inNamespace(namespace)
                    .withName(saName).get()).isNotNull();
            assertThat(client.rbac().roles().inNamespace(namespace)
                    .withName(saName).get()).isNotNull();
            assertThat(client.rbac().roleBindings().inNamespace(namespace)
                    .withName(saName).get()).isNotNull();
        });

        // Switch to in-memory storage (no storage type = in-memory)
        registry.update(r -> {
            r.getSpec().getApp().getStorage().setType(null);
            r.getSpec().getApp().getStorage().setKubernetesops(null);
        });

        // Verify RBAC resources are cleaned up
        await().untilAsserted(() -> {
            assertThat(client.serviceAccounts().inNamespace(namespace)
                    .withName(saName).get()).isNull();
            assertThat(client.rbac().roles().inNamespace(namespace)
                    .withName(saName).get()).isNull();
            assertThat(client.rbac().roleBindings().inNamespace(namespace)
                    .withName(saName).get()).isNull();
        });
    }
}
