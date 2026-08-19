package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Mock-server equivalent of {@code CRUpdateITTest}: verifies that a deprecated CR spec is
 * automatically migrated to the current form on creation.
 */
@QuarkusTest
public class CRUpdateReconcileTest extends MockServerTestBase {

    @Test
    void crSpecMigrated() {
        var deprecated = ResourceFactory.deserialize(
                "/k8s/examples/simple-deprecated.apicurioregistry3.yaml", ApicurioRegistry3.class);
        var expectedSpec = ResourceFactory.deserialize(
                "/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class).getSpec();

        createRegistry(deprecated);

        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL)
                .ignoreExceptionsInstanceOf(KubernetesClientException.class)
                .untilAsserted(() -> {
                    var updated = client.resources(ApicurioRegistry3.class)
                            .inNamespace(namespace).list().getItems().stream()
                            .filter(r -> r.getMetadata().getName().equals(deprecated.getMetadata().getName()))
                            .toList();
                    assertThat(updated).hasSize(1);
                    assertThat(updated.get(0).getSpec())
                            .usingRecursiveComparison()
                            .isEqualTo(expectedSpec);
                });
    }
}
