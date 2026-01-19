package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.core.ConditionTimeoutException;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static io.apicurio.registry.operator.Tags.SMOKE;
import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_UI_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static io.apicurio.registry.operator.utils.K8sCell.k8sCellCreate;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Tag(SMOKE)
public class SmokeITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(SmokeITTest.class);

    @Test
    void smoke() {

        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
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
                    .get(0).getHost()).isEqualTo(registry.getSpec().getApp().getIngress().getHost());
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-ui-ingress").get().getSpec().getRules()
                    .get(0).getHost()).isEqualTo(registry.getSpec().getUi().getIngress().getHost());
            return true;
        });

        // Check CORS allowed origins is set on the app, with the value based on the UI ingress host
        String uiIngressHost = client.network().v1().ingresses().inNamespace(namespace)
                .withName(registry.getMetadata().getName() + "-ui-ingress").get().getSpec().getRules().get(0)
                .getHost();
        String corsOriginsExpectedValue = "http://" + uiIngressHost + "," + "https://" + uiIngressHost;
        var appEnv = getContainerFromDeployment(
                client.apps().deployments().inNamespace(namespace)
                        .withName(registry.getMetadata().getName() + "-app-deployment").get(),
                REGISTRY_APP_CONTAINER_NAME).getEnv();
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.QUARKUS_HTTP_CORS_ORIGINS + "=" + corsOriginsExpectedValue);
    }

    @Test
    void replicas() {
        final var registry = k8sCellCreate(client, () -> {
            var r = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);

            r.getMetadata().setNamespace(namespace);
            r.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
            r.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

            return r;
        });

        // Verify first replica
        checkDeploymentExists(registry.getCached(), COMPONENT_APP, 1);
        checkDeploymentExists(registry.getCached(), COMPONENT_UI, 1);

        // Scale up
        registry.update(r -> {
            r.getSpec().getApp().setReplicas(3);
            r.getSpec().getUi().setReplicas(3);
        });

        checkDeploymentExists(registry.getCached(), COMPONENT_APP, 3);
        checkDeploymentExists(registry.getCached(), COMPONENT_UI, 3);

        // Scale down
        registry.update(r -> {
            r.getSpec().getApp().setReplicas(2);
            r.getSpec().getUi().setReplicas(2);
        });

        checkDeploymentExists(registry.getCached(), COMPONENT_APP, 2);
        checkDeploymentExists(registry.getCached(), COMPONENT_UI, 2);
    }

    @Test
    void testService() {

        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
        registry.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

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
                .startServicePortForward(registry.getMetadata().getName() + "-app-service", 8080);

        await().ignoreExceptions().until(() -> {
            given().get(new URI("http://localhost:" + appServicePort + "/apis/registry/v3/system/info"))
                    .then().statusCode(200);
            return true;
        });

        await().ignoreExceptions().timeout(MEDIUM_DURATION.multipliedBy(5).plusSeconds(10)).until(() -> {
            int uiServicePort = portForwardManager.startServicePortForward(registry.getMetadata().getName() + "-ui-service", 8080);
            try {
                await().ignoreExceptions().timeout(MEDIUM_DURATION).until(() -> {
                    var pf = portForwardManager.getPortForward(uiServicePort);
                    log.warn("Port-forward status: {}->8080:\n    Is alive: {}\n    Client errors: {}\n    Server errors: {}",
                            pf.getLocalPort(), pf.isAlive(), pf.getClientThrowables(), pf.getServerThrowables());
                    given()
                            .log().all()
                            .when().get(new URI("http://localhost:" + uiServicePort + "/config.js"))
                            .then().statusCode(200);
                    return true;
                });
                return true;
            } catch (ConditionTimeoutException ex) {
                log.warn("Condition timed out.", ex);
                return false;
            }
        });
    }

    @Test
    @DisabledIf("io.apicurio.registry.operator.it.SmokeITTest#ingressDisabled")
    void testIngress() {

        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
        registry.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

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

        final var registry = k8sCellCreate(client, () -> {

            var r = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);
            r.getMetadata().setNamespace(namespace);
            r.withSpec().withApp().withIngress().setHost(ingressManager.getIngressHost("app"));
            r.withSpec().withUi().withIngress().setHost(ingressManager.getIngressHost("ui"));

            return r;
        });

        // Wait for Ingresses
        await().untilAsserted(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getCached().getMetadata().getName() + "-app-ingress").get()).isNotNull();
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getCached().getMetadata().getName() + "-ui-ingress").get()).isNotNull();
        });

        // Check that REGISTRY_API_URL is set
        await().ignoreExceptions().untilAsserted(() -> {
            var uiDeployment = client.apps().deployments().inNamespace(namespace)
                    .withName(registry.getCached().getMetadata().getName() + "-ui-deployment").get();
            verify_REGISTRY_API_URL_isSet(registry.get(), uiDeployment);
        });

        // Disable host and therefore Ingress
        registry.update(r -> {
            r.getSpec().getApp().getIngress().setHost("");
            r.getSpec().getUi().getIngress().setHost("");
        });
        /*
        // TODO: The remote test does not work properly. As a workaround the CR will be deleted and recreated
        // instead of updated:
        // client.resource(registry).update();
        client.resource(registry).delete();
        await().untilAsserted(() -> {
            assertThat(client.resource(registry).get()).isNull();
        });
        client.resource(registry).create();
        */
        await().untilAsserted(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getCached().getMetadata().getName() + "-app-ingress").get()).isNull();
        });
        await().untilAsserted(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getCached().getMetadata().getName() + "-ui-ingress").get()).isNull();
        });

        var uiDeployment = client.apps().deployments().inNamespace(namespace)
                .withName(registry.getCached().getMetadata().getName() + "-ui-deployment").get();
        assertThat(uiDeployment).isNotNull();
        assertThat(uiDeployment.getSpec().getTemplate().getSpec().getContainers())
                .filteredOn(c -> REGISTRY_UI_CONTAINER_NAME.equals(c.getName()))
                .flatMap(Container::getEnv)
                .filteredOn(e -> "REGISTRY_API_URL".equals(e.getName()))
                .isEmpty();

        // Enable again
        registry.update(r -> {
            r.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
            r.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));
        });

        // Verify Ingresses are back
        await().untilAsserted(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getCached().getMetadata().getName() + "-app-ingress").get()).isNotNull();
            assertThat(client.network().v1().ingresses().inNamespace(namespace)
                    .withName(registry.getCached().getMetadata().getName() + "-ui-ingress").get()).isNotNull();
        });

        // Check that REGISTRY_API_URL is set again
        await().ignoreExceptions().untilAsserted(() -> {
            var uiDeployment2 = client.apps().deployments().inNamespace(namespace)
                    .withName(registry.getCached().getMetadata().getName() + "-ui-deployment").get();
            verify_REGISTRY_API_URL_isSet(registry.get(), uiDeployment2);
        });
    }

    private void verify_REGISTRY_API_URL_isSet(ApicurioRegistry3 registry, Deployment deployment) {
        assertThat(deployment).isNotNull();
        assertThat(deployment.getSpec().getTemplate().getSpec().getContainers())
                .filteredOn(c -> REGISTRY_UI_CONTAINER_NAME.equals(c.getName()))
                .flatMap(Container::getEnv)
                .filteredOn(e -> "REGISTRY_API_URL".equals(e.getName()))
                .hasSize(1)
                .map(EnvVar::getValue)
                .first()
                .asInstanceOf(InstanceOfAssertFactories.STRING)
                .startsWith("http://" + registry.getSpec().getApp().getIngress().getHost());
    }

    static boolean ingressDisabled() {
        return ConfigProvider.getConfig().getValue(INGRESS_SKIP_PROP, Boolean.class);
    }
}
