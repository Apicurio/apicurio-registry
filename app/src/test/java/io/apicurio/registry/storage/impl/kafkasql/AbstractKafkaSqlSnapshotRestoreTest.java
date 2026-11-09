package io.apicurio.registry.storage.impl.kafkasql;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSnapshotRestoreTestResource.AFTER_MARKER_GROUP;
import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSnapshotRestoreTestResource.BEFORE_MARKER_GROUP;
import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSnapshotRestoreTestResource.SNAPSHOT_ONLY_GROUP;
import static org.awaitility.Awaitility.await;

/**
 * Starts a fresh registry against the journal and snapshots topic that a
 * {@link KafkaSqlSnapshotRestoreTestResource} scenario seeded, and checks that the registry restores the
 * snapshot, applies the journal record after its marker, and does not apply the one before it.
 * Subclasses select the scenario through their test profile.
 */
public abstract class AbstractKafkaSqlSnapshotRestoreTest {

    @Inject
    KafkaSqlRegistryStorage storage;

    @Test
    public void testFreshRegistryRestoresSnapshotAndSkipsJournalBeforeMarker() {
        // The record after the marker is the last one seeded, so once it is applied the
        // consumer has gone through the marker and everything before it.
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            Assertions.assertTrue(storage.isReady(), "KafkaSQL storage did not bootstrap");
            Assertions.assertTrue(storage.isGroupExists(AFTER_MARKER_GROUP),
                    "The journal record after the snapshot marker was not applied");
        });

        Assertions.assertTrue(storage.isGroupExists(SNAPSHOT_ONLY_GROUP),
                "The group that exists only in the snapshot file is missing: the snapshot was not restored "
                        + "(never read off the snapshots topic, or rejected by the store path check)");
        Assertions.assertFalse(storage.isGroupExists(BEFORE_MARKER_GROUP),
                "A journal record before the snapshot marker was applied");
    }
}
