package io.apicurio.registry.storage;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.metrics.OTelMetricsProvider;
import io.apicurio.registry.storage.dto.UsageSummaryCountsDto;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.time.Instant;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class UsageAggregationJob {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    OTelMetricsProvider otelMetrics;

    @Inject
    UsageTelemetryConfig usageConfig;

    @Scheduled(delay = 120, concurrentExecution = SKIP, every = "{apicurio.usage.aggregation.every}")
    void run() {
        if (!usageConfig.isEnabled()) {
            return;
        }
        try {
            if (storage.isReady()) {
                if (!storage.isReadOnly()) {
                    log.debug("Running usage aggregation job at {}", Instant.now());
                    storage.aggregateUsageData();
                    updateOTelGauges();
                } else {
                    log.debug("Skipping usage aggregation because storage is in read-only mode.");
                }
            } else {
                log.debug("Skipping usage aggregation because storage is not ready.");
            }
        } catch (Exception ex) {
            log.error("Exception thrown when running usage aggregation job", ex);
        }
    }

    private void updateOTelGauges() {
        try {
            long nowMs = System.currentTimeMillis();
            long activeMs = usageConfig.getActiveMsThreshold();
            long staleMs = usageConfig.getStaleMsThreshold();
            UsageSummaryCountsDto counts = storage.getUsageSummaryCounts(nowMs, activeMs, staleMs, staleMs);
            otelMetrics.updateUsageSummaryCounts(counts.getActive(), counts.getStale(), counts.getDead());
        } catch (Exception ex) {
            log.debug("Failed to update OTel usage gauges", ex);
        }
    }
}
