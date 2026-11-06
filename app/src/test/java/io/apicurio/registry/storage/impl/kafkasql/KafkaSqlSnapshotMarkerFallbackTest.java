package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import java.util.List;
import java.util.Map;

import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSnapshotRestoreTestResource.POSITION_PAST_MARKER;
import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSnapshotRestoreTestResource.SCENARIO;

/**
 * Restores a snapshot whose record points past its marker, at the journal record after it. Starting
 * there would never meet the marker, so the registry would discard every later record, its own
 * bootstrap message included, and never become ready. It must notice that the record at the position
 * is not the marker and read the journal from the beginning instead.
 */
@QuarkusTest
@TestProfile(KafkaSqlSnapshotMarkerFallbackTest.PositionPastMarkerProfile.class)
@Tag(ApicurioTestTags.SLOW)
@DisabledIfSystemProperty(named = "cluster.tests", matches = "true")
public class KafkaSqlSnapshotMarkerFallbackTest extends AbstractKafkaSqlSnapshotRestoreTest {

    public static class PositionPastMarkerProfile extends KafkasqlTestProfile {
        @Override
        public List<TestResourceEntry> testResources() {
            return List.of(new TestResourceEntry(KafkaSqlSnapshotRestoreTestResource.class,
                    Map.of(SCENARIO, POSITION_PAST_MARKER)));
        }
    }
}
