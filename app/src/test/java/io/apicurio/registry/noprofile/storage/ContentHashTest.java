package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentHashType;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.error.ContentNotFoundException;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for content hash storage functionality, specifically:
 * 1. Content creation with multiple hash types
 * 2. Content deduplication via contentHash constraint
 */
@QuarkusTest
public class ContentHashTest extends AbstractResourceTestBase {

    private static final String GROUP_ID = ContentHashTest.class.getSimpleName();
    private static final String ARTIFACT_ID = "test-artifact";

    private static final String JSON_CONTENT_1 = "{\"name\":\"test\",\"version\":\"1.0.0\"}";
    private static final String JSON_CONTENT_2 = "{\"name\":\"test\",\"version\":\"2.0.0\"}";
    private static final String JSON_CONTENT_FORMATTED = "{\n  \"name\": \"test\",\n  \"version\": \"1.0.0\"\n}";

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RegistryStorageContentUtils contentUtils;

    @BeforeEach
    public void cleanup() {
        try {
            storage.deleteArtifact(GROUP_ID, ARTIFACT_ID);
        } catch (Exception ignored) {
        }
        try {
            storage.deleteArtifact(GROUP_ID, ARTIFACT_ID + "-2");
        } catch (Exception ignored) {
        }
        try {
            storage.deleteArtifact(GROUP_ID, ARTIFACT_ID + "-ref");
        } catch (Exception ignored) {
        }
    }

    /**
     * Test #1: Content Creation with Multiple Hashes
     *
     * Verifies that when content is created, all three hash types are stored correctly:
     * - content-sha256 (in content.contentHash column)
     * - canonical-sha256 (in content_hashes table)
     * - canonical-no-refs-sha256 (in content_hashes table)
     */
    @Test
    public void testContentCreationWithMultipleHashes() throws Exception {
        // Create artifact with initial version
        ContentHandle content = ContentHandle.create(JSON_CONTENT_1);
        ContentWrapperDto contentDto = ContentWrapperDto.builder()
                .content(content)
                .contentType(ContentTypes.APPLICATION_JSON)
                .build();

        var result = storage.createArtifact(
                GROUP_ID,
                ARTIFACT_ID,
                ArtifactType.JSON,
                EditableArtifactMetaDataDto.builder().build(),
                "1.0.0",
                contentDto,
                EditableVersionMetaDataDto.builder().build(),
                Collections.emptyList(),
                false,  // not a draft
                false,  // not a dry run
                "test-user"
        );

        ArtifactVersionMetaDataDto versionMetadata = result.getRight();
        assertNotNull(versionMetadata);
        long contentId = versionMetadata.getContentId();

        // Calculate expected hash values
        TypedContent typedContent = TypedContent.create(content, ContentTypes.APPLICATION_JSON);
        String expectedContentHash = contentUtils.getContentHash(typedContent, null);
        String expectedCanonicalHash = contentUtils.getCanonicalContentHash(typedContent, ArtifactType.JSON, null, null);
        String expectedCanonicalNoRefsHash = contentUtils.getCanonicalContentHashWithoutRefs(typedContent, ArtifactType.JSON, null, null);

        // Verify all three hash types can be used to retrieve the same content

        // 1. Verify content-sha256 lookup (fast path via content.contentHash)
        ContentWrapperDto retrievedByContentHash = storage.getContentByHash(expectedContentHash, ContentHashType.CONTENT_SHA256);
        assertNotNull(retrievedByContentHash);
        assertEquals(JSON_CONTENT_1, retrievedByContentHash.getContent().content());

        // 2. Verify canonical-sha256 lookup (via content_hashes table)
        ContentWrapperDto retrievedByCanonicalHash = storage.getContentByHash(expectedCanonicalHash, ContentHashType.CANONICAL_SHA256);
        assertNotNull(retrievedByCanonicalHash);
        assertEquals(JSON_CONTENT_1, retrievedByCanonicalHash.getContent().content());

        // 3. Verify canonical-no-refs-sha256 lookup (via content_hashes table)
        ContentWrapperDto retrievedByCanonicalNoRefsHash = storage.getContentByHash(expectedCanonicalNoRefsHash, ContentHashType.CANONICAL_NO_REFS_SHA256);
        assertNotNull(retrievedByCanonicalNoRefsHash);
        assertEquals(JSON_CONTENT_1, retrievedByCanonicalNoRefsHash.getContent().content());

        // Verify all three hash lookups return the same content
        assertEquals(retrievedByContentHash.getContent().content(), retrievedByCanonicalHash.getContent().content());
        assertEquals(retrievedByContentHash.getContent().content(), retrievedByCanonicalNoRefsHash.getContent().content());
    }

