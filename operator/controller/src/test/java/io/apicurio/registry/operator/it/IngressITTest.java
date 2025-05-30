package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.apicurio.registry.utils.Cell;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.apicurio.registry.utils.Cell.cell;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class IngressITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(IngressITTest.class);

    @Test
    void ingressAnnotations() {

        var primary = ResourceFactory.deserialize("/k8s/examples/ingress/ingress-annotations.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        primary.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
        primary.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

        client.resource(primary).create();

        Cell<Ingress> appIngress = cell();
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            var i = client.network().v1().ingresses().withName(primary.getMetadata().getName() + "-app-ingress").get();
            assertThat(i).isNotNull();
            appIngress.set(i);
        });

        Cell<Ingress> uiIngress = cell();
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            var i = client.network().v1().ingresses().withName(primary.getMetadata().getName() + "-ui-ingress").get();
            assertThat(i).isNotNull();
            uiIngress.set(i);
        });

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(appIngress.get().getMetadata().getAnnotations()).containsAllEntriesOf(Map.of(
                    "kubernetes.io/ingress.class", "haproxy",
                    "color", "yellow"
            ));
        });

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(uiIngress.get().getMetadata().getAnnotations()).containsAllEntriesOf(Map.of(
                    "kubernetes.io/ingress.class", "haproxy",
                    "color", "pink"
            ));
        });

        // ---

        appIngress.get().getMetadata().getAnnotations().put("animal", "cat");
        appIngress.set(client.resource(appIngress.get()).update());
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            appIngress.set(client.resource(appIngress.get()).get());
            assertThat(appIngress.get().getMetadata().getAnnotations()).containsAllEntriesOf(Map.of(
                    "kubernetes.io/ingress.class", "haproxy",
                    "color", "yellow",
                    "animal", "cat"
            ));
        });

        primary.getSpec().getApp().getIngress().getAnnotations().put("color", "blue");
        updateAndCheck(primary, appIngress, Map.of(
                "kubernetes.io/ingress.class", "haproxy",
                "color", "blue",
                "animal", "cat"
        ));

        appIngress.get().getMetadata().getAnnotations().remove("color");
        appIngress.set(client.resource(appIngress.get()).update());
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            appIngress.set(client.resource(appIngress.get()).get());
            assertThat(appIngress.get().getMetadata().getAnnotations()).containsAllEntriesOf(Map.of(
                    "kubernetes.io/ingress.class", "haproxy",
                    "color", "blue",
                    "animal", "cat"
            ));
        });

        primary.getSpec().getApp().getIngress().getAnnotations().remove("color");
        updateAndCheck(primary, appIngress, Map.of(
                "kubernetes.io/ingress.class", "haproxy",
                "animal", "cat"
        ));

        primary.getSpec().getApp().getIngress().getAnnotations().put("animal", "dog");
        updateAndCheck(primary, appIngress, Map.of(
                "kubernetes.io/ingress.class", "haproxy",
                "animal", "dog"
        ));
    }

    private static void updateAndCheck(ApicurioRegistry3 primary, Cell<Ingress> ingress, Map<String, String> expected) {
        client.resource(primary).update();
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            ingress.set(client.resource(ingress.get()).get());
            assertThat(ingress.get().getMetadata().getAnnotations()).containsAllEntriesOf(expected);
        });
    }

    @Test
    void ingressClassName() {

        var primary = ResourceFactory.deserialize("/k8s/examples/ingress/ingress-class-name.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        primary.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
        primary.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

        client.resource(primary).create();

        Cell<Ingress> appIngress = cell();
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            var i = client.network().v1().ingresses().withName(primary.getMetadata().getName() + "-app-ingress").get();
            assertThat(i).isNotNull();
            appIngress.set(i);
        });

        Cell<Ingress> uiIngress = cell();
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            var i = client.network().v1().ingresses().withName(primary.getMetadata().getName() + "-ui-ingress").get();
            assertThat(i).isNotNull();
            uiIngress.set(i);
        });

        assertThat(appIngress.get().getSpec().getIngressClassName()).isEqualTo("haproxy1");
        assertThat(uiIngress.get().getSpec().getIngressClassName()).isEqualTo("haproxy2");

        primary.getSpec().getApp().getIngress().setIngressClassName("nginx");
        client.resource(primary).update();
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            appIngress.set(client.resource(appIngress.get()).get());
            assertThat(appIngress.get().getSpec().getIngressClassName()).isEqualTo("nginx");
        });

        primary.getSpec().getApp().getIngress().setIngressClassName("");
        client.resource(primary).update();
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            appIngress.set(client.resource(appIngress.get()).get());
            assertThat(appIngress.get().getSpec().getIngressClassName()).isNull();
        });
    }
}
