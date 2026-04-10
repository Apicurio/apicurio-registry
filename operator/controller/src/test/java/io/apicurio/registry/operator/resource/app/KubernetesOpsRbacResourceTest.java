package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesOpsRbacResourceTest {

    private static final ClassLoader CLASS_LOADER = KubernetesOpsRbacResourceTest.class.getClassLoader();

    @Test
    void testServiceAccountDesired() {
        var registry = loadCR("k8s/examples/kubernetesops/example-default.yaml");
        var resource = new AppServiceAccountResource();
        var sa = resource.desired(registry, null);

        assertThat(sa.getMetadata().getName()).isEqualTo("my-registry-kubeops");
        assertThat(sa.getMetadata().getNamespace()).isEqualTo("test-ns");
    }

    @Test
    void testRoleDesiredDefaultNamespace() {
        var registry = loadCR("k8s/examples/kubernetesops/example-default.yaml");
        var resource = new AppRoleResource();
        var role = resource.desired(registry, null);

        assertThat(role.getMetadata().getName()).isEqualTo("my-registry-kubeops");
        assertThat(role.getMetadata().getNamespace()).isEqualTo("test-ns");
        assertThat(role.getRules()).hasSize(1);
        assertThat(role.getRules().get(0).getApiGroups()).containsExactly("");
        assertThat(role.getRules().get(0).getResources()).containsExactly("configmaps");
        assertThat(role.getRules().get(0).getVerbs()).containsExactlyInAnyOrder("get", "list", "watch");
    }

    @Test
    void testRoleDesiredCustomNamespace() {
        var registry = loadCR("k8s/examples/kubernetesops/example-custom-namespace.yaml");
        var resource = new AppRoleResource();
        var role = resource.desired(registry, null);

        assertThat(role.getMetadata().getNamespace()).isEqualTo("configmaps-ns");
    }

    @Test
    void testRoleBindingDesiredDefaultNamespace() {
        var registry = loadCR("k8s/examples/kubernetesops/example-default.yaml");
        var resource = new AppRoleBindingResource();
        var roleBinding = resource.desired(registry, null);

        assertThat(roleBinding.getMetadata().getName()).isEqualTo("my-registry-kubeops");
        assertThat(roleBinding.getMetadata().getNamespace()).isEqualTo("test-ns");
        assertThat(roleBinding.getRoleRef().getApiGroup()).isEqualTo("rbac.authorization.k8s.io");
        assertThat(roleBinding.getRoleRef().getKind()).isEqualTo("Role");
        assertThat(roleBinding.getRoleRef().getName()).isEqualTo("my-registry-kubeops");
        assertThat(roleBinding.getSubjects()).hasSize(1);
        assertThat(roleBinding.getSubjects().get(0).getKind()).isEqualTo("ServiceAccount");
        assertThat(roleBinding.getSubjects().get(0).getName()).isEqualTo("my-registry-kubeops");
        assertThat(roleBinding.getSubjects().get(0).getNamespace()).isEqualTo("test-ns");
    }

    @Test
    void testRoleBindingDesiredCustomNamespace() {
        var registry = loadCR("k8s/examples/kubernetesops/example-custom-namespace.yaml");
        var resource = new AppRoleBindingResource();
        var roleBinding = resource.desired(registry, null);

        assertThat(roleBinding.getMetadata().getNamespace()).isEqualTo("configmaps-ns");
        assertThat(roleBinding.getSubjects().get(0).getNamespace()).isEqualTo("test-ns");
    }

    private ApicurioRegistry3 loadCR(String path) {
        return ResourceFactory.deserialize(path, ApicurioRegistry3.class, CLASS_LOADER);
    }
}
