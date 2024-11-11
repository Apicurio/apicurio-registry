package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static io.apicurio.registry.operator.resource.ResourceFactory.UI_CONTAINER_NAME;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class SmokeITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(SmokeITTest.class);

    @Test
    void demoDeployment() {

        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().setHost(ingressManager.getIngressHost("app"));
        registry.getSpec().getUi().setHost(ingressManager.getIngressHost("ui"));

        client.resource(registry).create();

        // Deployments
        await().ignoreExceptions().until(() -> {
            assertThat(client.apps().deployments().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-deployment").get().getStatus()
                    .getReadyReplicas()).isEqualTo(1);
            assertThat(client.apps().deployments().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-ui-deployment").get().getStatus()
                    .getReadyReplicas()).isEqualTo(1);
            return true;
        });

        // Services
        await().ignoreExceptions().until(() -> {
            assertThat(client.services().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-service").get().getSpec()
                    .getClusterIP()).isNotBlank();
            assertThat(client.services().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-ui-service").get().getSpec()
                    .getClusterIP()).isNotBlank();
            return true;
        });

        // Ingresses
        await().ignoreExceptions().until(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-ingress").get().getSpec().getRules()
                    .get(0).getHost()).isEqualTo(registry.getSpec().getApp().getHost());
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-ui-ingress").get().getSpec().getRules()
                    .get(0).getHost()).isEqualTo(registry.getSpec().getUi().getHost());
            return true;
        });
    }

    @Test
    void testService() {

        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().setHost(ingressManager.getIngressHost("app"));
        registry.getSpec().getUi().setHost(ingressManager.getIngressHost("ui"));

        client.resource(registry).create();

        // Wait for Services
        await().ignoreExceptions().until(() -> {
            assertThat(client.services().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-service").get().getSpec()
                    .getClusterIP()).isNotBlank();
            assertThat(client.services().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-ui-service").get().getSpec()
                    .getClusterIP()).isNotBlank();
            return true;
        });

        int appServicePort = portForwardManager
                .startPortForward(registry.getMetadata().getName() + "-app-service", 8080);

        await().ignoreExceptions().until(() -> {
            given().get(new URI("http://localhost:" + appServicePort + "/apis/registry/v3/system/info"))
                    .then().statusCode(200);
            return true;
        });

        int uiServicePort = portForwardManager
                .startPortForward(registry.getMetadata().getName() + "-ui-service", 8080);

        await().ignoreExceptions().until(() -> {
            given().get(new URI("http://localhost:" + uiServicePort + "/config.js")).then().statusCode(200);
            return true;
        });
    }

    @Test
    @DisabledIfSystemProperty(named = INGRESS_SKIP_PROP, matches = "true")
    void testIngress() {

        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().setHost(ingressManager.getIngressHost("app"));
        registry.getSpec().getUi().setHost(ingressManager.getIngressHost("ui"));

        client.resource(registry).create();

        // Wait for Ingresses
        await().untilAsserted(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-ingress").get()).isNotNull();
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-ui-ingress").get()).isNotNull();
        });

        await().ignoreExceptions().until(() -> {
            ingressManager.startHttpRequest(registry.getMetadata().getName() + "-app-ingress")
                    .basePath("/apis/registry/v3/system/info").get().then().statusCode(200);
            return true;
        });

        await().ignoreExceptions().until(() -> {
            ingressManager.startHttpRequest(registry.getMetadata().getName() + "-ui-ingress")
                    .basePath("/config.js").get().then().statusCode(200);
            return true;
        });
    }

    @Test
    void testEmptyHostDisablesIngress() {

        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().setHost(ingressManager.getIngressHost("app"));
        registry.getSpec().getUi().setHost(ingressManager.getIngressHost("ui"));

        client.resource(registry).create();

        // Wait for Ingresses
        await().untilAsserted(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-ingress").get()).isNotNull();
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-ui-ingress").get()).isNotNull();
        });

        // Check that REGISTRY_API_URL is set
        var uiDeployment = client.apps().deployments().inNamespace(namespace)
                .withName(registry.getMetadata().getName() + "-ui-deployment").get();
        verify_REGISTRY_API_URL_isSet(registry, uiDeployment);

        // Disable host and therefore Ingress
        registry.getSpec().getApp().setHost("");
        registry.getSpec().getUi().setHost("");

        // TODO: The remote test does not work properly. As a workaround the CR will be deleted and recreated
        // instead of updated:
        // client.resource(registry).update();
        client.resource(registry).delete();
        await().untilAsserted(() -> {
            assertThat(client.resource(registry).get()).isNull();
        });
        client.resource(registry).create();

        await().untilAsserted(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-ingress").get()).isNull();
        });
        await().untilAsserted(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-ui-ingress").get()).isNull();
        });

        uiDeployment = client.apps().deployments().inNamespace(namespace)
                .withName(registry.getMetadata().getName() + "-ui-deployment").get();
        assertThat(uiDeployment).isNotNull();
        // spotless:off
        assertThat(uiDeployment.getSpec().getTemplate().getSpec().getContainers())
                .filteredOn(c -> UI_CONTAINER_NAME.equals(c.getName()))
                .flatMap(Container::getEnv)
                .filteredOn(e -> "REGISTRY_API_URL".equals(e.getName()))
                .isEmpty();
        // spotless:on

        // Enable again
        registry.getSpec().getApp().setHost(ingressManager.getIngressHost("app"));
        registry.getSpec().getUi().setHost(ingressManager.getIngressHost("ui"));
        client.resource(registry).update();

        // Verify Ingresses are back
        await().untilAsserted(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-ingress").get()).isNotNull();
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-ui-ingress").get()).isNotNull();
        });

        // Check that REGISTRY_API_URL is set again
        uiDeployment = client.apps().deployments().inNamespace(namespace)
                .withName(registry.getMetadata().getName() + "-ui-deployment").get();
        verify_REGISTRY_API_URL_isSet(registry, uiDeployment);
    }

    private void verify_REGISTRY_API_URL_isSet(ApicurioRegistry3 registry, Deployment deployment) {
        // spotless:off
        assertThat(deployment).isNotNull();
        assertThat(deployment.getSpec().getTemplate().getSpec().getContainers())
                .filteredOn(c -> UI_CONTAINER_NAME.equals(c.getName()))
                .flatMap(Container::getEnv)
                .filteredOn(e -> "REGISTRY_API_URL".equals(e.getName()))
                .hasSize(1)
                .map(EnvVar::getValue)
                .first()
                .asInstanceOf(InstanceOfAssertFactories.STRING)
                .startsWith("http://" + registry.getSpec().getApp().getHost());
        // spotless:on
    }
}
