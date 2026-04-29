package io.apicurio.registry.storage;

import io.apicurio.registry.cdi.Current;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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

    @ConfigProperty(name = "apicurio.usage.telemetry.enabled", defaultValue = "false")
    boolean usageTelemetryEnabled;

    @Scheduled(delay = 120, concurrentExecution = SKIP, every = "{apicurio.usage.aggregation.every}")
    void run() {
        if (!usageTelemetryEnabled) {
            return;
        }
        try {
            if (storage.isReady()) {
                if (!storage.isReadOnly()) {
                    log.debug("Running usage aggregation job at {}", Instant.now());
                    storage.aggregateUsageData();
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
}
