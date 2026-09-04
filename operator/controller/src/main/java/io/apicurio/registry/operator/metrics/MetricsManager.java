package io.apicurio.registry.operator.metrics;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Status;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.MetricsSpec;
import io.apicurio.registry.operator.api.v1.status.MetricsStatus;
import io.apicurio.registry.operator.api.v1.status.MetricsSummary;
import io.apicurio.registry.operator.status.MetricsUnavailableConditionManager;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

/**
 * Collects operand metrics for a single Apicurio Registry instance and turns them into status fields and
 * Kubernetes Events.
 * <p>
 * Instances are kept per custom resource, mirroring
 * {@link io.apicurio.registry.operator.status.StatusManager}, because rates can only be derived by comparing
 * two collections and that requires state between reconciliations.
 * <p>
 * <strong>On reconciliation loops.</strong> The reconciler patches the status on every pass, and the operator
 * does not use server-side apply for the primary resource, so a status that changed on every pass would
 * rewrite the resource and trigger another reconciliation. What stops that here is the scrape interval:
 * collection happens at most once per interval, and in between the previously published status object is
 * reused verbatim. The non-SSA status patch is an RFC 6902 diff, so an unchanged status produces an empty
 * patch and no write at all.
 * <p>
 * A successful collection does move {@code lastCollected}, which writes and therefore fires a watch event.
 * The reconciliation that event triggers falls inside the interval, reuses the published status, and writes
 * nothing, so it settles after one extra pass instead of looping. That costs one additional reconciliation
 * per interval, which is the price of {@code lastCollected} meaning what its name says. Reporting the last
 * change instead would leave the field frozen on an idle registry, where every value is legitimately
 * stable, and an operator could not then tell a healthy quiet deployment from one the operator stopped
 * collecting from an hour ago.
 */
public class MetricsManager {

    private static final Logger log = LoggerFactory.getLogger(MetricsManager.class);

    private static final Map<ResourceID, MetricsManager> instances = new ConcurrentHashMap<>();

    static final Duration DEFAULT_SCRAPE_INTERVAL = Duration.ofSeconds(60);

    static final Duration MIN_SCRAPE_INTERVAL = Duration.ofSeconds(10);

    public static final double DEFAULT_POOL_UTILIZATION_THRESHOLD = 0.8;

    public static final double DEFAULT_ERROR_RATE_THRESHOLD = 0.1;

    public static final long DEFAULT_KAFKA_CONSUMER_LAG_THRESHOLD = 1000L;

    /**
     * How long to wait before repeating an Event for a breach that is still ongoing.
     */
    static final Duration EVENT_REPEAT_INTERVAL = Duration.ofMinutes(10);

    public static final String REASON_CONNECTION_POOL_SATURATED = "ConnectionPoolSaturated";

    public static final String REASON_HIGH_ERROR_RATE = "HighErrorRate";

    public static final String REASON_KAFKA_CONSUMER_LAG = "KafkaConsumerLagHigh";

    private static final DateTimeFormatter EVENT_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    public static MetricsManager get(ApicurioRegistry3 primary) {
        // We're assuming no concurrent reconciliations per primary resource instance.
        return instances.computeIfAbsent(ResourceID.fromResource(primary), ignored -> new MetricsManager());
    }

    public static void clean(ApicurioRegistry3 primary) {
        instances.remove(ResourceID.fromResource(primary));
    }

    private final MetricsCollector collector;

    private final Map<String, Instant> lastEventAt = new HashMap<>();

    private RegistryMetricsSnapshot previousSnapshot;

    private MetricsStatus published;

    private Instant lastAttempt;

    private String currentFailure;

    private final Supplier<Instant> clock;

    MetricsManager() {
        this(new MetricsCollector(), Instant::now);
    }

    MetricsManager(MetricsCollector collector, Supplier<Instant> clock) {
        this.collector = collector;
        this.clock = clock;
    }

    /**
     * Collect metrics if the scrape interval has elapsed, then evaluate thresholds.
     * <p>
     * Never throws. A registry that cannot be reached is reported through the condition manager and leaves
     * the previously reported summary in place.
     */
    public synchronized void collect(ApicurioRegistry3 primary, KubernetesClient client,
                                     MetricsUnavailableConditionManager conditionManager) {
        var now = clock.get();
        if (lastAttempt != null && Duration.between(lastAttempt, now).compareTo(collectionDue(primary)) < 0) {
            // Inside the interval. Reusing the published status keeps the resulting patch a no-op. The
            // condition still has to be re-asserted, because the condition manager is reset after every pass.
            reportFailure(conditionManager);
            return;
        }
        lastAttempt = now;

        RegistryMetricsSnapshot snapshot;
        try {
            snapshot = collector.collect(client, primary);
        } catch (MetricsCollectionException ex) {
            log.debug("Could not collect metrics for {}", ResourceID.fromResource(primary), ex);
            currentFailure = ex.getMessage();
            reportFailure(conditionManager);
            return;
        }
        currentFailure = null;

        var summary = summarize(snapshot);
        publish(summary, now);
        previousSnapshot = snapshot;
        evaluateThresholds(primary, client, summary, now);
    }

