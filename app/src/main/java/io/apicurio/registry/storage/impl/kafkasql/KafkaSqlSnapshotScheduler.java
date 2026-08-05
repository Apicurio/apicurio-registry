package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;
import static io.apicurio.registry.utils.CollectionsUtil.toProperties;
import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * Periodically triggers creation of a KafkaSQL snapshot, bounding the growth of the "kafkasql-journal" topic.
 * Only active when the KafkaSQL storage variant is selected and scheduled snapshots are explicitly enabled.
 * <p>
 * {@code @LookupIfProperty} only gates programmatic/CDI lookup; the Quarkus scheduler discovers
 * {@code @Scheduled} methods at build time regardless of it. The runtime checks in {@link #run()} are what
 * actually prevent this from running on other storage variants or when disabled.
 * <p>
 * In a multi-replica deployment, every replica runs its own scheduler. To reduce redundant snapshots, the
 * scheduler checks the {@code kafkasql-snapshots} topic before triggering: if a snapshot was published
 * within the configured interval, the run is skipped. This is best-effort deduplication (not a hard
 * guarantee), since it relies on a check-then-act pattern without distributed locking.
 */
@ApplicationScoped
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
public class KafkaSqlSnapshotScheduler {

    private static final String KAFKASQL_STORAGE_KIND = "kafkasql";
    private static final long MIN_INITIAL_DELAY_SECONDS = 60L;
    private static final long MAX_INITIAL_DELAY_JITTER_SECONDS = 30L;

    @ConfigProperty(name = "apicurio.storage.kind")
    @Info(category = CATEGORY_STORAGE, description = "Application storage variant, for example, sql, kafkasql, gitops, or kubernetesops", availableSince = "3.0.0")
    String registryStorageType;

    @Dynamic(label = "Scheduled KafkaSQL snapshots", description = "When enabled, KafkaSQL snapshots are created automatically at the interval defined by apicurio.kafkasql.snapshot.every.seconds.", requires = "apicurio.storage.kind=kafkasql")
    @ConfigProperty(name = "apicurio.kafkasql.snapshot.scheduled.enabled", defaultValue = "false")
    @Info(category = CATEGORY_STORAGE, description = "Enable automatic periodic KafkaSQL snapshot creation.", availableSince = "3.3.2")
    Supplier<Boolean> scheduledSnapshotsEnabled;

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    KafkaSqlConfiguration configuration;

    volatile boolean initialDelayApplied = false;

    @Scheduled(delay = MIN_INITIAL_DELAY_SECONDS, delayUnit = TimeUnit.SECONDS, concurrentExecution = SKIP, every = "{apicurio.kafkasql.snapshot.every.seconds}")
    void run() {
        if (!KAFKASQL_STORAGE_KIND.equals(registryStorageType) || !scheduledSnapshotsEnabled.get()) {
            return;
        }
        if (!initialDelayApplied) {
            initialDelayApplied = true;
            applyJitter();
        }
        try {
            triggerIfWritable();
        } catch (Exception ex) {
            log.error("Exception thrown when running scheduled KafkaSQL snapshot creation", ex);
        }
    }

    /**
     * Sleeps for a random jitter period on the first invocation to reduce the chance that all replicas
     * fire simultaneously after a coordinated restart.
     */
    private void applyJitter() {
        try {
            long jitterMs = ThreadLocalRandom.current().nextLong(MAX_INITIAL_DELAY_JITTER_SECONDS * 1000);
            log.debug("Applying {}ms jitter before first scheduled snapshot check.", jitterMs);
            Thread.sleep(jitterMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        if (recentSnapshotExists()) {
            log.debug("Skipping scheduled KafkaSQL snapshot creation because a recent snapshot already exists.");
            return;
        }
        log.info("Running scheduled KafkaSQL snapshot creation at {}", Instant.now());
        storage.triggerSnapshotCreation();
    }

    /**
     * Checks the {@code kafkasql-snapshots} topic for the most recent snapshot record. Returns {@code true}
     * if a snapshot was published within the configured interval, indicating that another replica (or a
     * manual trigger) already created one and this replica should skip.
     * <p>
     * Fails closed: if the check encounters an error, returns {@code true} to avoid a snapshot storm.
     */
    boolean recentSnapshotExists() {
        try {
            long intervalMs = parseDurationMs(configuration.getSnapshotEvery());
            long cutoff = System.currentTimeMillis() - intervalMs;

            TopicPartition partition = new TopicPartition(configuration.getSnapshotsTopic(), 0);
            List<TopicPartition> partitions = Collections.singletonList(partition);

            try (KafkaConsumer<byte[], byte[]> consumer = createSnapshotsConsumer()) {
                consumer.assign(partitions);
                Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
                long endOffset = endOffsets.getOrDefault(partition, 0L);

                if (endOffset == 0) {
                    return false;
                }

                consumer.seek(partition, endOffset - 1);
                ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofSeconds(5));
                for (ConsumerRecord<byte[], byte[]> record : records) {
                    if (record.timestamp() > cutoff) {
                        log.debug("Found recent snapshot (age {}s, interval {}s).",
                                (System.currentTimeMillis() - record.timestamp()) / 1000, intervalMs / 1000);
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception ex) {
            log.warn("Failed to check snapshots topic for recent snapshots, skipping this cycle.", ex);
            return true;
        }
    }

    private KafkaConsumer<byte[], byte[]> createSnapshotsConsumer() {
        return new KafkaConsumer<>(toProperties(configuration.getConsumerProperties()),
                new ByteArrayDeserializer(), new ByteArrayDeserializer());
    }

    /**
     * Parses a Quarkus-style duration string to milliseconds. Supports:
     * <ul>
     *   <li>ISO-8601 format: {@code PT24H}, {@code PT86400S}</li>
     *   <li>Simplified format with suffix: {@code 86400s}, {@code 1440m}, {@code 24h}, {@code 1d}</li>
     *   <li>Bare number (interpreted as seconds): {@code 86400}</li>
     * </ul>
     */
    static long parseDurationMs(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("PT") || trimmed.startsWith("pt") || trimmed.startsWith("-PT")) {
            return Duration.parse(trimmed).toMillis();
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Empty duration value");
        }
        char lastChar = Character.toLowerCase(trimmed.charAt(trimmed.length() - 1));
        if (Character.isDigit(lastChar)) {
            return Long.parseLong(trimmed) * 1000L;
        }
        long number = Long.parseLong(trimmed.substring(0, trimmed.length() - 1).trim());
        switch (lastChar) {
            case 's':
                return number * 1000L;
            case 'm':
                return number * 60_000L;
            case 'h':
                return number * 3_600_000L;
            case 'd':
                return number * 86_400_000L;
            default:
                throw new IllegalArgumentException(
                        String.format(Locale.ROOT, "Unknown duration suffix '%c' in '%s'", lastChar, trimmed));
        }
    }
}
