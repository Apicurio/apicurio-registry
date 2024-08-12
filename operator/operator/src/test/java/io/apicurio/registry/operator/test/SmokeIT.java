package io.apicurio.registry.operator.test;

import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3Builder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;

import static io.apicurio.registry.operator.test.OperatorTestExtension.LONG_WAIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class SmokeIT {

    private static final Logger log = LoggerFactory.getLogger(SmokeIT.class);

    private static final OperatorTestExtension ext = OperatorTestExtension.builder().build();

    @BeforeAll
    static void beforeAll() {
        ext.start();
    }

    @Test
    void testSmoke() {

        var name = "smoke";

        // @formatter:off
        // spotless:off
        var as1 = new ApicurioRegistry3Builder()
                .withNewMetadata()
                    .withName(name)
                .endMetadata()
                .withNewSpec()
                .endSpec()
                .build();
        // spotless:on
        // @formatter:on

        ext.create(as1);

        ext.isPresent(Deployment.class, name + "-app-deployment");
        ext.isPresent(Deployment.class, name + "-ui-deployment");
        ext.isPresent(Service.class, name + "-app-service");
        ext.isPresent(Service.class, name + "-ui-service");
        ext.isPresent(Ingress.class, name + "-app-ingress");
        ext.isPresent(Ingress.class, name + "-ui-ingress");

        ext.expect(ApicurioRegistry3.class, as1.getMetadata().getName(), r -> {
            assertThat(r.getSpec().getApp().getHost()).startsWith(name + "-app." + ext.getNamespace() + ".");
            assertThat(r.getSpec().getUi().getHost()).startsWith(name + "-ui." + ext.getNamespace() + ".");
        });

        int appPort = ext.portForward(name + "-app-service", 8080);
        int uiPort = ext.portForward(name + "-ui-service", 8080);

        var httpClient = HttpClient.newBuilder().build();

        await().atMost(LONG_WAIT).untilAsserted(() -> {
            var req = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:" + appPort + "/apis/registry/v3/system/info")).GET()
                    .build();
            try {
                var res = httpClient.send(req, BodyHandlers.discarding());
                assertThat(res.statusCode()).isEqualTo(200);
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
        });

        await().atMost(LONG_WAIT).untilAsserted(() -> {
            var req = HttpRequest.newBuilder().uri(new URI("http://localhost:" + uiPort)).GET().build();
            try {
                var res = httpClient.send(req, BodyHandlers.discarding());
                assertThat(res.statusCode()).isEqualTo(200);
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
        });

        ext.delete(as1);

        ext.isNotPresent(Deployment.class, name + "-app-deployment");
        ext.isNotPresent(Deployment.class, name + "-ui-deployment");
        ext.isNotPresent(Service.class, name + "-app-service");
        ext.isNotPresent(Service.class, name + "-ui-service");
        ext.isNotPresent(Ingress.class, name + "-app-ingress");
        ext.isNotPresent(Ingress.class, name + "-ui-ingress");
    }

    @AfterAll
    static void afterAll() {
        ext.stop();
    }
}