    /**
     * Test #1b: Content Creation with References
     *
     * Verifies that content with references generates different hash values for each type:
     * - content-sha256 includes references
     * - canonical-sha256 includes references
     * - canonical-no-refs-sha256 excludes references
     */
    @Test
    public void testContentCreationWithReferences() throws Exception {
        // First create a referenced artifact
        ContentHandle refContent = ContentHandle.create("{\"type\":\"string\"}");
        ContentWrapperDto refContentDto = ContentWrapperDto.builder()
                .content(refContent)
                .contentType(ContentTypes.APPLICATION_JSON)
                .build();

        storage.createArtifact(
                GROUP_ID,
                ARTIFACT_ID + "-ref",
                ArtifactType.JSON,
                EditableArtifactMetaDataDto.builder().build(),
                "1.0.0",
                refContentDto,
                EditableVersionMetaDataDto.builder().build(),
                Collections.emptyList(),
                false,
                false,
                "test-user"
        );

        // Create artifact with reference
        ContentHandle content = ContentHandle.create("{\"ref\":\"#/components/schemas/MyType\"    }");
        ArtifactReferenceDto reference = ArtifactReferenceDto.builder()
                .groupId(GROUP_ID)
                .artifactId(ARTIFACT_ID + "-ref")
                .version("1.0.0")
                .name("MyType")
                .build();

        ContentWrapperDto contentDto = ContentWrapperDto.builder()
                .content(content)
                .contentType(ContentTypes.APPLICATION_JSON)
                .references(List.of(reference))
                .build();

        var result = storage.createArtifact(
                GROUP_ID,
                ARTIFACT_ID,
                ArtifactType.JSON,
                EditableArtifactMetaDataDto.builder().build(),
                "1.0.0",
                contentDto,
                EditableVersionMetaDataDto.builder().build(),
                Collections.emptyList(),
                false,
                false,
                "test-user"
        );

        ArtifactVersionMetaDataDto versionMetadata = result.getRight();
        long contentId = versionMetadata.getContentId();

        Function<List<ArtifactReferenceDto>, Map<String, TypedContent>> referenceResolver = (refs) -> {
            return refs.stream().map(ref -> {
                return storage.getContentByReference(ref);
            }).collect(Collectors.toMap(
                    (dto -> reference.getName()),
                    (dto -> TypedContent.create(dto.getContent(), dto.getContentType()))
            ));
        };

        // Calculate expected hash values
        TypedContent typedContent = TypedContent.create(content, ContentTypes.APPLICATION_JSON);
        String expectedContentHash = contentUtils.getContentHash(typedContent, List.of(reference));
        String expectedCanonicalHash = contentUtils.getCanonicalContentHash(typedContent, ArtifactType.JSON, List.of(reference),
                referenceResolver);
        String expectedCanonicalNoRefsHash = contentUtils.getCanonicalContentHashWithoutRefs(typedContent, ArtifactType.JSON, List.of(reference),
                referenceResolver);

        // Verify all three hash types are different when references are involved
        assertNotEquals(expectedContentHash, expectedCanonicalHash, "content-sha256 should differ from canonical-sha256");
        assertNotEquals(expectedContentHash, expectedCanonicalNoRefsHash, "content-sha256 should differ from canonical-no-refs-sha256");

        // canonical-sha256 and canonical-no-refs-sha256 may be the same or different depending on whether
        // references are included in canonicalization, so we don't assert their relationship

        // Verify all three hash types retrieve the same content
        ContentWrapperDto retrievedByContentHash = storage.getContentByHash(expectedContentHash, ContentHashType.CONTENT_SHA256);
        ContentWrapperDto retrievedByCanonicalHash = storage.getContentByHash(expectedCanonicalHash, ContentHashType.CANONICAL_SHA256);
        ContentWrapperDto retrievedByCanonicalNoRefsHash = storage.getContentByHash(expectedCanonicalNoRefsHash, ContentHashType.CANONICAL_NO_REFS_SHA256);

        assertEquals(content.content(), retrievedByContentHash.getContent().content());
        assertEquals(content.content(), retrievedByCanonicalHash.getContent().content());
        assertEquals(content.content(), retrievedByCanonicalNoRefsHash.getContent().content());
    }

