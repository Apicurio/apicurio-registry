package io.apicurio.registry.operator.metrics;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Status;
import io.apicurio.registry.operator.api.v1.spec.MetricsSpec;
import io.apicurio.registry.operator.api.v1.status.MetricsStatus;
import io.apicurio.registry.operator.api.v1.status.MetricsSummary;
import io.apicurio.registry.operator.metrics.RegistryMetricsSnapshot.RequestCounters;
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
import java.util.LinkedHashMap;
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
     * The reconciler patches the status on every pass. Inside the scrape interval nothing is collected and
     * the previously published status is reused unchanged, so the patch is empty and nothing is written.
     * That is what stops a reconciliation from feeding itself.
     */
    @Test
    public void testStatusIsReusedInsideTheScrapeInterval() {
        collector.enqueue(snapshot(T0, 100, 0));
        collect();
        var afterFirst = currentStatus();
        assertThat(afterFirst.getLastCollected()).isEqualTo(T0);

        advance(Duration.ofSeconds(30));
        collect();

        assertThat(collector.calls).isEqualTo(1);
        assertThat(currentStatus()).isEqualTo(afterFirst);
    }

    /**
     * lastCollected reports when the operator last managed to read the operand, not when the numbers last
     * changed. An idle registry serves no requests and stores nothing new, so it reports the same values
     * every interval, and a timestamp that froze there would be indistinguishable from one that froze
     * because collection had stopped.
     */
    @Test
    public void testLastCollectedAdvancesEvenWhenTheNumbersAreUnchanged() {
        collector.enqueue(snapshot(T0, 100, 0));
        collect();
        assertThat(currentStatus().getLastCollected()).isEqualTo(T0);

        advance(Duration.ofSeconds(60));
        collector.enqueue(snapshot(clock.get(), 160, 0));
        collect();
        var afterSecond = currentStatus();
        assertThat(afterSecond.getLastCollected()).isEqualTo(T0.plusSeconds(60));
        assertThat(afterSecond.getSummary().getRequestRate()).isEqualTo(1.0);

        // Identical traffic over an identical interval, so every reported number is the same.
        advance(Duration.ofSeconds(60));
        collector.enqueue(snapshot(clock.get(), 220, 0));
        collect();

        assertThat(currentStatus().getSummary()).isEqualTo(afterSecond.getSummary());
        assertThat(currentStatus().getLastCollected()).isEqualTo(T0.plusSeconds(120));
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
        collector.enqueue(snapshot(clock.get(), counters("pod-a", 4000, 0), counters("pod-b", 100, 0)));
        collect();
        assertThat(currentStatus().getSummary().getRequestRate()).isEqualTo(2.0);
    }

    /**
     * A scrape that loses one Pod and gains another covers the same number of Pods but not the same Pods, so
     * a count alone cannot tell the two apart. Differencing the sums anyway would report the gap between two
     * populations as traffic, which inflates the request rate and can invent an error rate large enough to
     * raise a Warning Event.
     */
    @Test
    public void testRatesAreDiscardedWhenThePodSetChangesWithoutChangingSize() {
        collector.enqueue(snapshot(T0, counters("pod-a", 1000, 0), counters("pod-b", 1000, 0)));
        collect();

        advance(Duration.ofSeconds(60));
        collector.enqueue(snapshot(clock.get(), counters("pod-a", 1060, 0), counters("pod-b", 1060, 0)));
        collect();
        assertThat(currentStatus().getSummary().getRequestRate()).isEqualTo(2.0);
        assertThat(currentStatus().getSummary().getErrorRate()).isEqualTo(0.0);

        // pod-b dropped out and pod-c answered instead. Still two Pods, but not the same two.
        advance(Duration.ofSeconds(60));
        collector.enqueue(snapshot(clock.get(), counters("pod-a", 1120, 0), counters("pod-c", 9000, 900)));
        collect();

        // The last trustworthy rates are kept rather than the 150/s and 15% the raw sums would imply.
        assertThat(currentStatus().getSummary().getRequestRate()).isEqualTo(2.0);
        assertThat(currentStatus().getSummary().getErrorRate()).isEqualTo(0.0);
    }

    /**
     * One Pod restarting is otherwise masked by another Pod serving traffic, because the two only have to
     * net out positive for the summed delta to look like a plausible rate.
     */
    @Test
    public void testARestartIsDetectedEvenWhenTheSummedDeltaStaysPositive() {
        collector.enqueue(snapshot(T0, counters("pod-a", 1000, 0), counters("pod-b", 1000, 0)));
        collect();

        advance(Duration.ofSeconds(60));
        collector.enqueue(snapshot(clock.get(), counters("pod-a", 1060, 0), counters("pod-b", 1060, 0)));
        collect();
        assertThat(currentStatus().getSummary().getRequestRate()).isEqualTo(2.0);

        // pod-b restarted back to 10, losing 1050, while pod-a served 2000 more. The summed delta is still
        // positive, so checking only the total would accept it and report a rate of nearly 16/s.
        advance(Duration.ofSeconds(60));
        collector.enqueue(snapshot(clock.get(), counters("pod-a", 3060, 0), counters("pod-b", 10, 0)));
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
        return snapshot(timestamp, counters("pod-a", requests, errors));
    }

    private static RegistryMetricsSnapshot snapshot(Instant timestamp, PodCounters... pods) {
        var counters = new LinkedHashMap<String, RequestCounters>();
        for (var pod : pods) {
            counters.put(pod.name(), pod.counters());
        }
        return new RegistryMetricsSnapshot(timestamp, counters.size(), counters, 0.5, 12L, 34L, 100L);
    }

    private static PodCounters counters(String name, double requests, double errors) {
        return new PodCounters(name, new RequestCounters(requests, errors));
    }

    private record PodCounters(String name, RequestCounters counters) {
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
