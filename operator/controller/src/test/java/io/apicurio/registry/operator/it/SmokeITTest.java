package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class SmokeITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(SmokeITTest.class);

    @Test
    void demoDeployment() {
        // spotless:off
        var registry = new ApicurioRegistry3();
        var meta = new ObjectMeta();
        meta.setName("demo");
        meta.setNamespace(getNamespace());
        registry.setMetadata(meta);
        registry.setSpec(ApicurioRegistry3Spec.builder()
                .appHost(ingressManager.getIngressHost("demo-app"))
                .uiHost(ingressManager.getIngressHost("demo-ui"))
                .build());
        // spotless:on

        // Act
        client.resources(ApicurioRegistry3.class).inNamespace(getNamespace()).create(registry);

        // Deployments
        await().ignoreExceptions().until(() -> {
            assertThat(client.apps().deployments().inNamespace(getNamespace()).withName("demo-app-deployment")
                    .get().getStatus().getReadyReplicas()).isEqualTo(1);
            assertThat(client.apps().deployments().inNamespace(getNamespace()).withName("demo-ui-deployment")
                    .get().getStatus().getReadyReplicas()).isEqualTo(1);
            return true;
        });

        // Services
        await().ignoreExceptions().until(() -> {
            assertThat(client.services().inNamespace(getNamespace()).withName("demo-app-service").get()
                    .getSpec().getClusterIP()).isNotBlank();
            assertThat(client.services().inNamespace(getNamespace()).withName("demo-ui-service").get()
                    .getSpec().getClusterIP()).isNotBlank();
            return true;
        });

        // Ingresses
        await().ignoreExceptions().until(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(getNamespace())
                    .withName("demo-app-ingress").get().getSpec().getRules().get(0).getHost())
                    .isEqualTo(registry.getSpec().getAppHost());
            assertThat(client.network().v1().ingresses().inNamespace(getNamespace())
                    .withName("demo-ui-ingress").get().getSpec().getRules().get(0).getHost())
                    .isEqualTo(registry.getSpec().getUiHost());
            return true;
        });
    }

    @Test
    void testService() {
        // spotless:off
        var registry = new ApicurioRegistry3();
        var meta = new ObjectMeta();
        meta.setName("demo");
        meta.setNamespace(namespace);
        registry.setMetadata(meta);
        registry.setSpec(ApicurioRegistry3Spec.builder()
                .appHost(ingressManager.getIngressHost("demo-app"))
                .uiHost(ingressManager.getIngressHost("demo-ui"))
                .build());
        // spotless:on

        // Act
        client.resources(ApicurioRegistry3.class).inNamespace(namespace).create(registry);

        // Wait for Services
        await().ignoreExceptions().until(() -> {
            assertThat(client.services().inNamespace(namespace).withName("demo-app-service").get().getSpec()
                    .getClusterIP()).isNotBlank();
            assertThat(client.services().inNamespace(namespace).withName("demo-ui-service").get().getSpec()
                    .getClusterIP()).isNotBlank();
            return true;
        });

        int appServicePort = portForwardManager.startPortForward("demo-app-service", 8080);

        await().ignoreExceptions().until(() -> {
            given().get(new URI("http://localhost:" + appServicePort + "/apis/registry/v3/system/info"))
                    .then().statusCode(200);
            return true;
        });

        int uiServicePort = portForwardManager.startPortForward("demo-ui-service", 8080);

        await().ignoreExceptions().until(() -> {
            given().get(new URI("http://localhost:" + uiServicePort + "/config.js")).then().statusCode(200);
            return true;
        });
    }

    @Test
    @DisabledIfSystemProperty(named = INGRESS_SKIP_PROP, matches = "true")
    void testIngress() {
        // spotless:off
        var registry = new ApicurioRegistry3();
        var meta = new ObjectMeta();
        meta.setName("demo");
        meta.setNamespace(namespace);
        registry.setMetadata(meta);
        registry.setSpec(ApicurioRegistry3Spec.builder()
                .appHost(ingressManager.getIngressHost("demo-app"))
                .uiHost(ingressManager.getIngressHost("demo-ui"))
                .build());
        // spotless:on

        // Act
        client.resources(ApicurioRegistry3.class).inNamespace(namespace).create(registry);

        // Wait for Ingresses
        await().ignoreExceptions().until(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespace).withName("demo-app-ingress")
                    .get()).isNotNull();
            assertThat(client.network().v1().ingresses().inNamespace(namespace).withName("demo-ui-ingress")
                    .get()).isNotNull();
            return true;
        });

        await().ignoreExceptions().until(() -> {
            ingressManager.startHttpRequest("demo-app-ingress").basePath("/apis/registry/v3/system/info")
                    .get().then().statusCode(200);
            return true;
        });

        await().ignoreExceptions().until(() -> {
            ingressManager.startHttpRequest("demo-ui-ingress").basePath("/config.js").get().then()
                    .statusCode(200);
            return true;
        });
    }
}