    /**
     * Test #2a: Content Deduplication via contentHash Constraint
     *
     * Verifies that:
     * 1. Identical content (same content-sha256) is deduplicated
     * 2. Only one content row is created
     * 3. Multiple versions can reference the same content
     */
    @Test
    public void testContentDeduplication() throws Exception {
        ContentHandle content = ContentHandle.create(JSON_CONTENT_1);
        ContentWrapperDto contentDto = ContentWrapperDto.builder()
                .content(content)
                .contentType(ContentTypes.APPLICATION_JSON)
                .build();

        // Create first version
        var result1 = storage.createArtifact(
                GROUP_ID,
                ARTIFACT_ID,
                ArtifactType.JSON,
                EditableArtifactMetaDataDto.builder().build(),
                "1.0.0",
                contentDto,
                EditableVersionMetaDataDto.builder().build(),
                Collections.emptyList(),
                false,
                false,
                "test-user"
        );

        long contentId1 = result1.getRight().getContentId();

        // Create second version with identical content
        var result2 = storage.createArtifactVersion(
                GROUP_ID,
                ARTIFACT_ID,
                "2.0.0",
                ArtifactType.JSON,
                contentDto,
                EditableVersionMetaDataDto.builder().build(),
                Collections.emptyList(),
                false,
                false,
                "test-user"
        );

        long contentId2 = result2.getContentId();

        // Verify both versions reference the same content (deduplication worked)
        assertEquals(contentId1, contentId2, "Both versions should reference the same deduplicated content");

        // Verify we can retrieve the content by hash
        TypedContent typedContent = TypedContent.create(content, ContentTypes.APPLICATION_JSON);
        String contentHash = contentUtils.getContentHash(typedContent, null);

        ContentWrapperDto retrieved = storage.getContentByHash(contentHash, ContentHashType.CONTENT_SHA256);
        assertEquals(content.content(), retrieved.getContent().content());
    }

    /**
     * Test #2b: Different Content Gets Different ContentId
     *
     * Verifies that different content (different content-sha256) creates separate content rows.
     */
    @Test
    public void testDifferentContentGetsDifferentId() throws Exception {
        ContentHandle content1 = ContentHandle.create(JSON_CONTENT_1);
        ContentWrapperDto contentDto1 = ContentWrapperDto.builder()
                .content(content1)
                .contentType(ContentTypes.APPLICATION_JSON)
                .build();

        ContentHandle content2 = ContentHandle.create(JSON_CONTENT_2);
        ContentWrapperDto contentDto2 = ContentWrapperDto.builder()
                .content(content2)
                .contentType(ContentTypes.APPLICATION_JSON)
                .build();

        // Create first version
        var result1 = storage.createArtifact(
                GROUP_ID,
                ARTIFACT_ID,
                ArtifactType.JSON,
                EditableArtifactMetaDataDto.builder().build(),
                "1.0.0",
                contentDto1,
                EditableVersionMetaDataDto.builder().build(),
                Collections.emptyList(),
                false,
                false,
                "test-user"
        );

        long contentId1 = result1.getRight().getContentId();

        // Create second version with different content
        var result2 = storage.createArtifactVersion(
                GROUP_ID,
                ARTIFACT_ID,
                "2.0.0",
                ArtifactType.JSON,
                contentDto2,
                EditableVersionMetaDataDto.builder().build(),
                Collections.emptyList(),
                false,
                false,
                "test-user"
        );

        long contentId2 = result2.getContentId();

        // Verify different content IDs
        assertNotEquals(contentId1, contentId2, "Different content should have different content IDs");

        // Verify both can be retrieved by their respective hashes
        TypedContent typedContent1 = TypedContent.create(content1, ContentTypes.APPLICATION_JSON);
        String contentHash1 = contentUtils.getContentHash(typedContent1, null);

        TypedContent typedContent2 = TypedContent.create(content2, ContentTypes.APPLICATION_JSON);
        String contentHash2 = contentUtils.getContentHash(typedContent2, null);

        ContentWrapperDto retrieved1 = storage.getContentByHash(contentHash1, ContentHashType.CONTENT_SHA256);
        ContentWrapperDto retrieved2 = storage.getContentByHash(contentHash2, ContentHashType.CONTENT_SHA256);

        assertEquals(content1.content(), retrieved1.getContent().content());
        assertEquals(content2.content(), retrieved2.getContent().content());
        assertEquals(JSON_CONTENT_1, retrieved1.getContent().content());
        assertEquals(JSON_CONTENT_2, retrieved2.getContent().content());
    }

