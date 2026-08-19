package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_UI_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.Labels.getSelectorLabels;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Mock-server variants of the spec-output-only tests from {@code SmokeITTest}.
 * Tests that require running pods ({@code testService}, {@code testIngress}) remain in the IT suite.
 */
@QuarkusTest
public class SmokeReconcileTest extends MockServerTestBase {

    @Test
    void smoke() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        createRegistry(registry);

        var expectedSelectorApp = getSelectorLabels(registry, COMPONENT_APP);
        var expectedSelectorUi = getSelectorLabels(registry, COMPONENT_UI);

        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() -> {
            // App Deployment exists with correct replica count and image
            var appDeployment = client.apps().deployments().inNamespace(namespace)
                    .withName(deploymentName(registry, COMPONENT_APP)).get();
            assertThat(appDeployment).isNotNull();
            assertThat(appDeployment.getSpec().getReplicas()).isEqualTo(1);
            assertThat(appDeployment.getSpec().getTemplate().getMetadata().getLabels())
                    .containsAllEntriesOf(expectedSelectorApp);

            // UI Deployment
            var uiDeployment = client.apps().deployments().inNamespace(namespace)
                    .withName(deploymentName(registry, COMPONENT_UI)).get();
            assertThat(uiDeployment).isNotNull();
            assertThat(uiDeployment.getSpec().getReplicas()).isEqualTo(1);
            assertThat(uiDeployment.getSpec().getTemplate().getMetadata().getLabels())
                    .containsAllEntriesOf(expectedSelectorUi);

            // Services
            assertThat(client.services().inNamespace(namespace)
                    .withName(serviceName(registry, COMPONENT_APP)).get()).isNotNull();
            assertThat(client.services().inNamespace(namespace)
                    .withName(serviceName(registry, COMPONENT_UI)).get()).isNotNull();

            // Ingresses
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(ingressName(registry, COMPONENT_APP)).get()).isNotNull();
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(ingressName(registry, COMPONENT_UI)).get()).isNotNull();
        });

        // CORS: allowed origins set from the UI ingress host
        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() -> {
            String uiHost = client.network().v1().ingresses().inNamespace(namespace)
                    .withName(ingressName(registry, COMPONENT_UI)).get()
                    .getSpec().getRules().get(0).getHost();
            String expectedCors = "http://" + uiHost + "," + "https://" + uiHost;
            var appEnv = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(deploymentName(registry, COMPONENT_APP)).get(),
                    REGISTRY_APP_CONTAINER_NAME).getEnv();
            assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                    .contains(EnvironmentVariables.QUARKUS_HTTP_CORS_ORIGINS + "=" + expectedCors);
        });
    }

    @Test
    void replicas() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        createRegistry(registry);

        // Initial: 1 replica each
        awaitDeploymentSpecReplicas(deploymentName(registry, COMPONENT_APP), 1);
        awaitDeploymentSpecReplicas(deploymentName(registry, COMPONENT_UI), 1);

        // Scale up
        updateRegistry(registry, r -> {
            r.getSpec().getApp().setReplicas(3);
            r.getSpec().getUi().setReplicas(3);
        });
        awaitDeploymentSpecReplicas(deploymentName(registry, COMPONENT_APP), 3);
        awaitDeploymentSpecReplicas(deploymentName(registry, COMPONENT_UI), 3);

        // Scale down
        updateRegistry(registry, r -> {
            r.getSpec().getApp().setReplicas(2);
            r.getSpec().getUi().setReplicas(2);
        });
        awaitDeploymentSpecReplicas(deploymentName(registry, COMPONENT_APP), 2);
        awaitDeploymentSpecReplicas(deploymentName(registry, COMPONENT_UI), 2);
    }

    @Test
    void emptyHostDisablesIngress() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getSpec().getApp().getIngress().setHost("app.example.com");
        registry.getSpec().getUi().getIngress().setHost("ui.example.com");
        createRegistry(registry);

        awaitIngressExists(ingressName(registry, COMPONENT_APP));
        awaitIngressExists(ingressName(registry, COMPONENT_UI));

        // Check that REGISTRY_API_URL is set while ingress is enabled
        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() -> {
            var uiDeployment = client.apps().deployments().inNamespace(namespace)
                    .withName(deploymentName(registry, COMPONENT_UI)).get();
            assertThat(uiDeployment).isNotNull();
            assertThat(uiDeployment.getSpec().getTemplate().getSpec().getContainers())
                    .filteredOn(c -> REGISTRY_UI_CONTAINER_NAME.equals(c.getName()))
                    .flatMap(io.fabric8.kubernetes.api.model.Container::getEnv)
                    .filteredOn(e -> "REGISTRY_API_URL".equals(e.getName()))
                    .hasSize(1);
        });

        // Disable ingresses
        updateRegistry(registry, r -> {
            r.getSpec().getApp().getIngress().setHost("");
            r.getSpec().getUi().getIngress().setHost("");
        });

        awaitIngressAbsent(ingressName(registry, COMPONENT_APP));
        awaitIngressAbsent(ingressName(registry, COMPONENT_UI));

        // REGISTRY_API_URL should be gone
        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() -> {
            var uiDeployment = client.apps().deployments().inNamespace(namespace)
                    .withName(deploymentName(registry, COMPONENT_UI)).get();
            assertThat(uiDeployment).isNotNull();
            assertThat(uiDeployment.getSpec().getTemplate().getSpec().getContainers())
                    .filteredOn(c -> REGISTRY_UI_CONTAINER_NAME.equals(c.getName()))
                    .flatMap(io.fabric8.kubernetes.api.model.Container::getEnv)
                    .filteredOn(e -> "REGISTRY_API_URL".equals(e.getName()))
                    .isEmpty();
        });

        // Re-enable
        updateRegistry(registry, r -> {
            r.getSpec().getApp().getIngress().setHost("app.example.com");
            r.getSpec().getUi().getIngress().setHost("ui.example.com");
        });
        awaitIngressExists(ingressName(registry, COMPONENT_APP));
        awaitIngressExists(ingressName(registry, COMPONENT_UI));
    }
}
