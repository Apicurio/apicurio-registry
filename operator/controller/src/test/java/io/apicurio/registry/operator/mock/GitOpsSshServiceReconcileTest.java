package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.StorageType;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Mock-server coverage for {@code GitOpsSshServiceResource}, a plain CRUD dependent resource
 * (activated only when GitOps push mode is configured) that is not migrated in the mock-server
 * test tier. Only resource create/remove/recreate is verified here - the actual SSH git push
 * flow requires a running sidecar and remains functional-test territory, not covered by this
 * class.
 */
@QuarkusTest
public class GitOpsSshServiceReconcileTest extends MockServerTestBase {

    @Test
    void sshServiceLifecycleFollowsPushMode() {
        var registry = ResourceFactory.deserialize("/k8s/examples/gitops/example-push.yaml",
                ApicurioRegistry3.class);
        createRegistry(registry);

        String sshServiceName = registry.getMetadata().getName() + "-gitops-ssh-service";

        // Push mode: the SSH service is created alongside the app deployment/service.
        awaitDeploymentExists(deploymentName(registry, "app"));
        var sshService = awaitResourceExists(sshServiceName,
                () -> client.services().inNamespace(namespace).withName(sshServiceName).get());
        assertThat(sshService.getSpec().getPorts()).extracting(
                io.fabric8.kubernetes.api.model.ServicePort::getPort).contains(2222);

        // Switch away from GitOps storage: the SSH service must be removed.
        updateRegistry(registry, r -> {
            r.getSpec().getApp().getStorage().setType(null);
            r.getSpec().getApp().getStorage().setGitops(null);
        });
        awaitResourceAbsent(() -> client.services().inNamespace(namespace).withName(sshServiceName).get());

        // Switch back to GitOps push mode: the SSH service must be recreated.
        var pushSpec = ResourceFactory.deserialize("/k8s/examples/gitops/example-push.yaml",
                ApicurioRegistry3.class).getSpec().getApp().getStorage();
        updateRegistry(registry, r -> {
            r.getSpec().getApp().getStorage().setType(StorageType.GITOPS);
            r.getSpec().getApp().getStorage().setGitops(pushSpec.getGitops());
        });
        awaitResourceExists(sshServiceName,
                () -> client.services().inNamespace(namespace).withName(sshServiceName).get());
    }

    @Test
    void sshServiceNotCreatedInPullMode() {
        var registry = ResourceFactory.deserialize("/k8s/examples/gitops/example-push.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setName("gitops-pull");
        registry.getSpec().getApp().getStorage().getGitops().setMode(
                io.apicurio.registry.operator.api.v1.spec.GitOpsMode.PULL);
        createRegistry(registry);

        awaitDeploymentExists(deploymentName(registry, "app"));

        String sshServiceName = registry.getMetadata().getName() + "-gitops-ssh-service";
        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() ->
                assertThat(client.services().inNamespace(namespace).withName(sshServiceName).get()).isNull());
    }
}
