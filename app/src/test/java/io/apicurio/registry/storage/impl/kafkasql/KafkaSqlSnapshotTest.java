package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
    public void testSnapshotContainsDatabaseContent() throws IOException {
        String snapshotLocation = kafkaSqlRegistryStorage.triggerSnapshotCreation();
        Path path = Path.of(snapshotLocation);
        try {
            Assertions.assertTrue(Files.exists(path));

            // Decompress and read the snapshot content to verify it contains valid H2 SQL
            // statements including the data we created.
            String content = readGzipContent(path);
            Assertions.assertTrue(content.contains("CREATE MEMORY TABLE"),
                    "Snapshot should contain H2 CREATE TABLE statements");
            Assertions.assertTrue(content.contains("SNAPSHOT_TEST_GROUP_ID"),
                    "Snapshot should contain the test group's artifact data");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    /**
     * Asserts that the file at the given path is valid GZIP data by attempting to read it
     * through a GZIPInputStream.
     */
    private void assertGzipCompressed(Path path) throws IOException {
        String content = readGzipContent(path);
        Assertions.assertFalse(content.isEmpty(), "GZIP content should decompress to non-empty data");
    }

    /**
     * Reads and decompresses a GZIP file, returning the content as a string.
     */
    private String readGzipContent(Path path) throws IOException {
        try (InputStream fis = Files.newInputStream(path);
             GZIPInputStream gzis = new GZIPInputStream(fis);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = gzis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }
}
