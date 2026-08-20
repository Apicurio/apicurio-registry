package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Mock-server coverage for {@code IngressCRUpdater} branches not exercised by
 * {@code CRUpdateReconcileTest}, which only covers the case where the deprecated {@code host}
 * field is set and the current {@code ingress.host} field is empty.
 */
@QuarkusTest
public class CRUpdateEdgeCasesReconcileTest extends MockServerTestBase {

    /**
     * When both the deprecated {@code host} field and the current {@code ingress.host} field are
     * set to different values, {@code IngressCRUpdater} deliberately declines to auto-migrate
     * (it would silently discard an explicit value the user just set). The spec must be left
     * exactly as submitted, while the rendered Ingress must still use the current field.
     */
    @Test
    void conflictingHostFieldsAreNotAutoMigrated() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setName("cr-update-conflict");
        registry.getSpec().getApp().setHost("deprecated.apps.cluster.example");
        registry.getSpec().getApp().getIngress().setHost("current.apps.cluster.example");
        createRegistry(registry);

        awaitIngressExists(ingressName(registry, COMPONENT_APP));

        // The rendered Ingress uses the current field...
        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() ->
                assertThat(client.network().v1().ingresses().inNamespace(namespace)
                        .withName(ingressName(registry, COMPONENT_APP)).get()
                        .getSpec().getRules().get(0).getHost())
                        .isEqualTo("current.apps.cluster.example"));

        // ...while the CR spec itself is left untouched: both fields remain exactly as
        // submitted, proving the deprecated value was not silently overwritten or discarded.
        await().during(MOCK_STABILITY).atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions()
                .untilAsserted(() -> {
                    var cr = client.resources(ApicurioRegistry3.class).inNamespace(namespace)
                            .withName("cr-update-conflict").get();
                    assertThat(cr.getSpec().getApp().getHost()).isEqualTo("deprecated.apps.cluster.example");
                    assertThat(cr.getSpec().getApp().getIngress().getHost())
                            .isEqualTo("current.apps.cluster.example");
                });
    }

    /**
     * A CR already in the current (post-migration) form must remain stable across the natural
     * reconciliation loop: no spurious re-patch should occur just because the operator keeps
     * reconciling in response to its own writes (status updates, etc.).
     */
    @Test
    void alreadyMigratedSpecIsStableAcrossReconciliation() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setName("cr-update-stable");
        createRegistry(registry);

        awaitIngressExists(ingressName(registry, COMPONENT_APP));

        await().during(MOCK_STABILITY).atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions()
                .untilAsserted(() -> {
                    var cr = client.resources(ApicurioRegistry3.class).inNamespace(namespace)
                            .withName("cr-update-stable").get();
                    assertThat(cr.getSpec().getApp().getHost()).isNull();
                    assertThat(cr.getSpec().getApp().getIngress().getHost())
                            .isEqualTo("simple-app.apps.cluster.example");
                });
    }
}
