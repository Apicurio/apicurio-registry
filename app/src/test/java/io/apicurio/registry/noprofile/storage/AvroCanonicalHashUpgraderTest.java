package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.avro.content.canon.EnhancedAvroContentCanonicalizer;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.upgrader.AvroCanonicalHashUpgrader;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.avro.Schema;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

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

    @Test
    void testUpgraderUsesCorrectArtifactTypeNotContentType() throws Exception {
        String artifactId = "testArtifactTypeVsContentType";
        String avroSchema = "{\"type\":\"record\",\"name\":\"TypeTest\","
                + "\"namespace\":\"com.example\","
                + "\"fields\":[{\"name\":\"name\",\"type\":\"string\"}]}";

        createArtifact(GROUP_ID, artifactId, ArtifactType.AVRO, avroSchema,
                ContentTypes.APPLICATION_JSON);

        StoredArtifactVersionDto storedVersion = storage.getArtifactVersionContent(GROUP_ID, artifactId,
                "1");
        long contentId = storedVersion.getContentId();

        // Independently compute the expected canonical hash using the same canonicalization
        // that the upgrader uses (EnhancedAvroContentCanonicalizer).
        Schema normalized = EnhancedAvroContentCanonicalizer
                .normalizeSchema(new Schema.Parser().parse(avroSchema));
        String expectedAvroHash = DigestUtils
                .sha256Hex(normalized.toString().getBytes(StandardCharsets.UTF_8));

        // Stored hash must match the Avro canonical hash, not some other canonicalization
        assertEquals(expectedAvroHash, getCanonicalHash(contentId));
        // Content type in the DB must remain the MIME type, not the artifact type
        assertEquals(ContentTypes.APPLICATION_JSON, getContentType(contentId));
    }

    @Test
    void testUpgraderRecomputesHashForSchemaWithArtifactReferences() throws Exception {
        String addressArtifactId = "testUpgraderAddressRef";
        String orderArtifactId = "testUpgraderOrderWithRef";

        String addressSchema = "{\"type\":\"record\",\"name\":\"Address\","
                + "\"namespace\":\"com.example.upgrader\","
                + "\"fields\":[{\"name\":\"street\",\"type\":\"string\"},"
                + "{\"name\":\"zip\",\"type\":\"string\"}]}";

        CreateArtifactResponse addressResponse = createArtifact(GROUP_ID, addressArtifactId,
                ArtifactType.AVRO, addressSchema, ContentTypes.APPLICATION_JSON);
        String addressVersion = addressResponse.getVersion().getVersion();

        ArtifactReference addressRef = new ArtifactReference();
        addressRef.setGroupId(GROUP_ID);
        addressRef.setArtifactId(addressArtifactId);
        addressRef.setVersion(addressVersion);
        addressRef.setName("com.example.upgrader.Address");

        String orderSchema = "{\"type\":\"record\",\"name\":\"Order\","
                + "\"namespace\":\"com.example.upgrader\","
                + "\"fields\":[{\"name\":\"id\",\"type\":\"string\"},"
                + "{\"name\":\"shippingAddress\",\"type\":\"com.example.upgrader.Address\"}]}";

        createArtifactWithReferences(GROUP_ID, orderArtifactId, ArtifactType.AVRO, orderSchema,
                ContentTypes.APPLICATION_JSON, Collections.singletonList(addressRef));

        StoredArtifactVersionDto storedVersion = storage.getArtifactVersionContent(GROUP_ID, orderArtifactId,
                "1");
        long contentId = storedVersion.getContentId();

        List<ArtifactReferenceDto> refs = storedVersion.getReferences();
        assertNotNull(refs);
        assertEquals(1, refs.size());

        String correctCanonicalHash = getCanonicalHash(contentId);
        assertNotNull(correctCanonicalHash);

        String fakeHash = "1111111111111111111111111111111111111111111111111111111111111111";
        setCanonicalHash(contentId, fakeHash);
        assertEquals(fakeHash, getCanonicalHash(contentId));

        // Exercises resolveReference() when recomputing the hash
        runUpgrader();

        assertEquals(correctCanonicalHash, getCanonicalHash(contentId));
    }

    @Test
    void testUpgraderLeavesHashUnchangedWhenReferenceResolutionFails() throws Exception {
        String addressArtifactId = "testUpgraderMissingRefAddress";
        String orderArtifactId = "testUpgraderMissingRefOrder";

        String addressSchema = "{\"type\":\"record\",\"name\":\"MissingRefAddress\","
                + "\"namespace\":\"com.example.upgrader\","
                + "\"fields\":[{\"name\":\"city\",\"type\":\"string\"}]}";

        CreateArtifactResponse addressResponse = createArtifact(GROUP_ID, addressArtifactId,
                ArtifactType.AVRO, addressSchema, ContentTypes.APPLICATION_JSON);

        ArtifactReference addressRef = new ArtifactReference();
        addressRef.setGroupId(GROUP_ID);
        addressRef.setArtifactId(addressArtifactId);
        addressRef.setVersion(addressResponse.getVersion().getVersion());
        addressRef.setName("com.example.upgrader.MissingRefAddress");

        String orderSchema = "{\"type\":\"record\",\"name\":\"MissingRefOrder\","
                + "\"namespace\":\"com.example.upgrader\","
                + "\"fields\":[{\"name\":\"id\",\"type\":\"string\"},"
                + "{\"name\":\"addr\",\"type\":\"com.example.upgrader.MissingRefAddress\"}]}";

        createArtifactWithReferences(GROUP_ID, orderArtifactId, ArtifactType.AVRO, orderSchema,
                ContentTypes.APPLICATION_JSON, Collections.singletonList(addressRef));

        StoredArtifactVersionDto storedVersion = storage.getArtifactVersionContent(GROUP_ID, orderArtifactId,
                "1");
        long contentId = storedVersion.getContentId();

        // Point refs at a version that does not exist so resolveReference() fails
        String brokenRefs = "[{\"groupId\":\"" + GROUP_ID + "\",\"artifactId\":\"" + addressArtifactId
                + "\",\"version\":\"999\",\"name\":\"com.example.upgrader.MissingRefAddress\"}]";
        setRefs(contentId, brokenRefs);

        String fakeHash = "2222222222222222222222222222222222222222222222222222222222222222";
        setCanonicalHash(contentId, fakeHash);

        // Upgrade should not throw; failed rows keep their (stale) hash
        runUpgrader();

        assertEquals(fakeHash, getCanonicalHash(contentId));
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

    private void setRefs(long contentId, String refs) {
        handles.<Void, RuntimeException>withHandleNoException((Handle handle) -> {
            handle.createUpdate("UPDATE content SET refs = ? WHERE contentId = ?").bind(0, refs)
                    .bind(1, contentId).execute();
            return null;
        });
    }

    private String getContentType(long contentId) {
        return handles.withHandleNoException(
                (Handle handle) -> handle
                        .createQuery("SELECT contentType FROM content WHERE contentId = ?")
                        .bind(0, contentId).map(rs -> rs.getString("contentType")).one());
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
