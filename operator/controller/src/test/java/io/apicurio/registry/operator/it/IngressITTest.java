package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
            assertThat(appIngress.getCached().getSpec().getIngressClassName()).isEqualTo("haproxy-app");
        });

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(uiIngress.get()).isNotNull();
            assertThat(uiIngress.getCached().getSpec().getIngressClassName()).isEqualTo("haproxy-ui");
        });

        primary.update(p -> p.getSpec().getApp().getIngress().setIngressClassName("test---nginx"));

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(appIngress.get().getSpec().getIngressClassName()).isEqualTo("test---nginx");
        });

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(uiIngress.get().getSpec().getIngressClassName()).isEqualTo("haproxy-ui");
        });

        primary.update(p -> p.getSpec().getApp().getIngress().setIngressClassName(""));

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(appIngress.get().getSpec().getIngressClassName()).isNotEqualTo("test---nginx");
        });
    }

    @Test
    void multiHostsPerSecret() {
        final var primary = acelli(() -> {
            var p = deserialize("/k8s/examples/ingress/ingress-tls.apicurioregistry3.yaml", ApicurioRegistry3.class);

            p.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost("app"));
            p.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost("ui"));

            client.resource(p).create();

            return p;
        }, r -> client.resource(r).update());

        final var appIngress = acell(() -> client.network().v1().ingresses()
                .withName(primary.get().getMetadata().getName() + "-app-ingress").get(),
                r -> client.resource(r).update());
        final var uiIngress = acell(() -> client.network().v1().ingresses()
                .withName(primary.get().getMetadata().getName() + "-ui-ingress").get(),
                r -> client.resource(r).update());

        // Verify the ingress is created with correct TLS configuration
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(appIngress.get()).isNotNull();

            var tlsConfig = appIngress.getCached().getSpec().getTls();
            assertThat(tlsConfig).isNotNull();
            assertThat(tlsConfig).hasSize(2);

            // Verify first TLS entry (app-secret with multiple hosts)
            var firstTls = tlsConfig.get(0);
            assertThat(firstTls.getSecretName()).isEqualTo("app-secret");
            assertThat(firstTls.getHosts()).hasSize(2);
            assertThat(firstTls.getHosts()).contains(
                "ingress-class-name-app.apps.cluster.example",
                "another-host.example.com"
            );

            // Verify second TLS entry (wildcard-secret)
            var secondTls = tlsConfig.get(1);
            assertThat(secondTls.getSecretName()).isEqualTo("wildcard-secret");
            assertThat(secondTls.getHosts()).hasSize(1);
            assertThat(secondTls.getHosts()).contains("*.example.com");
        });

        // Get the configured app host for consistency in assertions
        String expectedAppHost = primary.get().getSpec().getApp().getIngress().getHost();

        // Test updating the TLS configuration
        primary.update(p -> {
            Map<String, List<String>> updatedTlsSecrets = Map.of(
                "app-secret", List.of(expectedAppHost),
                "new-secret", List.of("new-host.example.com", "another-new-host.example.com")
            );
            p.getSpec().getApp().getIngress().setTlsSecrets(updatedTlsSecrets);
        });

        // Verify the ingress is updated with new TLS configuration
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            var tlsConfig = appIngress.get().getSpec().getTls();
            assertThat(tlsConfig).hasSize(2);

            // Find the entries by secret name (order might vary)
            var appSecretTls = tlsConfig.stream()
                .filter(tls -> "app-secret".equals(tls.getSecretName()))
                .findFirst().orElse(null);
            var newSecretTls = tlsConfig.stream()
                .filter(tls -> "new-secret".equals(tls.getSecretName()))
                .findFirst().orElse(null);

            assertThat(appSecretTls).isNotNull();
            assertThat(appSecretTls.getHosts()).hasSize(1);
            assertThat(appSecretTls.getHosts()).contains(expectedAppHost);

            assertThat(newSecretTls).isNotNull();
            assertThat(newSecretTls.getHosts()).hasSize(2);
            assertThat(newSecretTls.getHosts()).contains("new-host.example.com", "another-new-host.example.com");
        });

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(uiIngress.get()).isNotNull();

            var tlsConfig = uiIngress.getCached().getSpec().getTls();
            assertThat(tlsConfig).isNotNull();
            assertThat(tlsConfig).hasSize(2);

            // Verify first TLS entry (app-secret with multiple hosts)
            var firstTls = tlsConfig.get(0);
            assertThat(firstTls.getSecretName()).isEqualTo("ui-secret");
            assertThat(firstTls.getHosts()).hasSize(1);
            assertThat(firstTls.getHosts()).contains(
                "ingress-class-name-ui.apps.cluster.example"
            );

            // Verify second TLS entry (wildcard-secret)
            var secondTls = tlsConfig.get(1);
            assertThat(secondTls.getSecretName()).isEqualTo("wildcard-secret");
            assertThat(secondTls.getHosts()).hasSize(2);
            assertThat(secondTls.getHosts()).contains(
                "*.example.com",
                "another-host.example.com"
            );
        });

        String expectedUiHost = primary.get().getSpec().getUi().getIngress().getHost();

        // Test updating the TLS configuration
        primary.update(p -> {
            Map<String, List<String>> updatedTlsSecrets = Map.of(
                    "new-ui-secret", List.of(expectedUiHost),
                    "new-secret", List.of("new-host.example.com", "another-new-host.example.com")
            );
            p.getSpec().getUi().getIngress().setTlsSecrets(updatedTlsSecrets);
        });

        // Verify the ingress is updated with new TLS configuration
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            var tlsConfig = uiIngress.get().getSpec().getTls();
            assertThat(tlsConfig).hasSize(2);

            // Find the entries by secret name (order might vary)
            var uiSecretTls = tlsConfig.stream()
                .filter(tls -> "new-ui-secret".equals(tls.getSecretName()))
                .findFirst().orElse(null);
            var newSecretTls = tlsConfig.stream()
                .filter(tls -> "new-secret".equals(tls.getSecretName()))
                .findFirst().orElse(null);

            assertThat(uiSecretTls).isNotNull();
            assertThat(uiSecretTls.getHosts()).hasSize(1);
            assertThat(uiSecretTls.getHosts()).contains(expectedUiHost);

            assertThat(newSecretTls).isNotNull();
            assertThat(newSecretTls.getHosts()).hasSize(2);
            assertThat(newSecretTls.getHosts()).contains(
                "new-host.example.com",
                "another-new-host.example.com"
            );
        });
    }
}