    /**
     * Copy the most recently published metrics onto the status being built.
     */
    public synchronized void applyStatus(ApicurioRegistry3Status status) {
        status.setMetrics(published);
    }

    private void reportFailure(MetricsUnavailableConditionManager conditionManager) {
        if (currentFailure != null) {
            conditionManager.recordFailure(currentFailure);
        }
    }

    private MetricsSummary summarize(RegistryMetricsSnapshot snapshot) {
        var summary = new MetricsSummary();
        summary.setConnectionPoolUtilization(round(snapshot.poolUtilization(), 3));
        summary.setArtifactCount(snapshot.artifactCount());
        summary.setArtifactVersionCount(snapshot.artifactVersionCount());
        summary.setKafkaConsumerLag(snapshot.kafkaConsumerLag());
        summary.setScrapedPods(snapshot.scrapedPods());

        var rates = deriveRates(snapshot);
        if (rates != null) {
            summary.setRequestRate(round(rates.requestRate(), 2));
            summary.setErrorRate(round(rates.errorRate(), 4));
        } else if (published != null && published.getSummary() != null) {
            // First collection after startup, or an interval we had to discard. Keep the last known rates
            // rather than reporting nothing at all.
            summary.setRequestRate(published.getSummary().getRequestRate());
            summary.setErrorRate(published.getSummary().getErrorRate());
        }
        return summary;
    }

    /**
     * Turn two consecutive counter readings into a rate, or return null when the pair cannot be trusted.
     * <p>
     * Counters are only comparable when both readings cover exactly the same Pods, which is why the set is
     * compared and not just its size. A scrape that lost one Pod and gained another covers the same number
     * of Pods, and the difference between the two sums would then be reported as traffic that never
     * happened. The deltas are also checked per Pod, because one Pod resetting its counters can otherwise be
     * masked by another Pod's traffic once the two are summed.
     */
    private Rates deriveRates(RegistryMetricsSnapshot snapshot) {
        if (previousSnapshot == null || !snapshot.requestMetricSeen()) {
            return null;
        }
        var current = snapshot.requestCounters();
        var previous = previousSnapshot.requestCounters();
        if (!current.keySet().equals(previous.keySet())) {
            return null;
        }
        var seconds = Duration.between(previousSnapshot.timestamp(), snapshot.timestamp()).toMillis() / 1000.0;
        if (seconds <= 0) {
            return null;
        }
        var requests = 0.0;
        var errors = 0.0;
        for (var pod : current.entrySet()) {
            var before = previous.get(pod.getKey());
            var requestDelta = pod.getValue().requests() - before.requests();
            var errorDelta = pod.getValue().serverErrors() - before.serverErrors();
            if (requestDelta < 0 || errorDelta < 0) {
                // A negative delta means this Pod restarted and reset its counters.
                return null;
            }
            requests += requestDelta;
            errors += errorDelta;
        }
        return new Rates(requests / seconds, requests > 0 ? errors / requests : 0.0);
    }

    /**
     * Store the summary and the time it was collected.
     */
    private void publish(MetricsSummary summary, Instant now) {
        var status = new MetricsStatus();
        status.setSummary(summary);
        status.setLastCollected(now);
        published = status;
    }

    private void evaluateThresholds(ApicurioRegistry3 primary, KubernetesClient client, MetricsSummary summary,
                                    Instant now) {
        for (var breach : breaches(primary, summary)) {
            if (!shouldReport(breach.reason(), now)) {
                continue;
            }
            if (emit(primary, client, now, breach)) {
                recordReported(breach.reason(), now);
            }
        }
    }

    /**
     * Which configured thresholds the given summary crosses. Kept free of any API calls so that the rule
     * itself can be exercised without a cluster.
     */
    static List<Breach> breaches(ApicurioRegistry3 primary, MetricsSummary summary) {
        var spec = metricsSpec(primary);
        var found = new ArrayList<Breach>();

        double poolThreshold = spec.map(MetricsSpec::getConnectionPoolUtilizationThreshold)
                .orElse(DEFAULT_POOL_UTILIZATION_THRESHOLD);
        if (summary.getConnectionPoolUtilization() != null
                && summary.getConnectionPoolUtilization() >= poolThreshold) {
            found.add(new Breach(REASON_CONNECTION_POOL_SATURATED,
                    "Database connection pool utilization on at least one application Pod is %.1f%%, at or above the configured threshold of %.1f%%."
                            .formatted(summary.getConnectionPoolUtilization() * 100, poolThreshold * 100)));
        }

        double errorThreshold = spec.map(MetricsSpec::getErrorRateThreshold).orElse(DEFAULT_ERROR_RATE_THRESHOLD);
        if (summary.getErrorRate() != null && summary.getErrorRate() >= errorThreshold) {
            found.add(new Breach(REASON_HIGH_ERROR_RATE,
                    "%.1f%% of REST API requests were answered with a 5xx status, at or above the configured threshold of %.1f%%."
                            .formatted(summary.getErrorRate() * 100, errorThreshold * 100)));
        }

        long lagThreshold = spec.map(MetricsSpec::getKafkaConsumerLagThreshold)
                .orElse(DEFAULT_KAFKA_CONSUMER_LAG_THRESHOLD);
        if (summary.getKafkaConsumerLag() != null && summary.getKafkaConsumerLag() >= lagThreshold) {
            found.add(new Breach(REASON_KAFKA_CONSUMER_LAG,
                    "KafkaSQL consumer lag is %d records, at or above the configured threshold of %d."
                            .formatted(summary.getKafkaConsumerLag(), lagThreshold)));
        }
        return found;
    }

