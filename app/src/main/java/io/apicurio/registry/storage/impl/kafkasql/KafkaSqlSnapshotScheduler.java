package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;
import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * Periodically triggers creation of a KafkaSQL snapshot, bounding the growth of the "kafkasql-journal" topic.
 * Only active when the KafkaSQL storage variant is selected.
 * <p>
 * {@code @LookupIfProperty} only gates programmatic/CDI lookup; the Quarkus scheduler discovers
 * {@code @Scheduled} methods at build time regardless of it. The {@link #registryStorageType} check below
 * is what actually prevents this from running on other storage variants, matching the pattern used by
 * {@code GitOpsRegistryStorage} and {@code KubernetesOpsRegistryStorage}.
 */
@ApplicationScoped
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
public class KafkaSqlSnapshotScheduler {

    private static final long INITIAL_DELAY_SECONDS = 60L;
    private static final String KAFKASQL_STORAGE_KIND = "kafkasql";

    @ConfigProperty(name = "apicurio.storage.kind")
    @Info(category = CATEGORY_STORAGE, description = "Application storage variant, for example, sql, kafkasql, gitops, or kubernetesops", availableSince = "3.0.0")
    String registryStorageType;

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Scheduled(delay = INITIAL_DELAY_SECONDS, delayUnit = TimeUnit.SECONDS, concurrentExecution = SKIP, every = "{apicurio.kafkasql.snapshot.every.seconds}")
    void run() {
        if (!KAFKASQL_STORAGE_KIND.equals(registryStorageType)) {
            return;
        }
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
