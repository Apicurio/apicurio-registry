package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import java.util.List;
import java.util.Map;

import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSnapshotRestoreTestResource.SCENARIO;
import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSnapshotRestoreTestResource.SEEK_TO_MARKER;

/**
 * Restores a snapshot whose record says where its marker is in the journal, and checks that the
 * journal consumer starts at that marker. The seeded journal holds an earlier copy of the same
 * marker followed by {@link KafkaSqlSnapshotRestoreTestResource#BEFORE_MARKER_GROUP}: reading the
 * journal from the beginning would stop at the copy and apply that group.
 */
@QuarkusTest
@TestProfile(KafkaSqlSnapshotMarkerSeekTest.SeekToMarkerProfile.class)
@Tag(ApicurioTestTags.SLOW)
@DisabledIfSystemProperty(named = "cluster.tests", matches = "true")
public class KafkaSqlSnapshotMarkerSeekTest extends AbstractKafkaSqlSnapshotRestoreTest {

    public static class SeekToMarkerProfile extends KafkasqlTestProfile {
        @Override
        public List<TestResourceEntry> testResources() {
            return List.of(new TestResourceEntry(KafkaSqlSnapshotRestoreTestResource.class,
                    Map.of(SCENARIO, SEEK_TO_MARKER)));
        }
    }
}
