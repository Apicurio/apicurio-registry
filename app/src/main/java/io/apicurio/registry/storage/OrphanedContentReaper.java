package io.apicurio.registry.storage;

import io.apicurio.registry.cdi.Current;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.time.Instant;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * Periodically cleanup orphaned content (content rows not referenced by any artifact version).
 */
@ApplicationScoped
public class OrphanedContentReaper {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * Minimal granularity is 1 minute.
     */
    @Scheduled(delay = 60, concurrentExecution = SKIP, every = "{apicurio.storage.orphan-cleanup.every}")
    void run() {
        try {
            if (storage.isReady()) {
                if (!storage.isReadOnly()) {
                    log.debug("Running orphaned content cleanup job at {}", Instant.now());
                    reap();
                } else {
                    log.debug("Skipping orphaned content cleanup because storage is in read-only mode.");
                }
            } else {
                log.debug("Skipping orphaned content cleanup because storage is not ready.");
            }
        } catch (Exception ex) {
            log.error("Exception thrown when running orphaned content cleanup job", ex);
        }
    }

    /**
     * Delete any rows in the "content" table that are not referenced by any artifact version.
     */
    void reap() {
        storage.deleteAllOrphanedContent();
    }
}
