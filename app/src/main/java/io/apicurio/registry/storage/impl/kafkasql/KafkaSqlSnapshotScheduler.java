package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * Periodically triggers creation of a KafkaSQL snapshot, bounding the growth of the "kafkasql-journal" topic.
 * Only active when the KafkaSQL storage variant is selected.
 */
@ApplicationScoped
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
public class KafkaSqlSnapshotScheduler {

    private static final long INITIAL_DELAY_SECONDS = 60L;

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Scheduled(delay = INITIAL_DELAY_SECONDS, delayUnit = TimeUnit.SECONDS, concurrentExecution = SKIP, every = "{apicurio.kafkasql.snapshot.every.seconds}")
    void run() {
        try {
            triggerIfWritable();
        } catch (Exception ex) {
            log.error("Exception thrown when running scheduled KafkaSQL snapshot creation", ex);
        }
    }

    private void triggerIfWritable() {
        if (!storage.isReady()) {
            log.debug("Skipping scheduled KafkaSQL snapshot creation because the storage is not ready.");
            return;
        }
        if (storage.isReadOnly()) {
            log.debug("Skipping scheduled KafkaSQL snapshot creation because the storage is in read-only mode.");
            return;
        }
        log.debug("Running scheduled KafkaSQL snapshot creation at {}", Instant.now());
        storage.triggerSnapshotCreation();
    }
}
