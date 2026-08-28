package io.apicurio.registry.operator.metrics;

import java.time.Instant;

/**
 * Raw metric values aggregated across the Apicurio Registry application Pods at a single point in time.
 * <p>
 * Counters are kept raw so that two snapshots can be differenced into a rate. Gauges are already aggregated,
 * because averaging or maximizing them later would not be meaningful.
 *
 * @param timestamp           when the scrape completed
 * @param scrapedPods         how many Pods answered
 * @param requestsTotal       cumulative REST API request count, summed across Pods
 * @param serverErrorsTotal   cumulative count of requests answered with a 5xx status, summed across Pods
 * @param requestMetricSeen   whether any Pod exposed the request counter at all
 * @param poolUtilization     mean fraction of in-use database connections, null when not exposed
 * @param artifactCount       number of artifacts in storage, null when not exposed
 * @param artifactVersionCount number of artifact versions in storage, null when not exposed
 * @param kafkaConsumerLag    highest consumer lag reported by any Pod, null when not exposed
 */
public record RegistryMetricsSnapshot(
        Instant timestamp,
        int scrapedPods,
        double requestsTotal,
        double serverErrorsTotal,
        boolean requestMetricSeen,
        Double poolUtilization,
        Long artifactCount,
        Long artifactVersionCount,
        Long kafkaConsumerLag
) {
}
