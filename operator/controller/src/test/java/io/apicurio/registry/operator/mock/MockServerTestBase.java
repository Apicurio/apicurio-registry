package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.App;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.utils.Cell;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.apicurio.registry.utils.Cell.cell;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Base class for operator reconciliation tests that run against a Fabric8 CRUD mock Kubernetes
 * API server instead of a real cluster. Each test method gets a fresh namespace; the operator is
 * started before every method and stopped after.
 *
 * <p>
 * <b>SSA coverage disclaimer:</b> the Fabric8 CRUD mock server does not implement Server-Side
 * Apply. All dependent resource classes in this operator already extend
 * {@code CRUDKubernetesDependentResource}, so CRUD semantics match the mock. However, any
 * SSA-specific behavior (field-manager ownership, conflict resolution, annotation merge) requires
 * the real-cluster IT suite.
 *
 * <p>
 * Tests that verify pod readiness, real HTTP endpoints, or external infrastructure (Kafka,
 * Keycloak, databases) must remain in the IT suite.
 */
@WithKubernetesTestServer(crud = true)
public abstract class MockServerTestBase {

    protected static final Duration MOCK_TIMEOUT = ofSeconds(10);
    protected static final Duration MOCK_POLL = ofMillis(50);
    protected static final Duration MOCK_STABILITY = ofSeconds(3);

    @Inject
    protected KubernetesClient client;

    protected String namespace;

    private App app;

    @BeforeEach
    void setUpMockBase() {
        namespace = "test-" + UUID.randomUUID().toString().substring(0, 7);
        client.resource(new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build())
                .create();

        app = CDI.current().select(App.class).get();
        app.start(configOverride -> {
            configOverride.withKubernetesClient(client);
            configOverride.withUseSSAToPatchPrimaryResource(false);
            // Safety: disable SSA for all resource types the operator manages, in case the
            // mock server ever encounters a PATCH-upsert path.
            configOverride.withDefaultNonSSAResource(Set.of(
                    Deployment.class, io.fabric8.kubernetes.api.model.Service.class, Ingress.class,
                    HorizontalPodAutoscaler.class, NetworkPolicy.class, PodDisruptionBudget.class,
                    ServiceAccount.class, Role.class, RoleBinding.class
            ));
            // The injected KubernetesClient is a CDI-managed singleton owned by the
            // quarkus-test-kubernetes-client extension. Prevent the operator from closing it on
            // stop, since the client is shared across test methods.
            configOverride.withCloseClientOnStop(false);
        });
    }

    @AfterEach
    void tearDownMockBase() {
        // Stop the JOSDK ControllerManager so informers/watches registered by this test's
        // App instance are torn down. Without this, each test would accumulate a new live
        // reconciler watching all namespaces on the shared mock server.
        app.stop();
    }

    // ---- Assertion helpers ---------------------------------------------------------------

    protected void awaitDeploymentExists(String name) {
        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() ->
                assertThat(client.apps().deployments().inNamespace(namespace).withName(name).get())
                        .isNotNull());
    }

    protected void awaitDeploymentSpecReplicas(String name, int replicas) {
        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() ->
                assertThat(client.apps().deployments().inNamespace(namespace).withName(name).get()
                        .getSpec().getReplicas()).isEqualTo(replicas));
    }

    protected void awaitDeploymentAbsent(String name) {
        await().during(MOCK_STABILITY).atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions()
                .untilAsserted(() ->
                        assertThat(client.apps().deployments().inNamespace(namespace).withName(name).get())
                                .isNull());
    }

    protected void awaitServiceExists(String name) {
        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() ->
                assertThat(client.services().inNamespace(namespace).withName(name).get()).isNotNull());
    }

    protected void awaitServiceAbsent(String name) {
        await().during(MOCK_STABILITY).atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions()
                .untilAsserted(() ->
                        assertThat(client.services().inNamespace(namespace).withName(name).get()).isNull());
    }

    protected void awaitIngressExists(String name) {
        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() ->
                assertThat(client.network().v1().ingresses().inNamespace(namespace).withName(name).get())
                        .isNotNull());
    }

    protected void awaitIngressAbsent(String name) {
        await().during(MOCK_STABILITY).atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions()
                .untilAsserted(() ->
                        assertThat(client.network().v1().ingresses().inNamespace(namespace).withName(name).get())
                                .isNull());
    }

    protected <T extends HasMetadata> T awaitResourceExists(
            String name, Supplier<T> fetcher) {
        Cell<T> result = cell();
        await().atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions().untilAsserted(() -> {
            T resource = fetcher.get();
            assertThat(resource).isNotNull();
            result.set(resource);
        });
        return result.get();
    }

    protected <T extends HasMetadata> void awaitResourceAbsent(Supplier<T> fetcher) {
        await().during(MOCK_STABILITY).atMost(MOCK_TIMEOUT).pollInterval(MOCK_POLL).ignoreExceptions()
                .untilAsserted(() -> assertThat(fetcher.get()).isNull());
    }

    protected ApicurioRegistry3 createRegistry(ApicurioRegistry3 registry) {
        registry.getMetadata().setNamespace(namespace);
        return client.resource(registry).inNamespace(namespace).create();
    }

    protected ApicurioRegistry3 updateRegistry(ApicurioRegistry3 registry, Consumer<ApicurioRegistry3> updater) {
        await().atMost(ofSeconds(30)).until(() -> {
            try {
                updater.accept(registry);
                client.resource(registry).inNamespace(namespace).update();
                return true;
            } catch (Exception ex) {
                if (ex.getMessage() != null && ex.getMessage().contains("modified")) {
                    var fresh = client.resource(registry).inNamespace(namespace).get();
                    registry.setMetadata(fresh.getMetadata());
                    return false;
                }
                throw ex;
            }
        });
        return client.resource(registry).inNamespace(namespace).get();
    }

    protected String deploymentName(ApicurioRegistry3 registry, String component) {
        return registry.getMetadata().getName() + "-" + component + "-deployment";
    }

    protected String serviceName(ApicurioRegistry3 registry, String component) {
        return registry.getMetadata().getName() + "-" + component + "-service";
    }

    protected String ingressName(ApicurioRegistry3 registry, String component) {
        return registry.getMetadata().getName() + "-" + component + "-ingress";
    }
}
