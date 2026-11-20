package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import java.util.List;

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
public class KafkaSqlSnapshotRestoreTest extends AbstractKafkaSqlSnapshotRestoreTest {

    public static class SnapshotRestoreProfile extends KafkasqlTestProfile {
        @Override
        public List<TestResourceEntry> testResources() {
            return List.of(new TestResourceEntry(KafkaSqlSnapshotRestoreTestResource.class));
        }
    }
}
