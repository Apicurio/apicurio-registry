package io.apicurio.registry.operator.metrics;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Status;
import io.apicurio.registry.operator.api.v1.spec.MetricsSpec;
import io.apicurio.registry.operator.api.v1.status.MetricsStatus;
import io.apicurio.registry.operator.api.v1.status.MetricsSummary;
import io.apicurio.registry.operator.status.MetricsUnavailableConditionManager;
import io.apicurio.registry.operator.status.StatusManager;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class MetricsManagerTest {

    private static final Instant T0 = Instant.parse("2026-08-27T10:00:00Z");

    private final AtomicReference<Instant> clock = new AtomicReference<>(T0);

    private StubCollector collector;

    private MetricsManager manager;

    private ApicurioRegistry3 primary;

    private MetricsUnavailableConditionManager conditionManager;

    @BeforeEach
    public void setUp() {
        clock.set(T0);
        collector = new StubCollector();
        manager = new MetricsManager(collector, clock::get);
        primary = registry("metrics-manager-test-" + System.nanoTime());
        conditionManager = StatusManager.get(primary)
                .getConditionManager(MetricsUnavailableConditionManager.class);
    }

    /**
     * Reconciliation is rescheduled one interval ahead, so a wake-up that lands slightly early must still be
     * allowed to collect. Otherwise the next chance is a further full interval away and the sampling rate
     * quietly halves.
     */
    @Test
    public void testCollectionRespectsTheScrapeInterval() {
        collector.enqueue(snapshot(T0, 100, 0));
        collect();

        advance(Duration.ofSeconds(30));
        collect();
        assertThat(collector.calls).isEqualTo(1);

        advance(Duration.ofSeconds(27));
        collector.enqueue(snapshot(clock.get(), 200, 0));
        collect();
        assertThat(collector.calls).isEqualTo(2);
    }

    /**
     * The reconciler patches the status on every pass. If the reported metrics changed on every pass, that
     * patch would rewrite the resource, produce a watch event, and reconcile again without end.
     */
    @Test
    public void testStatusDoesNotChangeWhenTheNumbersDoNot() {
        collector.enqueue(snapshot(T0, 100, 0));
        collect();
        var afterFirst = currentStatus();
        assertThat(afterFirst.getLastCollected()).isEqualTo(T0);

        // Inside the interval nothing is collected and the same status is reported.
        advance(Duration.ofSeconds(30));
        collect();
        assertThat(currentStatus()).isEqualTo(afterFirst);

        // A second collection produces rates for the first time, so the summary legitimately changes.
        advance(Duration.ofSeconds(30));
        collector.enqueue(snapshot(clock.get(), 160, 0));
        collect();
        var afterSecond = currentStatus();
        assertThat(afterSecond.getLastCollected()).isEqualTo(T0.plusSeconds(60));
        assertThat(afterSecond.getSummary().getRequestRate()).isEqualTo(1.0);

        // A third collection yields the same numbers, so the timestamp must not move.
        advance(Duration.ofSeconds(60));
        collector.enqueue(snapshot(clock.get(), 220, 0));
        collect();
        assertThat(currentStatus()).isEqualTo(afterSecond);
    }

    @Test
    public void testRatesAreDerivedFromConsecutiveCollections() {
        collector.enqueue(snapshot(T0, 1000, 10));
        collect();
        assertThat(currentStatus().getSummary().getRequestRate()).isNull();

        advance(Duration.ofSeconds(60));
        collector.enqueue(snapshot(clock.get(), 1120, 16));
        collect();

        var summary = currentStatus().getSummary();
        assertThat(summary.getRequestRate()).isEqualTo(2.0);
        assertThat(summary.getErrorRate()).isEqualTo(0.05);
    }

    /**
     * A restarted Pod resets its counters and a rescaled Deployment changes what the sum covers. Neither
     * delta is a rate, so the last known values are kept instead.
     */
    @Test
    public void testUntrustworthyDeltasAreDiscarded() {
        collector.enqueue(snapshot(T0, 1000, 0));
        collect();
        advance(Duration.ofSeconds(60));
        collector.enqueue(snapshot(clock.get(), 1120, 0));
        collect();
        assertThat(currentStatus().getSummary().getRequestRate()).isEqualTo(2.0);

        advance(Duration.ofSeconds(60));
        collector.enqueue(snapshot(clock.get(), 5, 0));
        collect();
        assertThat(currentStatus().getSummary().getRequestRate()).isEqualTo(2.0);

        advance(Duration.ofSeconds(60));
        collector.enqueue(new RegistryMetricsSnapshot(clock.get(), 2, 4000, 0, true, 0.5, 12L, 34L, 100L));
        collect();
        assertThat(currentStatus().getSummary().getRequestRate()).isEqualTo(2.0);
    }

    @Test
    public void testThresholdsAreCrossedAtTheirConfiguredValue() {
        var summary = new MetricsSummary();
        summary.setConnectionPoolUtilization(0.79);
        summary.setErrorRate(0.09);
        summary.setKafkaConsumerLag(999L);
        // Just below each default, so nothing is reported.
        assertThat(MetricsManager.breaches(primary, summary)).isEmpty();

        // Exactly at the default is a breach, not just above it.
        summary.setConnectionPoolUtilization(0.8);
        summary.setErrorRate(0.1);
        summary.setKafkaConsumerLag(1000L);
        assertThat(MetricsManager.breaches(primary, summary)).map(MetricsManager.Breach::reason)
                .containsExactlyInAnyOrder(MetricsManager.REASON_CONNECTION_POOL_SATURATED,
                        MetricsManager.REASON_HIGH_ERROR_RATE, MetricsManager.REASON_KAFKA_CONSUMER_LAG);

        // A configured threshold replaces the default.
        primary.getSpec().getApp().getMetrics().setConnectionPoolUtilizationThreshold(0.95);
        assertThat(MetricsManager.breaches(primary, summary)).map(MetricsManager.Breach::reason)
                .doesNotContain(MetricsManager.REASON_CONNECTION_POOL_SATURATED);

        // An absent value cannot breach anything.
        assertThat(MetricsManager.breaches(primary, new MetricsSummary())).isEmpty();
    }

    /**
     * A breach that persists would otherwise produce one Event per scrape interval and bury everything else
     * in kubectl describe.
     */
    @Test
    public void testAnOngoingBreachIsNotReportedAgainWithinTheWindow() {
        var reason = MetricsManager.REASON_HIGH_ERROR_RATE;

        assertThat(manager.shouldReport(reason, T0)).isTrue();
        manager.recordReported(reason, T0);

        assertThat(manager.shouldReport(reason, T0.plusSeconds(60))).isFalse();
        assertThat(manager.shouldReport(reason, T0.plus(MetricsManager.EVENT_REPEAT_INTERVAL))).isTrue();
        // A different threshold is tracked separately.
        assertThat(manager.shouldReport(MetricsManager.REASON_KAFKA_CONSUMER_LAG, T0)).isTrue();
    }

    @Test
    public void testFailedCollectionKeepsTheLastKnownSummary() {
        collector.enqueue(snapshot(T0, 100, 0));
        collect();
        var afterFirst = currentStatus();

        advance(Duration.ofSeconds(60));
        collector.enqueueFailure("connection refused");
        collect();

        assertThat(currentStatus()).isEqualTo(afterFirst);
    }

    private void collect() {
        manager.collect(primary, null, conditionManager);
    }

    private void advance(Duration duration) {
        clock.set(clock.get().plus(duration));
    }

    private MetricsStatus currentStatus() {
        var status = new ApicurioRegistry3Status();
        manager.applyStatus(status);
        return status.getMetrics();
    }

    private static RegistryMetricsSnapshot snapshot(Instant timestamp, double requests, double errors) {
        return new RegistryMetricsSnapshot(timestamp, 1, requests, errors, true, 0.5, 12L, 34L, 100L);
    }

    private static ApicurioRegistry3 registry(String name) {
        var registry = new ApicurioRegistry3();
        registry.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace("test").build());
        var metrics = new MetricsSpec();
        metrics.setEnabled(true);
        metrics.setScrapeIntervalSeconds(60);
        registry.withSpec().withApp().setMetrics(metrics);
        return registry;
    }

    private static class StubCollector extends MetricsCollector {

        private final Deque<Object> results = new ArrayDeque<>();

        private int calls;

        void enqueue(RegistryMetricsSnapshot snapshot) {
            results.add(snapshot);
        }

        void enqueueFailure(String message) {
            results.add(new MetricsCollectionException(message));
        }

        @Override
        public RegistryMetricsSnapshot collect(KubernetesClient client, ApicurioRegistry3 primary)
                throws MetricsCollectionException {
            calls++;
            var next = results.poll();
            if (next instanceof MetricsCollectionException ex) {
                throw ex;
            }
            return (RegistryMetricsSnapshot) next;
        }
    }
}
