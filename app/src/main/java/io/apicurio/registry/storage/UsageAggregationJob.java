package io.apicurio.registry.storage;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.metrics.OTelMetricsProvider;
import io.apicurio.registry.storage.dto.UsageSummaryCountsDto;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

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

    private volatile UsageSummaryCountsDto cachedCounts;

    @Scheduled(delay = 30, delayUnit = TimeUnit.SECONDS, concurrentExecution = SKIP, every = "{apicurio.usage.aggregation.every}")
    void run() {
        if (!usageConfig.isEnabled() || !storage.isReady()) {
            return;
        }
        try {
            long nowMs = System.currentTimeMillis();
            long activeMs = usageConfig.getActiveMsThreshold();
            long staleMs = usageConfig.getStaleMsThreshold();
            UsageSummaryCountsDto counts = storage.getUsageSummaryCounts(nowMs, activeMs, staleMs);
            cachedCounts = counts;
            otelMetrics.updateUsageSummaryCounts(counts.getActive(), counts.getStale(), counts.getDead());

            long retentionMs = usageConfig.getRetentionMs();
            if (retentionMs > 0 && !storage.isReadOnly()) {
                storage.deleteOldUsageEvents(nowMs - retentionMs);
            }
        } catch (Exception ex) {
            log.error("Exception thrown when running usage metrics job", ex);
        }
    }

    public UsageSummaryCountsDto getCachedCounts() {
        return cachedCounts;
    }
}
