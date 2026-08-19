package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.resource.ResourceFactory.deserialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Mock-server equivalent of the spec-propagation part of {@code IngressITTest}.
 *
 * <p>
 * The {@code ingressAnnotations()} test from the IT suite exercises SSA field-manager semantics
 * (non-managed annotations are preserved across reconciliations) and is not migrated here.
 */
@QuarkusTest
public class IngressReconcileTest extends MockServerTestBase {

    @Test
    void ingressClassName() {
        var primary = deserialize("/k8s/examples/ingress/ingress-class-name.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        primary.getSpec().getApp().getIngress().setHost("app.example.com");
        primary.getSpec().getUi().getIngress().setHost("ui.example.com");
        createRegistry(primary);

        // Initial class names from the CR
        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(ingressName(primary, COMPONENT_APP)).get()).isNotNull();
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(ingressName(primary, COMPONENT_APP)).get()
                    .getSpec().getIngressClassName()).isEqualTo("haproxy-app");

            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(ingressName(primary, COMPONENT_UI)).get()).isNotNull();
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(ingressName(primary, COMPONENT_UI)).get()
                    .getSpec().getIngressClassName()).isEqualTo("haproxy-ui");
        });

        // Update app class name
        updateRegistry(primary, p -> p.getSpec().getApp().getIngress().setIngressClassName("test---nginx"));

        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() ->
                assertThat(client.network().v1().ingresses().inNamespace(namespace)
                        .withName(ingressName(primary, COMPONENT_APP)).get()
                        .getSpec().getIngressClassName()).isEqualTo("test---nginx"));

        // UI class name unchanged
        assertThat(client.network().v1().ingresses().inNamespace(namespace)
                .withName(ingressName(primary, COMPONENT_UI)).get()
                .getSpec().getIngressClassName()).isEqualTo("haproxy-ui");

        // Clear app class name
        updateRegistry(primary, p -> p.getSpec().getApp().getIngress().setIngressClassName(""));

        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() ->
                assertThat(client.network().v1().ingresses().inNamespace(namespace)
                        .withName(ingressName(primary, COMPONENT_APP)).get()
                        .getSpec().getIngressClassName()).isNotEqualTo("test---nginx"));
    }
}
