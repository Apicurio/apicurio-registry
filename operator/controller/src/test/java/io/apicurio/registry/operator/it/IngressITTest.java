package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.apicurio.registry.operator.resource.ResourceFactory.deserialize;
import static io.apicurio.registry.utils.AutoCell.acell;
import static io.apicurio.registry.utils.AutoCell.acelli;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class IngressITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(IngressITTest.class);

    @Test
    void ingressAnnotations() {

        final var primary = acelli(() -> {

            var p = deserialize("/k8s/examples/ingress/ingress-annotations.apicurioregistry3.yaml", ApicurioRegistry3.class);

            p.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
            p.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

            // Avoid clash in case the annotation is being used on the test cluster:

            var a = p.getSpec().getApp().getIngress().getAnnotations();
            a.put("test---kubernetes.io/ingress.class", a.remove("kubernetes.io/ingress.class"));
            a = p.getSpec().getUi().getIngress().getAnnotations();
            a.put("test---kubernetes.io/ingress.class", a.remove("kubernetes.io/ingress.class"));

            client.resource(p).create();

            return p;

        }, r -> client.resource(r).update());

        final var appIngress = acell(() -> client.network().v1().ingresses().withName(primary.get().getMetadata().getName() + "-app-ingress").get(),
                r -> client.resource(r).update());
        final var uiIngress = acell(() -> client.network().v1().ingresses().withName(primary.get().getMetadata().getName() + "-ui-ingress").get(),
                r -> client.resource(r).update());

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(appIngress.get()).isNotNull();
        });

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(uiIngress.get()).isNotNull();
        });

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(appIngress.get().getMetadata().getAnnotations()).containsAllEntriesOf(Map.of(
                    "test---kubernetes.io/ingress.class", "haproxy",
                    "color", "yellow"
            ));
        });

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(uiIngress.get().getMetadata().getAnnotations()).containsAllEntriesOf(Map.of(
                    "test---kubernetes.io/ingress.class", "haproxy",
                    "color", "pink"
            ));
        });

        appIngress.updateCached(i -> i.getMetadata().getAnnotations().put("animal", "cat"));

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(appIngress.get().getMetadata().getAnnotations()).containsAllEntriesOf(Map.of(
                    "test---kubernetes.io/ingress.class", "haproxy",
                    "color", "yellow",
                    "animal", "cat"
            ));
        });

        primary.updateCached(p -> p.getSpec().getApp().getIngress().getAnnotations().put("color", "blue"));

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(appIngress.get().getMetadata().getAnnotations()).containsAllEntriesOf(Map.of(
                    "test---kubernetes.io/ingress.class", "haproxy",
                    "color", "blue",
                    "animal", "cat"
            ));
        });

        appIngress.updateCached(i -> i.getMetadata().getAnnotations().remove("color"));

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(appIngress.get().getMetadata().getAnnotations()).containsAllEntriesOf(Map.of(
                    "test---kubernetes.io/ingress.class", "haproxy",
                    "color", "blue",
                    "animal", "cat"
            ));
        });

        primary.updateCached(p -> p.getSpec().getApp().getIngress().getAnnotations().remove("color"));

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(appIngress.get().getMetadata().getAnnotations()).containsAllEntriesOf(Map.of(
                    "test---kubernetes.io/ingress.class", "haproxy",
                    "animal", "cat"
            ));
        });

        primary.updateCached(p -> p.getSpec().getApp().getIngress().getAnnotations().put("animal", "dog"));

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(appIngress.get().getMetadata().getAnnotations()).containsAllEntriesOf(Map.of(
                    "test---kubernetes.io/ingress.class", "haproxy",
                    "animal", "dog"
            ));
        });
    }

    @Test
    void ingressClassName() {

        final var primary = acelli(() -> {

            var p = deserialize("/k8s/examples/ingress/ingress-class-name.apicurioregistry3.yaml", ApicurioRegistry3.class);

            p.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
            p.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

            client.resource(p).create();

            return p;

        }, r -> client.resource(r).update());

        final var appIngress = acell(() -> client.network().v1().ingresses().withName(primary.get().getMetadata().getName() + "-app-ingress").get(),
                r -> client.resource(r).update());
        final var uiIngress = acell(() -> client.network().v1().ingresses().withName(primary.get().getMetadata().getName() + "-ui-ingress").get(),
                r -> client.resource(r).update());


        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(appIngress.get()).isNotNull();
            assertThat(appIngress.getCached().getSpec().getIngressClassName()).isEqualTo("haproxy1");
        });

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(uiIngress.get()).isNotNull();
            assertThat(uiIngress.getCached().getSpec().getIngressClassName()).isEqualTo("haproxy2");
        });

        primary.update(p -> p.getSpec().getApp().getIngress().setIngressClassName("nginx"));

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(appIngress.get().getSpec().getIngressClassName()).isEqualTo("nginx");
        });

        primary.update(p -> p.getSpec().getApp().getIngress().setIngressClassName(""));

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(appIngress.get().getSpec().getIngressClassName()).isNull();
        });
    }
}
