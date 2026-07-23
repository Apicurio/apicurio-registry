package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.Tags;
import io.apicurio.registry.operator.resource.Labels;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
@Tag(Tags.FEATURE)
class OperatorClusterWideInstallerTest {

    private static final String NS = "apicurio-operator-test-ns";
    private static final String FIXTURE = "/operator-install/install-file-fixture.yaml";

    private static List<HasMetadata> transform(String deploymentTarget) throws IOException {
        try (InputStream in = OperatorClusterWideInstallerTest.class.getResourceAsStream(FIXTURE)) {
            assertThat(in).as("fixture on classpath").isNotNull();
            return OperatorClusterWideInstaller.transformInstallFile(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8), NS, deploymentTarget);
        }
    }

    @Test
    void bindsClusterRoleBindingSubjectToTheDedicatedNamespace() throws Exception {
        var resources = transform("minikube");

        var bindings = resources.stream().filter(ClusterRoleBinding.class::isInstance)
                .map(ClusterRoleBinding.class::cast).toList();
        assertThat(bindings).hasSize(1);
        assertThat(bindings.get(0).getSubjects()).singleElement()
                .satisfies(s -> {
                    assertThat(s.getKind()).isEqualTo("ServiceAccount");
                    assertThat(s.getNamespace()).isEqualTo(NS);
                });
        assertThat(bindings.get(0).getRoleRef().getName())
                .isEqualTo("apicurio-registry-operator-clusterrole");
    }

    @Test
    void placesNamespacedResourcesInTheDedicatedNamespace() throws Exception {
        var resources = transform("minikube");

        assertThat(resources).hasSize(5);
        assertThat(resources.stream().filter(ServiceAccount.class::isInstance))
                .singleElement()
                .satisfies(sa -> assertThat(sa.getMetadata().getNamespace()).isEqualTo(NS));
        assertThat(resources.stream().filter(Deployment.class::isInstance))
                .singleElement()
                .satisfies(d -> assertThat(d.getMetadata().getNamespace()).isEqualTo(NS));
        assertThat(resources).noneSatisfy(r -> assertThat(r.getMetadata().getNamespace())
                .isEqualTo("PLACEHOLDER_NAMESPACE"));
    }

    @Test
    void keepsOperatorSelectorLabelsSoTheReadinessGuardCanFindTheDeployment() throws Exception {
        var deployment = (Deployment) transform("minikube").stream()
                .filter(Deployment.class::isInstance).findFirst().orElseThrow();

        assertThat(deployment.getMetadata().getLabels())
                .containsAllEntriesOf(Labels.getOperatorSelectorLabels());
    }

    @Test
    void forcesLocalImageOnMinikubeOnly() throws Exception {
        assertThat(imagePullPolicy(transform("minikube"))).isEqualTo("IfNotPresent");
        assertThat(imagePullPolicy(transform("kubernetes"))).isEqualTo("Always");
    }

    private static String imagePullPolicy(List<HasMetadata> resources) {
        var deployment = (Deployment) resources.stream().filter(Deployment.class::isInstance)
                .findFirst().orElseThrow();
        return deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImagePullPolicy();
    }
}
