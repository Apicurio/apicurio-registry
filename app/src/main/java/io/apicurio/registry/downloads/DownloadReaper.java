package io.apicurio.registry.downloads;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.time.Instant;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * Periodically cleanup data of downloads marked as deleted.
 *
 */
@ApplicationScoped
public class DownloadReaper {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * Minimal granularity is 1 minute.
     */
    @Scheduled(delay = 2, concurrentExecution = SKIP, every = "{registry.downloads.reaper.every}")
    void run() {
        try {
            if(storage.isReady()) {
                if(!storage.isReadOnly()) {
                    log.debug("Running download reaper job at {}", Instant.now());
                    reap();
                } else {
                    log.debug("Skipping download reaper job because the storage is in read-only mode.");
                }
            } else {
                log.debug("Skipping download reaper job because the storage is not ready.");
            }
        }
        catch (Exception ex) {
            log.error("Exception thrown when running download reaper job", ex);
        }
    }

    /**
     * Delete any rows in the "downloads" table that represent downloads that have expired.
     */
    void reap() {
        storage.deleteAllExpiredDownloads();
    }
}
