package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * Periodically removes consumed rows from the MySQL ID ticket tables. Other databases are unaffected.
 */
@ApplicationScoped
public class SequenceTicketReaper {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    SqlRegistryStorage sqlStorage;

    @Inject
    SqlStatements sqlStatements;

    @Scheduled(delay = 5, delayUnit = TimeUnit.MINUTES, every = "1h", concurrentExecution = SKIP)
    void run() {
        try {
            if ("mysql".equals(sqlStatements.dbType()) && storage.isReady() && !storage.isReadOnly()) {
                sqlStorage.pruneSequenceTickets();
            }
        } catch (Exception ex) {
            log.error("Exception thrown when pruning sequence ticket tables", ex);
        }
    }
}
