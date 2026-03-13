package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.upgrader.AvroCanonicalHashUpgrader;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
public class AvroCanonicalHashUpgraderTest extends AbstractResourceTestBase {

    private static final String GROUP_ID = "AvroCanonicalHashUpgraderTest";

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    HandleFactory handles;

    @Test
    void testUpgraderRecomputesAvroCanonicalHash() throws Exception {
        // 1. Create an Avro artifact through the normal API
        String artifactId = "testUpgraderRecomputesHash";
        String avroSchema = "{\"type\":\"record\",\"name\":\"TestRecord\","
                + "\"namespace\":\"com.example\","
                + "\"fields\":[{\"name\":\"field1\",\"type\":\"string\"},"
                + "{\"name\":\"field2\",\"type\":\"int\"}]}";

        createArtifact(GROUP_ID, artifactId, ArtifactType.AVRO, avroSchema,
                ContentTypes.APPLICATION_JSON);

        // 2. Get the stored content and its current (correct) canonical hash
        StoredArtifactVersionDto storedVersion = storage.getArtifactVersionContent(GROUP_ID, artifactId,
                "1");
        long contentId = storedVersion.getContentId();

        String correctCanonicalHash = getCanonicalHash(contentId);
        assertNotNull(correctCanonicalHash);

        // 3. Corrupt the canonical hash directly in the DB
        String fakeHash = "0000000000000000000000000000000000000000000000000000000000000000";
        setCanonicalHash(contentId, fakeHash);

        // Verify it was corrupted
        assertEquals(fakeHash, getCanonicalHash(contentId));

        // 4. Run the upgrader
        runUpgrader();

        // 5. Verify the hash was restored to the correct value
        assertEquals(correctCanonicalHash, getCanonicalHash(contentId));
    }

    @Test
    void testUpgraderDoesNotTouchNonAvroContent() throws Exception {
        // 1. Create a JSON Schema artifact
        String artifactId = "testUpgraderSkipsNonAvro";
        String jsonSchema = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}";

        createArtifact(GROUP_ID, artifactId, ArtifactType.JSON, jsonSchema,
                ContentTypes.APPLICATION_JSON);

        StoredArtifactVersionDto storedVersion = storage.getArtifactVersionContent(GROUP_ID, artifactId,
                "1");
        long contentId = storedVersion.getContentId();

        // 2. Corrupt the canonical hash
        String fakeHash = "0000000000000000000000000000000000000000000000000000000000000000";
        setCanonicalHash(contentId, fakeHash);

        // 3. Run the upgrader
        runUpgrader();

        // 4. Verify the hash was NOT changed (upgrader only touches Avro)
        assertEquals(fakeHash, getCanonicalHash(contentId));
    }

    @Test
    void testUpgraderSkipsAlreadyCorrectHashes() throws Exception {
        // 1. Create an Avro artifact
        String artifactId = "testUpgraderSkipsCorrectHash";
        String avroSchema = "{\"type\":\"record\",\"name\":\"AlreadyCorrect\","
                + "\"namespace\":\"com.example\","
                + "\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]}";

        createArtifact(GROUP_ID, artifactId, ArtifactType.AVRO, avroSchema,
                ContentTypes.APPLICATION_JSON);

        StoredArtifactVersionDto storedVersion = storage.getArtifactVersionContent(GROUP_ID, artifactId,
                "1");
        long contentId = storedVersion.getContentId();

        String hashBefore = getCanonicalHash(contentId);

        // 2. Run the upgrader (hash should already be correct)
        runUpgrader();

        // 3. Verify hash is unchanged
        assertEquals(hashBefore, getCanonicalHash(contentId));
    }

    private String getCanonicalHash(long contentId) {
        return handles.withHandleNoException(
                (Handle handle) -> handle
                        .createQuery("SELECT canonicalHash FROM content WHERE contentId = ?")
                        .bind(0, contentId).map(rs -> rs.getString("canonicalHash")).one());
    }

    private void setCanonicalHash(long contentId, String hash) {
        handles.<Void, RuntimeException>withHandleNoException((Handle handle) -> {
            handle.createUpdate("UPDATE content SET canonicalHash = ? WHERE contentId = ?").bind(0, hash)
                    .bind(1, contentId).execute();
            return null;
        });
    }

    private void runUpgrader() {
        handles.<Void, RuntimeException>withHandleNoException((Handle handle) -> {
            try {
                new AvroCanonicalHashUpgrader().upgrade(handle);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }
}
