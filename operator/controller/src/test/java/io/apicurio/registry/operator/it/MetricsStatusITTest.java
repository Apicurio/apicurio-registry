package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.MetricsSpec;
import io.apicurio.registry.operator.metrics.MetricsManager;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static io.apicurio.registry.operator.Tags.FEATURE;
import static io.apicurio.registry.operator.Tags.FEATURE_A;
import static io.apicurio.registry.operator.api.v1.status.ConditionConstants.TYPE_METRICS_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Collection reads the application Pods over the cluster network, so the tests that need it only work when
 * the operator itself runs in the cluster. With a local deployment the operator sits outside, where a Pod IP
 * is not routable, and neither would a Service ClusterIP be.
 */
@QuarkusTest
@Tag(FEATURE)
@Tag(FEATURE_A)
public class MetricsStatusITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(MetricsStatusITTest.class);

    @Test
    @DisabledIf("io.apicurio.registry.operator.it.ITBase#isLocalDeployment")
    void testMetricsAreReportedWhenEnabled() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);

        var metrics = new MetricsSpec();
        metrics.setEnabled(true);
        metrics.setScrapeIntervalSeconds(10);
        registry.getSpec().getApp().setMetrics(metrics);

        client.resource(registry).create();

        checkDeploymentExists(registry, ResourceFactory.COMPONENT_APP, 1);

        await().atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() -> {
            var status = client.resource(registry).get().getStatus();
            assertThat(status).isNotNull();
            assertThat(status.getMetrics()).isNotNull();
            // A timestamp means the operator reached the management interface and parsed a response.
            assertThat(status.getMetrics().getLastCollected()).isNotNull();
            assertThat(status.getMetrics().getSummary()).isNotNull();
            // Reaching the endpoint means the failure condition must not be present.
            assertThat(status.getConditions())
                    .noneMatch(condition -> TYPE_METRICS_UNAVAILABLE.equals(condition.getType()));
        });
    }

    /**
     * The reported timestamp tracks the last change in the numbers, not the last collection attempt. If it
     * moved on every pass, each status patch would trigger another reconciliation.
     */
    @Test
    @DisabledIf("io.apicurio.registry.operator.it.ITBase#isLocalDeployment")
    void testLastCollectedDoesNotMoveWhileTheRegistryIsIdle() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);

        var metrics = new MetricsSpec();
        metrics.setEnabled(true);
        metrics.setScrapeIntervalSeconds(10);
        registry.getSpec().getApp().setMetrics(metrics);

        client.resource(registry).create();

        checkDeploymentExists(registry, ResourceFactory.COMPONENT_APP, 1);

        await().atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() -> assertThat(
                client.resource(registry).get().getStatus().getMetrics().getLastCollected()).isNotNull());

        // An idle registry serves no API requests, so every scrape in this window yields the same numbers.
        var first = client.resource(registry).get().getStatus().getMetrics().getLastCollected();
        await().pollDelay(SHORT_DURATION).atMost(MEDIUM_DURATION).ignoreExceptions()
                .untilAsserted(() -> assertThat(client.resource(registry).get()).isNotNull());
        var later = client.resource(registry).get().getStatus().getMetrics().getLastCollected();

        log.info("lastCollected across several scrape intervals: {} then {}", first, later);
        assertThat(later).isEqualTo(first);
    }

    /**
     * The Event is sent to the API server, not to the operand, so unlike collection this works with a local
     * operator too. Without it the shape of the Event would never be checked against a real API server, and a
     * rejected Event only produces a log warning.
     */
    @Test
    void testABreachEventIsAcceptedByTheApiServer() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        var created = client.resource(registry).create();

        var breach = new MetricsManager.Breach(MetricsManager.REASON_HIGH_ERROR_RATE,
                "12.0% of REST API requests were answered with a 5xx status.");
        var event = MetricsManager.buildEvent(created, Instant.now(), breach);

        client.v1().events().inNamespace(namespace).resource(event).create();

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            var stored = client.v1().events().inNamespace(namespace).list().getItems().stream()
                    .filter(e -> MetricsManager.REASON_HIGH_ERROR_RATE.equals(e.getReason()))
                    .findFirst();
            assertThat(stored).isPresent();
            assertThat(stored.get().getType()).isEqualTo("Warning");
            // An Event whose involvedObject does not resolve would not show up under kubectl describe.
            assertThat(stored.get().getInvolvedObject().getName())
                    .isEqualTo(created.getMetadata().getName());
            assertThat(stored.get().getInvolvedObject().getUid()).isEqualTo(created.getMetadata().getUid());
        });
    }

    @Test
    void testMetricsAreAbsentByDefault() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);

        client.resource(registry).create();

        checkDeploymentExists(registry, ResourceFactory.COMPONENT_APP, 1);

        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            var status = client.resource(registry).get().getStatus();
            assertThat(status).isNotNull();
            assertThat(status.getMetrics()).isNull();
        });
    }
}
