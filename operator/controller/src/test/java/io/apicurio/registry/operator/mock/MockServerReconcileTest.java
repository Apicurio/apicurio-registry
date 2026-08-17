package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.App;
import io.apicurio.registry.operator.Configuration;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.Labels.getSelectorLabels;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
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
 *
 * <p>
 * <b>Server-Side Apply coverage disclaimer:</b> the fabric8 CRUD mock server does not implement
 * SSA (a PATCH to a resource that doesn't exist yet 404s instead of upserting, unlike a real API
 * server), so this POC overrides {@code Deployment}/{@code Service}/{@code Ingress} dependent
 * resources to use plain create/update via {@code withDefaultNonSSAResource} - a test-only
 * override; no production reconciliation code is changed to accommodate the mock. These tests do
 * exercise the real JOSDK reconciliation and dependent-resource workflow end to end, but because
 * SSA is disabled here, they provide <b>no coverage of production SSA behavior</b> (field-manager
 * ownership, conflict resolution, merge semantics). Real-cluster integration tests
 * ({@link io.apicurio.registry.operator.it.SmokeITTest} and friends) remain necessary for
 * SSA-specific behavior; this suite must not be read as a replacement for them.
 */
@QuarkusTest
@WithKubernetesTestServer(crud = true)
public class MockServerReconcileTest {

    @Inject
    KubernetesClient client;

    private String namespace;

    private App app;

    @BeforeEach
    void setUp() {
        namespace = "test-" + UUID.randomUUID().toString().substring(0, 7);
        client.resource(new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build())
                .create();

        app = CDI.current().select(App.class).get();
        app.start(configOverride -> {
            configOverride.withKubernetesClient(client);
            configOverride.withUseSSAToPatchPrimaryResource(false);
            // The fabric8 CRUD mock server doesn't implement Server-Side Apply (a PATCH to a
            // resource that doesn't exist yet 404s instead of upserting, unlike a real API
            // server), so dependent resources of these types must fall back to plain create/update.
            configOverride.withDefaultNonSSAResource(Set.of(Deployment.class, Service.class, Ingress.class));
            // The injected KubernetesClient is a CDI-managed singleton owned by the
            // quarkus-test-kubernetes-client extension and is shared across every test method in
            // this class. Operator#stop() closes the client it was configured with by default
            // (JOSDK's ConfigurationService#closeClientOnStop() defaults to true), which would
            // leave the client unusable for the next test. The client's lifecycle belongs to the
            // test framework here, not to this operator instance, so closing it on stop is
            // disabled.
            configOverride.withCloseClientOnStop(false);
        });
    }

    @AfterEach
    void tearDown() {
        // Stops the JOSDK ControllerManager (informers/watches) that this test's App/Operator
        // instance registered. App is an @ApplicationScoped CDI bean, so without this, every test
        // after the first would accumulate another live reconciler still watching all namespaces
        // on the same mock server, alongside the previous test's now-orphaned one.
        app.stop();
    }

    @Test
    void createsAppDeploymentAndService() {
        var registry = newRegistry("mock-smoke");
        client.resource(registry).inNamespace(namespace).create();
        var expectedSelector = getSelectorLabels(registry, COMPONENT_APP);

        await().atMost(ofSeconds(10)).pollInterval(ofMillis(50)).ignoreExceptions().untilAsserted(() -> {
            var deployment = client.apps().deployments().inNamespace(namespace)
                    .withName("mock-smoke-app-deployment").get();
            assertThat(deployment).isNotNull();
            assertThat(deployment.getSpec().getReplicas()).isEqualTo(1);

            var container = getContainerFromDeployment(deployment, REGISTRY_APP_CONTAINER_NAME);
            assertThat(container.getImage()).isEqualTo(Configuration.getAppImage());
            assertThat(deployment.getSpec().getTemplate().getMetadata().getLabels())
                    .containsAllEntriesOf(expectedSelector);

            var service = client.services().inNamespace(namespace)
                    .withName("mock-smoke-app-service").get();
            assertThat(service).isNotNull();
            assertThat(service.getSpec().getSelector()).isEqualTo(expectedSelector);
            // The Service must actually select the Deployment's Pods, not merely declare a
            // same-looking selector independently.
            assertThat(deployment.getSpec().getTemplate().getMetadata().getLabels())
                    .containsAllEntriesOf(service.getSpec().getSelector());
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

        // ApicurioRegistry3Reconciler's @ControllerConfiguration sets no periodic resync
        // interval, so the informer only re-reconciles on a real watch event. The fabric8 CRUD
        // mock server (crud = true, as opposed to a plain expectation stub) replays
        // create/update/delete operations as watch notifications, which is what actually drives
        // this reconciliation - there is no resync fallback that could let this assertion pass
        // "late" on a fluke; if watch delivery broke, this would time out and fail every run.
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
