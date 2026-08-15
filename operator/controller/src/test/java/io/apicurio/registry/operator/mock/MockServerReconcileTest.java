package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.App;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * POC: reconciles an {@link ApicurioRegistry3} CR against a fabric8 mock Kubernetes API server
 * (quarkus-test-kubernetes-client) instead of a real cluster. Covers the same create/update
 * seams as SmokeITTest and SmokeITTest#replicas(), but without booting real pods, so it runs in
 * milliseconds instead of minutes. Only the reconciler's desired-state output is verified here -
 * whether the resulting pods actually become healthy on a real kubelet is left to the existing
 * ITTest suite.
 */
@QuarkusTest
@WithKubernetesTestServer(crud = true)
public class MockServerReconcileTest {

    @Inject
    KubernetesClient client;

    private String namespace;

    @BeforeEach
    void setUp() {
        namespace = "test-" + UUID.randomUUID().toString().substring(0, 7);
        client.resource(new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build())
                .create();

        var app = CDI.current().select(App.class).get();
        app.start(configOverride -> {
            configOverride.withKubernetesClient(client);
            configOverride.withUseSSAToPatchPrimaryResource(false);
            // The fabric8 CRUD mock server doesn't implement Server-Side Apply (a PATCH to a
            // resource that doesn't exist yet 404s instead of upserting, unlike a real API
            // server), so dependent resources of these types must fall back to plain create/update.
            configOverride.withDefaultNonSSAResource(Set.of(Deployment.class, Service.class, Ingress.class));
        });
    }

    @Test
    void createsAppDeploymentAndService() {
        var registry = newRegistry("mock-smoke");
        client.resource(registry).inNamespace(namespace).create();

        await().atMost(ofSeconds(10)).pollInterval(ofMillis(50)).ignoreExceptions().untilAsserted(() -> {
            var deployment = client.apps().deployments().inNamespace(namespace)
                    .withName("mock-smoke-app-deployment").get();
            assertThat(deployment).isNotNull();
            assertThat(deployment.getSpec().getReplicas()).isEqualTo(1);

            var service = client.services().inNamespace(namespace)
                    .withName("mock-smoke-app-service").get();
            assertThat(service).isNotNull();
        });
    }

    @Test
    void scalesAppDeploymentOnReplicaUpdate() {
        var registry = newRegistry("mock-scale");
        client.resource(registry).inNamespace(namespace).create();

        await().atMost(ofSeconds(10)).pollInterval(ofMillis(50)).ignoreExceptions().untilAsserted(() ->
                assertThat(client.apps().deployments().inNamespace(namespace)
                        .withName("mock-scale-app-deployment").get()).isNotNull());

        registry.getSpec().getApp().setReplicas(3);
        client.resource(registry).inNamespace(namespace).update();

        await().atMost(ofSeconds(10)).pollInterval(ofMillis(50)).ignoreExceptions().untilAsserted(() -> {
            Deployment deployment = client.apps().deployments().inNamespace(namespace)
                    .withName("mock-scale-app-deployment").get();
            assertThat(deployment.getSpec().getReplicas()).isEqualTo(3);
        });
    }

    private ApicurioRegistry3 newRegistry(String name) {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setName(name);
        registry.getMetadata().setNamespace(namespace);
        return registry;
    }
}
