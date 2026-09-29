package io.apicurio.registry.operator.metrics;

import java.time.Instant;
import java.util.Map;

/**
 * Raw metric values collected from the Apicurio Registry application Pods at a single point in time.
 * <p>
 * Request counters are kept per Pod rather than pre-summed, because a rate can only be derived from two
 * readings that cover the same set of Pods. Summing first loses the information needed to tell that apart: a
 * scrape that lost one Pod and gained another covers the same number of Pods, and the difference between the
 * two sums would then be reported as traffic. Gauges are already aggregated, because averaging or maximizing
 * them later would not be meaningful.
 *
 * @param timestamp            when the scrape completed
 * @param scrapedPods          how many Pods answered
 * @param requestCounters      cumulative REST API request counters, keyed by Pod name. A Pod that did not
 *                             expose the counter is absent.
 * @param poolUtilization      highest fraction of in-use database connections on any Pod, null when not
 *                             exposed
 * @param artifactCount        number of artifacts in storage, null when not exposed
 * @param artifactVersionCount number of artifact versions in storage, null when not exposed
 * @param kafkaConsumerLag     highest consumer lag reported by any Pod, null when not exposed
 */
public record RegistryMetricsSnapshot(
        Instant timestamp,
        int scrapedPods,
        Map<String, RequestCounters> requestCounters,
        Double poolUtilization,
        Long artifactCount,
        Long artifactVersionCount,
        Long kafkaConsumerLag
) {

    /**
     * Whether any Pod exposed the request counter at all.
     */
    public boolean requestMetricSeen() {
        return !requestCounters.isEmpty();
    }

    /**
     * The cumulative request counters read from a single Pod.
     *
     * @param requests     all REST API requests, across every status code group
     * @param serverErrors the subset answered with a server error status
     */
    public record RequestCounters(double requests, double serverErrors) {
    }
}
