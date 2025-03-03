package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyIngressRule;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class TlsITTest extends ITBase {

    @BeforeAll
    public static void setup() {
        List<HasMetadata> resources = Serialization
                .unmarshal(AuthITTest.class.getResourceAsStream("/k8s/examples/tls/secrets.yaml"));

        createResources(resources, "tls-secrets");
    }

    /**
     * In this test, the server uses a Keystore in PCKS format that identifies itself to the client.
     */
    @Test
    void testTLS() {
        var registry = ResourceFactory.deserialize("/k8s/examples/tls/simple-with_tls.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
        registry.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

        client.resource(registry).create();

        // Deployments
        await().ignoreExceptions().until(() -> {
            assertThat(client.apps().deployments().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-deployment").get().getStatus()
                    .getReadyReplicas()).isEqualTo(1);
            assertThat(client.apps().deployments().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-ui-deployment").get().getStatus()
                    .getReadyReplicas()).isEqualTo(1);

            var appEnv = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(registry.getMetadata().getName() + "-app-deployment").get(),
                    REGISTRY_APP_CONTAINER_NAME).getEnv();

            assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                    .contains(EnvironmentVariables.QUARKUS_HTTP_INSECURE_REQUESTS + "=" + "disabled");

            return true;
        });

        // Services
        await().ignoreExceptions().until(() -> {

            var service = client.services().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-service").get().getSpec();

            assertThat(service.getClusterIP()).isNotBlank();
            assertThat(service.getPorts().get(0).getPort()).isEqualTo(443);
            assertThat(service.getClusterIP()).isNotBlank();
            return true;
        });

        // Ingresses
        await().ignoreExceptions().until(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-ingress").get().getSpec().getRules()
                    .get(0).getHost()).isEqualTo(registry.getSpec().getApp().getIngress().getHost());
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-ui-ingress").get().getSpec().getRules()
                    .get(0).getHost()).isEqualTo(registry.getSpec().getUi().getIngress().getHost());
            return true;
        });

        // Network Policy
        await().ignoreExceptions().until(() -> {
            assertThat(client.network().v1().networkPolicies().inNamespace(namespace)
                    .withName("simple-app-networkpolicy").get().getSpec().getIngress()
                    .get(0).getPorts().get(0).getPort().getIntVal()).isEqualTo(8443);
            return true;
        });
    }

    /**
     * In this test, the server uses a Keystore in PCKS format that identifies itself to the client.
     */
    @Test
    void testTLSInsecureTrafficEnabled() {
        var registry = ResourceFactory.deserialize("/k8s/examples/tls/insecure-traffic-with_tls.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
        registry.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

        client.resource(registry).create();

        // Deployments
        await().ignoreExceptions().until(() -> {
            assertThat(client.apps().deployments().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-deployment").get().getStatus()
                    .getReadyReplicas()).isEqualTo(1);
            assertThat(client.apps().deployments().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-ui-deployment").get().getStatus()
                    .getReadyReplicas()).isEqualTo(1);

            var appEnv = getContainerFromDeployment(
                    client.apps().deployments().inNamespace(namespace)
                            .withName(registry.getMetadata().getName() + "-app-deployment").get(),
                    REGISTRY_APP_CONTAINER_NAME).getEnv();

            assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                    .contains(EnvironmentVariables.QUARKUS_HTTP_INSECURE_REQUESTS + "=" + "enabled");

            return true;
        });

        // Services
        await().ignoreExceptions().until(() -> {
            var service = client.services().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-service").get().getSpec();

            assertThat(service.getClusterIP()).isNotBlank();
            assertThat(service.getPorts().get(0).getPort()).isEqualTo(443);
            assertThat(service.getPorts().get(1).getPort()).isEqualTo(8080);
            assertThat(service.getClusterIP()).isNotBlank();
            return true;
        });

        // Ingresses
        await().ignoreExceptions().until(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-ingress").get().getSpec().getRules()
                    .get(0).getHost()).isEqualTo(registry.getSpec().getApp().getIngress().getHost());
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-ui-ingress").get().getSpec().getRules()
                    .get(0).getHost()).isEqualTo(registry.getSpec().getUi().getIngress().getHost());
            return true;
        });

        // Network Policy
        await().ignoreExceptions().until(() -> {
            NetworkPolicyIngressRule networkPolicyIngressRule = client.network().v1().networkPolicies().inNamespace(namespace)
                    .withName("simple-app-networkpolicy").get().getSpec().getIngress()
                    .get(0);
            assertThat(networkPolicyIngressRule.getPorts().get(0).getPort().getIntVal()).isEqualTo(8443);
            assertThat(networkPolicyIngressRule.getPorts().get(1).getPort().getIntVal()).isEqualTo(8080);
            return true;
        });
    }
}
