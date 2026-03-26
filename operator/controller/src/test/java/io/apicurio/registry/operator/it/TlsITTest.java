package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.InsecureRequests;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyIngressRule;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static io.apicurio.registry.operator.Tags.FEATURE;
import static io.apicurio.registry.operator.Tags.FEATURE_SETUP;
import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Tag(FEATURE)
@Tag(FEATURE_SETUP)
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
            Assertions.assertEquals(1, service.getPorts().size());
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
            var networkPolicyIngressRules = client.network().v1().networkPolicies().inNamespace(namespace)
                    .withName("simple-app-networkpolicy").get().getSpec().getIngress();

            Assertions.assertEquals(2, networkPolicyIngressRules.size());

            assertThat(networkPolicyIngressRules)
                    .flatMap(NetworkPolicyIngressRule::getPorts)
                    .map(p -> p.getPort().getIntVal())
                    .containsExactlyInAnyOrder(8443, 9000);
            return true;
        });

        int appServicePort = portForwardManager
                .startServicePortForward(registry.getMetadata().getName() + "-app-service", 8443);

        await().ignoreExceptions().until(() -> {
            given().relaxedHTTPSValidation("TLS").get(new URI("https://localhost:" + appServicePort + "/apis/registry/v3/system/info"))
                    .then().statusCode(200);
            return true;
        });

        // Verify Ingress has backend port "https" since app-level TLS is configured (passthrough)
        await().ignoreExceptions().until(() -> {
            var appIngress = client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-ingress").get();

            assertThat(appIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0)
                    .getBackend().getService().getPort().getName()).isEqualTo("https");
            return true;
        });
    }

    /**
     * Test edge-terminated TLS: TLS is terminated at the Ingress, traffic to app is HTTP.
     */
    @Test
    void testEdgeTerminatedTLS() {
        var registry = ResourceFactory.deserialize("/k8s/examples/tls/edge-tls.apicurioregistry3.yaml",
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
            return true;
        });

        // Verify app Ingress has TLS section with correct secret, host, and OpenShift annotation
        await().ignoreExceptions().until(() -> {
            var appIngress = client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-ingress").get();

            assertThat(appIngress.getSpec().getTls()).hasSize(1);
            assertThat(appIngress.getSpec().getTls().get(0).getSecretName()).isEqualTo("my-tls-cert");
            assertThat(appIngress.getSpec().getTls().get(0).getHosts())
                    .contains(registry.getSpec().getApp().getIngress().getHost());

            // Backend port should be "http" since no app-level TLS is configured
            assertThat(appIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0)
                    .getBackend().getService().getPort().getName()).isEqualTo("http");

            // OpenShift Route termination annotation should be set
            assertThat(appIngress.getMetadata().getAnnotations())
                    .containsEntry("route.openshift.io/termination", "edge");

            return true;
        });

        // Verify UI Ingress has TLS section and annotation
        await().ignoreExceptions().until(() -> {
            var uiIngress = client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-ui-ingress").get();

            assertThat(uiIngress.getSpec().getTls()).hasSize(1);
            assertThat(uiIngress.getSpec().getTls().get(0).getSecretName()).isEqualTo("my-tls-cert");
            assertThat(uiIngress.getSpec().getTls().get(0).getHosts())
                    .contains(registry.getSpec().getUi().getIngress().getHost());

            assertThat(uiIngress.getMetadata().getAnnotations())
                    .containsEntry("route.openshift.io/termination", "edge");

            return true;
        });
    }

    /**
     * Test TLS passthrough: TLS is NOT terminated at the Ingress, app handles HTTPS.
     */
    @Test
    void testTLSPassthrough() {
        var registry = ResourceFactory.deserialize("/k8s/examples/tls/passthrough-tls.apicurioregistry3.yaml",
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
            return true;
        });

        // Verify app Ingress has TLS section, backend port "https", and passthrough annotation
        await().ignoreExceptions().until(() -> {
            var appIngress = client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-ingress").get();

            assertThat(appIngress.getSpec().getTls()).hasSize(1);
            assertThat(appIngress.getSpec().getTls().get(0).getSecretName()).isEqualTo("my-tls-cert");

            // Backend port should be "https" since app-level TLS is configured
            assertThat(appIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0)
                    .getBackend().getService().getPort().getName()).isEqualTo("https");

            // OpenShift Route termination annotation should be "passthrough"
            assertThat(appIngress.getMetadata().getAnnotations())
                    .containsEntry("route.openshift.io/termination", "passthrough");

            return true;
        });

        // Services should have HTTPS port
        await().ignoreExceptions().until(() -> {
            var service = client.services().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-service").get().getSpec();

            Assertions.assertEquals(1, service.getPorts().size());
            assertThat(service.getPorts().get(0).getPort()).isEqualTo(443);

            return true;
        });
    }

    /**
     * Test OpenShift edge-terminated TLS using default router certificate (no tlsSecretName).
     */
    @Test
    void testOpenShiftEdgeTLS() {
        var registry = ResourceFactory.deserialize("/k8s/examples/tls/openshift-edge-tls.apicurioregistry3.yaml",
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
            return true;
        });

        // Verify Ingress has TLS section (without secretName) and edge annotation
        await().ignoreExceptions().until(() -> {
            var appIngress = client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-ingress").get();

            assertThat(appIngress.getSpec().getTls()).hasSize(1);
            assertThat(appIngress.getSpec().getTls().get(0).getHosts())
                    .contains(registry.getSpec().getApp().getIngress().getHost());
            // No secretName since we use the default router cert
            assertThat(appIngress.getSpec().getTls().get(0).getSecretName()).isNull();

            // Backend port should be "http" (edge termination, not passthrough)
            assertThat(appIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0)
                    .getBackend().getService().getPort().getName()).isEqualTo("http");

            // OpenShift Route termination annotation
            assertThat(appIngress.getMetadata().getAnnotations())
                    .containsEntry("route.openshift.io/termination", "edge");

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
                    .contains(EnvironmentVariables.QUARKUS_HTTP_INSECURE_REQUESTS + "=" + InsecureRequests.ENABLED.getValue());

            return true;
        });

        // Services
        await().ignoreExceptions().until(() -> {
            var service = client.services().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-service").get().getSpec();

            assertThat(service.getClusterIP()).isNotBlank();
            assertThat(service.getPorts().get(0).getPort()).isEqualTo(443);
            assertThat(service.getPorts().get(1).getPort()).isEqualTo(8080);

            Assertions.assertEquals(2, service.getPorts().size());

            assertThat(service.getClusterIP()).isNotBlank();
            return true;
        });


        // Network Policy
        await().ignoreExceptions().until(() -> {
            var networkPolicyIngressRules = client.network().v1().networkPolicies().inNamespace(namespace)
                    .withName("simple-app-networkpolicy").get().getSpec().getIngress();

            Assertions.assertEquals(3, networkPolicyIngressRules.size());

            assertThat(networkPolicyIngressRules)
                    .flatMap(NetworkPolicyIngressRule::getPorts)
                    .map(p -> p.getPort().getIntVal())
                    .containsExactlyInAnyOrder(8080, 8443, 9000);
            return true;
        });

        int appServicePortInsecure = portForwardManager
                .startServicePortForward(registry.getMetadata().getName() + "-app-service", 8080);

        await().ignoreExceptions().until(() -> {
            given().get(new URI("http://localhost:" + appServicePortInsecure + "/apis/registry/v3/system/info"))
                    .then().statusCode(200);
            return true;
        });

        portForwardManager.stop(appServicePortInsecure);

        int appServicePort = portForwardManager
                .startServicePortForward(registry.getMetadata().getName() + "-app-service", 8443);

        await().ignoreExceptions().until(() -> {
            given().relaxedHTTPSValidation("TLS").get(new URI("https://localhost:" + appServicePort + "/apis/registry/v3/system/info"))
                    .then().statusCode(200);
            return true;
        });
    }
}