    /**
     * Whether a breach that is still ongoing may be reported again yet. Without this, a saturated pool would
     * produce one Event per scrape interval.
     */
    boolean shouldReport(String reason, Instant now) {
        var previous = lastEventAt.get(reason);
        return previous == null || Duration.between(previous, now).compareTo(EVENT_REPEAT_INTERVAL) >= 0;
    }

    void recordReported(String reason, Instant now) {
        lastEventAt.put(reason, now);
    }

    /**
     * @return true when the Event was accepted by the API server
     */
    private boolean emit(ApicurioRegistry3 primary, KubernetesClient client, Instant now, Breach breach) {
        var reason = breach.reason();
        var namespace = primary.getMetadata().getNamespace();
        try {
            client.v1().events().inNamespace(namespace).resource(buildEvent(primary, now, breach)).create();
            return true;
        } catch (Exception ex) {
            // Reporting is best effort. Failing to record an Event must not fail reconciliation, and not
            // marking it as reported means the next collection will try again.
            log.warn("Could not emit {} Event for {}: {}", reason, ResourceID.fromResource(primary),
                    ex.getMessage());
            return false;
        }
    }

    /**
     * Builds the Event reporting a breach. Separate from sending it so that the shape can be checked against
     * a real API server without having to reach the operand.
     */
    public static Event buildEvent(ApicurioRegistry3 primary, Instant now, Breach breach) {
        var namespace = primary.getMetadata().getNamespace();
        var timestamp = EVENT_TIMESTAMP.format(now);
        return new EventBuilder()
                .withNewMetadata()
                .withNamespace(namespace)
                .withGenerateName(primary.getMetadata().getName() + "-metrics-")
                .endMetadata()
                .withType("Warning")
                .withReason(breach.reason())
                .withMessage(breach.message())
                .withFirstTimestamp(timestamp)
                .withLastTimestamp(timestamp)
                .withCount(1)
                .withNewInvolvedObject()
                .withApiVersion(primary.getApiVersion())
                .withKind(primary.getKind())
                .withName(primary.getMetadata().getName())
                .withNamespace(namespace)
                .withUid(primary.getMetadata().getUid())
                .endInvolvedObject()
                .withNewSource()
                .withComponent("apicurio-registry-operator")
                .endSource()
                .build();
    }

    /**
     * A threshold that the reported summary crosses, and the wording used to report it.
     */
    public record Breach(String reason, String message) {
    }

    public static boolean isEnabled(ApicurioRegistry3 primary) {
        return metricsSpec(primary).map(MetricsSpec::getEnabled).orElse(false);
    }

    public static Duration scrapeInterval(ApicurioRegistry3 primary) {
        var configured = metricsSpec(primary)
                .map(MetricsSpec::getScrapeIntervalSeconds)
                .filter(seconds -> seconds > 0)
                .map(Duration::ofSeconds)
                .orElse(DEFAULT_SCRAPE_INTERVAL);
        // A very short interval would scrape the operand more often than it is useful, and would keep the
        // reconciler busy for no benefit.
        return configured.compareTo(MIN_SCRAPE_INTERVAL) < 0 ? MIN_SCRAPE_INTERVAL : configured;
    }

    /**
     * How much time must pass before another collection is allowed.
     * <p>
     * This is slightly shorter than the scrape interval on purpose. Reconciliation is rescheduled one
     * interval ahead, so a wake-up that lands a few milliseconds early would otherwise be turned away and
     * the next chance would not come until a further full interval had passed, quietly halving the sampling
     * rate.
     */
    private static Duration collectionDue(ApicurioRegistry3 primary) {
        var interval = scrapeInterval(primary);
        return interval.minus(interval.dividedBy(10));
    }

    private static Optional<MetricsSpec> metricsSpec(ApicurioRegistry3 primary) {
        return ofNullable(primary)
                .map(ApicurioRegistry3::getSpec)
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getMetrics);
    }

    private static Double round(Double value, int decimals) {
        if (value == null) {
            return null;
        }
        var factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private record Rates(double requestRate, double errorRate) {
    }
}
