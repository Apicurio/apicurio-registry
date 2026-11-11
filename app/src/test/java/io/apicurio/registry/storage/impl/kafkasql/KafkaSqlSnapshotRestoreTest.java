package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import java.time.Duration;
import java.util.List;

import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSnapshotRestoreTestResource.AFTER_MARKER_GROUP;
import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSnapshotRestoreTestResource.BEFORE_MARKER_GROUP;
import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSnapshotRestoreTestResource.SNAPSHOT_ONLY_GROUP;
import static org.awaitility.Awaitility.await;

/**
 * Starts a fresh registry against a journal and a snapshots topic that already hold one snapshot
 * (seeded by {@link KafkaSqlSnapshotRestoreTestResource}) and checks that the registry restores
 * it instead of replaying the whole journal. Disabled in cluster mode, where there is no dedicated
 * broker to seed before the registry starts.
 */
@QuarkusTest
@TestProfile(KafkaSqlSnapshotRestoreTest.SnapshotRestoreProfile.class)
@Tag(ApicurioTestTags.SLOW)
@DisabledIfSystemProperty(named = "cluster.tests", matches = "true")
public class KafkaSqlSnapshotRestoreTest {

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
                "The journal record before the snapshot marker was replayed");
    }

    public static class SnapshotRestoreProfile extends KafkasqlTestProfile {
        @Override
        public List<TestResourceEntry> testResources() {
            return List.of(new TestResourceEntry(KafkaSqlSnapshotRestoreTestResource.class));
        }
    }
}
