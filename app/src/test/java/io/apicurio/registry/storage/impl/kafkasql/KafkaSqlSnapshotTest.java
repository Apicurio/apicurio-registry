package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.storage.impl.sql.SqlRegistryStorage;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
public class KafkaSqlSnapshotTest extends AbstractResourceTestBase {

    private static final String NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID = "SNAPSHOT_TEST_GROUP_ID";

    @Inject
    KafkaSqlRegistryStorage kafkaSqlRegistryStorage;

    @Inject
    SqlRegistryStorage sqlRegistryStorage;

    @BeforeAll
    public void init() {
        // Create a bunch of artifacts and rules, so they're added on top of the snapshot.
        String simpleAvro = resourceToString("avro.json");

        for (int idx = 0; idx < 1000; idx++) {
            System.out.println("Iteration: " + idx);
            String artifactId = UUID.randomUUID().toString();
            CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                    simpleAvro, ContentTypes.APPLICATION_JSON);
            clientV3.groups().byGroupId(NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID).artifacts().post(createArtifact,
                    config -> config.headers.add("X-Registry-ArtifactId", artifactId));
            CreateRule createRule = new CreateRule();
            createRule.setRuleType(RuleType.VALIDITY);
            createRule.setConfig("SYNTAX_ONLY");
            clientV3.groups().byGroupId(NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID).artifacts()
                    .byArtifactId(artifactId).rules().post(createRule);
        }
    }

    @Test
    public void testSnapshotCreation() throws IOException {
        String snapshotLocation = kafkaSqlRegistryStorage.triggerSnapshotCreation();
        Path path = Path.of(snapshotLocation);
        try {
            Assertions.assertTrue(Files.exists(path));
            Assertions.assertTrue(snapshotLocation.endsWith(SqlStatements.COMPRESSED_SNAPSHOT_EXTENSION),
                    "Snapshot file should use the .sql.gz extension");
            Assertions.assertTrue(Files.size(path) > 0, "Snapshot file should not be empty");
            assertGzipCompressed(path);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void testSnapshotRestoreRoundTrip() throws IOException {
        long artifactCountBefore = clientV3.groups().byGroupId(NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID)
                .artifacts().get(config -> config.queryParameters.limit = 1).getCount();
        Assertions.assertTrue(artifactCountBefore > 0, "Artifacts should exist before snapshot");

        String snapshotLocation = kafkaSqlRegistryStorage.triggerSnapshotCreation();
        Path path = Path.of(snapshotLocation);
        try {
            Assertions.assertTrue(Files.exists(path));

            // Restore from the compressed snapshot into the same database. This exercises
            // H2's RUNSCRIPT FROM ? COMPRESSION GZIP path and verifies the snapshot is valid.
            sqlRegistryStorage.restoreFromSnapshot(snapshotLocation);

            long artifactCountAfter = clientV3.groups().byGroupId(NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID)
                    .artifacts().get(config -> config.queryParameters.limit = 1).getCount();
            Assertions.assertEquals(artifactCountBefore, artifactCountAfter,
                    "Artifact count should be the same after restoring from snapshot");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    /**
     * Asserts that the file at the given path is valid GZIP data by attempting to read it
     * through a GZIPInputStream.
     */
    private void assertGzipCompressed(Path path) throws IOException {
        try (InputStream fis = Files.newInputStream(path);
             GZIPInputStream gzis = new GZIPInputStream(fis)) {
            byte[] buffer = new byte[1024];
            int totalRead = 0;
            int bytesRead;
            while ((bytesRead = gzis.read(buffer)) != -1) {
                totalRead += bytesRead;
            }
            Assertions.assertTrue(totalRead > 0, "GZIP content should decompress to non-empty data");
        }
    }
}
