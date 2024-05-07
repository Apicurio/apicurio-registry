package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/*
 * Periodically triggers Kafkasql snapshots
 *
 */
@ApplicationScoped
public class KafkaSqlSnapshotManager {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    KafkaSqlConfiguration kafkaSqlConfiguration;

    @Scheduled(delay = 2, concurrentExecution = SKIP, every = "{apicurio.kafkasql.snapshot.every.seconds}")
    void run() {
        try {
            if (storage.isReady()) {
                log.debug("Running storage snapshot job at {}", Instant.now());
                triggerInternalStorageSnapshot();
            }
            else {
                log.debug("Skipping storage snapshot job because the storage is not ready.");
            }
        }
        catch (Exception ex) {
            log.error("Exception thrown when creating storage snapshot", ex);
        }
    }

    void triggerInternalStorageSnapshot() {
        Path path = Path.of(kafkaSqlConfiguration.snapshotLocation(), UUID.randomUUID().toString());
        storage.triggerSnapshotCreation(path.toString());


        //TODO if everything is ok the snapshot has been created here, we must send the message to the snapshots topic with the information to retrieve it.
    }
}