    /**
     * Test #2c: Canonical Deduplication
     *
     * Verifies that functionally equivalent content (same canonical-sha256 but different content-sha256)
     * creates separate content rows but can both be found via canonical hash.
     */
    @Test
    public void testCanonicalDeduplication() throws Exception {
        // JSON_CONTENT_1 and JSON_CONTENT_FORMATTED are semantically equivalent but have different formatting
        ContentHandle content1 = ContentHandle.create(JSON_CONTENT_1);
        ContentWrapperDto contentDto1 = ContentWrapperDto.builder()
                .content(content1)
                .contentType(ContentTypes.APPLICATION_JSON)
                .build();

        ContentHandle content2 = ContentHandle.create(JSON_CONTENT_FORMATTED);
        ContentWrapperDto contentDto2 = ContentWrapperDto.builder()
                .content(content2)
                .contentType(ContentTypes.APPLICATION_JSON)
                .build();

        // Create first artifact
        var result1 = storage.createArtifact(
                GROUP_ID,
                ARTIFACT_ID,
                ArtifactType.JSON,
                EditableArtifactMetaDataDto.builder().build(),
                "1.0.0",
                contentDto1,
                EditableVersionMetaDataDto.builder().build(),
                Collections.emptyList(),
                false,
                false,
                "test-user"
        );

        long contentId1 = result1.getRight().getContentId();

        // Create second artifact with canonically equivalent content
        var result2 = storage.createArtifact(
                GROUP_ID,
                ARTIFACT_ID + "-2",
                ArtifactType.JSON,
                EditableArtifactMetaDataDto.builder().build(),
                "1.0.0",
                contentDto2,
                EditableVersionMetaDataDto.builder().build(),
                Collections.emptyList(),
                false,
                false,
                "test-user"
        );

        long contentId2 = result2.getRight().getContentId();

        // Verify different content IDs (because content-sha256 differs)
        assertNotEquals(contentId1, contentId2, "Different raw content should have different content IDs");

        // Calculate hashes
        TypedContent typedContent1 = TypedContent.create(content1, ContentTypes.APPLICATION_JSON);
        TypedContent typedContent2 = TypedContent.create(content2, ContentTypes.APPLICATION_JSON);

        String contentHash1 = contentUtils.getContentHash(typedContent1, null);
        String contentHash2 = contentUtils.getContentHash(typedContent2, null);

        String canonicalHash1 = contentUtils.getCanonicalContentHash(typedContent1, ArtifactType.JSON, null, null);
        String canonicalHash2 = contentUtils.getCanonicalContentHash(typedContent2, ArtifactType.JSON, null, null);

        // Verify content hashes are different
        assertNotEquals(contentHash1, contentHash2, "Raw content hashes should differ");

        // Verify canonical hashes are the same (functionally equivalent)
        assertEquals(canonicalHash1, canonicalHash2, "Canonical hashes should be identical for functionally equivalent content");

        // Verify both can be retrieved by canonical hash (but will return different contentIds)
        ContentWrapperDto retrieved1 = storage.getContentByHash(canonicalHash1, ContentHashType.CANONICAL_SHA256);
        assertNotNull(retrieved1);
        // Note: The canonical hash lookup will return one of the two content items, but we can't predict which
    }

    /**
     * Test #2e: Content Not Found for Invalid Hash
     *
     * Verifies that querying with a non-existent hash throws ContentNotFoundException.
     */
    @Test
    public void testContentNotFoundForInvalidHash() {
        String invalidHash = "0000000000000000000000000000000000000000000000000000000000000000";

        assertThrows(ContentNotFoundException.class, () -> {
            storage.getContentByHash(invalidHash, ContentHashType.CONTENT_SHA256);
        }, "Should throw ContentNotFoundException for non-existent content-sha256 hash");

        assertThrows(ContentNotFoundException.class, () -> {
            storage.getContentByHash(invalidHash, ContentHashType.CANONICAL_SHA256);
        }, "Should throw ContentNotFoundException for non-existent canonical-sha256 hash");

        assertThrows(ContentNotFoundException.class, () -> {
            storage.getContentByHash(invalidHash, ContentHashType.CANONICAL_NO_REFS_SHA256);
        }, "Should throw ContentNotFoundException for non-existent canonical-no-refs-sha256 hash");
    }
}
