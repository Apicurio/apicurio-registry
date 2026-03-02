package io.apicurio.registry.storage.impl.search;

import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.openapi.content.extract.OpenApiStructuredContentExtractor;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.types.VersionState;
import org.apache.lucene.index.Term;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LuceneSearchService. Uses real Lucene index (no mocks) to verify search query
 * building, execution, sorting, pagination, and result mapping.
 */
public class LuceneSearchServiceTest {

    @TempDir
    Path tempDir;

    private LuceneIndexWriter indexWriter;
    private LuceneIndexSearcher indexSearcher;
    private LuceneDocumentBuilder documentBuilder;
    private LuceneSearchService searchService;
    private String testIndexPath;

    @BeforeEach
    void setUp() throws Exception {
        testIndexPath = tempDir.resolve("test-index").toString();

        LuceneSearchConfig config = new LuceneSearchConfig() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public String getIndexPath() {
                return testIndexPath;
            }

            @Override
            public int getRamBufferSizeMB() {
                return 16;
            }
        };

        // Initialize writer
        indexWriter = new LuceneIndexWriter();
        injectField(indexWriter, LuceneIndexWriter.class, "config", config);
        indexWriter.initialize();

        // Initialize searcher
        indexSearcher = new LuceneIndexSearcher();
        injectField(indexSearcher, LuceneIndexSearcher.class, "config", config);
        injectField(indexSearcher, LuceneIndexSearcher.class, "indexWriter", indexWriter);
        indexSearcher.initialize();

        // Initialize document builder
        documentBuilder = new LuceneDocumentBuilder();

        // Initialize search service
        searchService = new LuceneSearchService();
        injectField(searchService, LuceneSearchService.class, "indexSearcher", indexSearcher);
        injectField(searchService, LuceneSearchService.class, "documentBuilder", documentBuilder);

        // Index test documents
        indexTestDocuments();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (indexSearcher != null) {
            indexSearcher.cleanup();
        }
        if (indexWriter != null) {
            indexWriter.cleanup();
        }
    }

    // ======== Filter Tests ========

    @Test
    void testSearchVersions_NoFilters() throws IOException {
        VersionSearchResultsDto results = searchService.searchVersions(
                Set.of(), OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertEquals(5, results.getCount());
        assertEquals(5, results.getVersions().size());
    }

    @Test
    void testSearchVersions_FilterByGroupId() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofGroupId("group-a"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertEquals(3, results.getCount());
        for (SearchedVersionDto v : results.getVersions()) {
            assertEquals("group-a", v.getGroupId());
        }
    }

    @Test
    void testSearchVersions_FilterByArtifactId() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofArtifactId("pet-api"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertEquals(2, results.getCount());
        for (SearchedVersionDto v : results.getVersions()) {
            assertEquals("pet-api", v.getArtifactId());
        }
    }

    @Test
    void testSearchVersions_FilterByName() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofName("Pet Store"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertTrue(results.getCount() > 0, "Should find versions matching 'Pet Store' name");
    }

    @Test
    void testSearchVersions_FilterByDescription() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofDescription("inventory"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertTrue(results.getCount() > 0, "Should find versions with 'inventory' in description");
    }

    @Test
    void testSearchVersions_FilterByContent() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofContent("Pet Store"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertTrue(results.getCount() > 0, "Should find versions with 'Pet Store' in content");
        // Pet Store API v1 and v2 both have "Pet Store" in their content
        assertEquals(2, results.getCount(), "Should find exactly 2 versions with 'Pet Store' in content");
    }

    @Test
    void testSearchVersions_FilterByContent_NoMatch() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofContent("nonexistent-content-string"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertEquals(0, results.getCount(), "Should not find any versions with nonexistent content");
    }

    @Test
    void testSearchVersions_FilterByContent_CombinedWithOtherFilter() throws IOException {
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofContent("Pet Store"));
        filters.add(SearchFilter.ofGroupId("group-a"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertEquals(2, results.getCount(),
                "Should find 2 Pet Store versions in group-a");
    }

    @Test
    void testSearchVersions_FilterByState() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofState(VersionState.DEPRECATED));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertEquals(1, results.getCount());
        assertEquals(VersionState.DEPRECATED, results.getVersions().get(0).getState());
    }

    @Test
    void testSearchVersions_FilterByArtifactType() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofArtifactType("OPENAPI"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertEquals(3, results.getCount());
        for (SearchedVersionDto v : results.getVersions()) {
            assertEquals("OPENAPI", v.getArtifactType());
        }
    }

    @Test
    void testSearchVersions_FilterByLabels_KeyOnly() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofLabel("env"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertTrue(results.getCount() > 0, "Should find versions with 'env' label key");
    }

    @Test
    void testSearchVersions_FilterByLabels_KeyAndValue() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofLabel("env", "production"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertTrue(results.getCount() > 0, "Should find versions with env=production label");
    }

    @Test
    void testSearchVersions_FilterByGlobalId() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofGlobalId(1001L));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertEquals(1, results.getCount());
        assertEquals(1001L, results.getVersions().get(0).getGlobalId());
    }

    @Test
    void testSearchVersions_FilterByContentId() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofContentId(501L));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertEquals(1, results.getCount());
        assertEquals(501L, results.getVersions().get(0).getContentId());
    }

    @Test
    void testSearchVersions_MultipleFilters() throws IOException {
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroupId("group-a"));
        filters.add(SearchFilter.ofArtifactType("OPENAPI"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertEquals(2, results.getCount());
        for (SearchedVersionDto v : results.getVersions()) {
            assertEquals("group-a", v.getGroupId());
            assertEquals("OPENAPI", v.getArtifactType());
        }
    }

    @Test
    void testSearchVersions_NegatedFilter() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofGroupId("group-a").negated());

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertEquals(2, results.getCount());
        for (SearchedVersionDto v : results.getVersions()) {
            assertNotEquals("group-a", v.getGroupId());
        }
    }

    // ======== Pagination Tests ========

    @Test
    void testSearchVersions_Pagination() throws IOException {
        // Get all results
        VersionSearchResultsDto all = searchService.searchVersions(
                Set.of(), OrderBy.globalId, OrderDirection.asc, 0, 100);
        assertEquals(5, all.getCount());

        // Page 1: offset=0, limit=2
        VersionSearchResultsDto page1 = searchService.searchVersions(
                Set.of(), OrderBy.globalId, OrderDirection.asc, 0, 2);
        assertEquals(5, page1.getCount()); // Total count remains the same
        assertEquals(2, page1.getVersions().size());

        // Page 2: offset=2, limit=2
        VersionSearchResultsDto page2 = searchService.searchVersions(
                Set.of(), OrderBy.globalId, OrderDirection.asc, 2, 2);
        assertEquals(5, page2.getCount());
        assertEquals(2, page2.getVersions().size());

        // Page 3: offset=4, limit=2
        VersionSearchResultsDto page3 = searchService.searchVersions(
                Set.of(), OrderBy.globalId, OrderDirection.asc, 4, 2);
        assertEquals(5, page3.getCount());
        assertEquals(1, page3.getVersions().size()); // Only 1 remaining

        // Verify no overlap between pages
        assertNotEquals(page1.getVersions().get(0).getGlobalId(),
                page2.getVersions().get(0).getGlobalId());
    }

    // ======== Sort Tests ========

    @Test
    void testSearchVersions_SortByNameAsc() throws IOException {
        VersionSearchResultsDto results = searchService.searchVersions(
                Set.of(), OrderBy.name, OrderDirection.asc, 0, 100);

        assertEquals(5, results.getCount());
        for (int i = 1; i < results.getVersions().size(); i++) {
            String prev = getEffectiveName(results.getVersions().get(i - 1));
            String curr = getEffectiveName(results.getVersions().get(i));
            assertTrue(prev.compareToIgnoreCase(curr) <= 0,
                    "Names should be in ascending order: " + prev + " <= " + curr);
        }
    }

    @Test
    void testSearchVersions_SortByNameDesc() throws IOException {
        VersionSearchResultsDto results = searchService.searchVersions(
                Set.of(), OrderBy.name, OrderDirection.desc, 0, 100);

        assertEquals(5, results.getCount());
        for (int i = 1; i < results.getVersions().size(); i++) {
            String prev = getEffectiveName(results.getVersions().get(i - 1));
            String curr = getEffectiveName(results.getVersions().get(i));
            assertTrue(prev.compareToIgnoreCase(curr) >= 0,
                    "Names should be in descending order: " + prev + " >= " + curr);
        }
    }

    @Test
    void testSearchVersions_SortByCreatedOn() throws IOException {
        VersionSearchResultsDto results = searchService.searchVersions(
                Set.of(), OrderBy.createdOn, OrderDirection.asc, 0, 100);

        assertEquals(5, results.getCount());
        for (int i = 1; i < results.getVersions().size(); i++) {
            Date prev = results.getVersions().get(i - 1).getCreatedOn();
            Date curr = results.getVersions().get(i).getCreatedOn();
            if (prev != null && curr != null) {
                assertTrue(prev.compareTo(curr) <= 0,
                        "CreatedOn should be in ascending order");
            }
        }
    }

    @Test
    void testSearchVersions_SortByGlobalId() throws IOException {
        VersionSearchResultsDto results = searchService.searchVersions(
                Set.of(), OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertEquals(5, results.getCount());
        for (int i = 1; i < results.getVersions().size(); i++) {
            long prev = results.getVersions().get(i - 1).getGlobalId();
            long curr = results.getVersions().get(i).getGlobalId();
            assertTrue(prev <= curr,
                    "GlobalIds should be in ascending order: " + prev + " <= " + curr);
        }
    }

    // ======== canHandleFilters Tests ========

    @Test
    void testCanHandleFilters_Supported() {
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroupId("g"));
        filters.add(SearchFilter.ofArtifactId("a"));
        filters.add(SearchFilter.ofName("n"));
        filters.add(SearchFilter.ofState(VersionState.ENABLED));

        assertTrue(searchService.canHandleFilters(filters));
    }

    @Test
    void testCanHandleFilters_ContentSupported() {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofContent("search text"));

        assertTrue(searchService.canHandleFilters(filters),
                "Content filter should be handled by Lucene");
    }

    @Test
    void testCanHandleFilters_Empty() {
        assertTrue(searchService.canHandleFilters(Set.of()));
        assertTrue(searchService.canHandleFilters(null));
    }

    @Test
    void testCanHandleFilters_UnsupportedContentHash() {
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroupId("g"));
        filters.add(SearchFilter.ofContentHash("abc123"));

        assertFalse(searchService.canHandleFilters(filters));
    }

    @Test
    void testCanHandleFilters_UnsupportedCanonicalHash() {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofCanonicalHash("abc123"));

        assertFalse(searchService.canHandleFilters(filters));
    }

    // ======== requiresLucene Tests ========

    @Test
    void testRequiresLucene_ContentFilter() {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofContent("search text"));

        assertTrue(searchService.requiresLucene(filters),
                "Content filter should require Lucene");
    }

    @Test
    void testRequiresLucene_NonLuceneFilters() {
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroupId("g"));
        filters.add(SearchFilter.ofName("n"));

        assertFalse(searchService.requiresLucene(filters),
                "Standard filters should not require Lucene");
    }

    @Test
    void testRequiresLucene_EmptyFilters() {
        assertFalse(searchService.requiresLucene(Set.of()),
                "Empty filters should not require Lucene");
        assertFalse(searchService.requiresLucene(null),
                "Null filters should not require Lucene");
    }

    @Test
    void testRequiresLucene_MixedFilters() {
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroupId("g"));
        filters.add(SearchFilter.ofContent("search text"));

        assertTrue(searchService.requiresLucene(filters),
                "Mixed filters containing content should require Lucene");
    }

    @Test
    void testRequiresLucene_StructureFilter() {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofStructure("schema:Pet"));

        assertTrue(searchService.requiresLucene(filters),
                "Structure filter should require Lucene");
    }

    @Test
    void testCanHandleFilters_StructureSupported() {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofStructure("schema:Pet"));

        assertTrue(searchService.canHandleFilters(filters),
                "Structure filter should be handled by Lucene");
    }

    // ======== Structure Filter Search Tests ========

    @Test
    void testSearchVersions_FilterByStructure_FullFormat() throws IOException {
        // Index a document with structured elements
        indexOpenApiWithStructure();

        // Search by full format: type:kind:name
        Set<SearchFilter> filters = Set.of(SearchFilter.ofStructure("openapi:schema:pet"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertTrue(results.getCount() > 0,
                "Should find version with 'openapi:schema:pet' structure");
    }

    @Test
    void testSearchVersions_FilterByStructure_KindAndName() throws IOException {
        // Index a document with structured elements
        indexOpenApiWithStructure();

        // Search by kind:name format
        Set<SearchFilter> filters = Set.of(SearchFilter.ofStructure("schema:Pet"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertTrue(results.getCount() > 0,
                "Should find version with 'schema:Pet' structure");
    }

    @Test
    void testSearchVersions_FilterByStructure_NameOnly() throws IOException {
        // Index a document with structured elements
        indexOpenApiWithStructure();

        // Search by name only
        Set<SearchFilter> filters = Set.of(SearchFilter.ofStructure("Pet"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertTrue(results.getCount() > 0,
                "Should find version with 'Pet' in structured elements");
    }

    @Test
    void testSearchVersions_FilterByStructure_NoMatch() throws IOException {
        // Index a document with structured elements
        indexOpenApiWithStructure();

        // Search for non-existent structure
        Set<SearchFilter> filters = Set.of(
                SearchFilter.ofStructure("openapi:schema:nonexistentschema"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertEquals(0, results.getCount(),
                "Should not find any versions with nonexistent structure");
    }

    @Test
    void testSearchVersions_FilterByStructure_CombinedWithGroupId() throws IOException {
        // Index a document with structured elements
        indexOpenApiWithStructure();

        // Search by structure + group filter
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofStructure("schema:Pet"));
        filters.add(SearchFilter.ofGroupId("struct-group"));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertTrue(results.getCount() > 0,
                "Should find version with 'schema:Pet' in struct-group");
    }

    // ======== Result Mapping Tests ========

    @Test
    void testResultMapping_AllFieldsPopulated() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofGlobalId(1001L));

        VersionSearchResultsDto results = searchService.searchVersions(
                filters, OrderBy.globalId, OrderDirection.asc, 0, 100);

        assertEquals(1, results.getCount());
        SearchedVersionDto version = results.getVersions().get(0);

        assertEquals(1001L, version.getGlobalId());
        assertEquals(501L, version.getContentId());
        assertEquals("group-a", version.getGroupId());
        assertEquals("pet-api", version.getArtifactId());
        assertEquals("1.0.0", version.getVersion());
        assertEquals("OPENAPI", version.getArtifactType());
        assertEquals(VersionState.ENABLED, version.getState());
        assertEquals("Pet Store API", version.getName());
        assertEquals("An API for managing pet inventory", version.getDescription());
        assertEquals("user1", version.getOwner());
        assertEquals("user1", version.getModifiedBy());
        assertNotNull(version.getCreatedOn());
        assertNotNull(version.getModifiedOn());
        assertEquals(1, version.getVersionOrder());

        // Labels
        assertNotNull(version.getLabels());
        assertEquals("production", version.getLabels().get("env"));
        assertEquals("v3", version.getLabels().get("api-version"));
    }

    // ======== Helper Methods ========

    /**
     * Indexes 5 test documents representing artifact versions across 2 groups.
     */
    private void indexTestDocuments() throws IOException {
        long baseTime = System.currentTimeMillis() - 100000;

        // Version 1: group-a/pet-api v1.0.0 (OPENAPI, ENABLED)
        indexVersion(1001L, 501L, "group-a", "pet-api", "1.0.0", 1, "OPENAPI",
                VersionState.ENABLED, "Pet Store API", "An API for managing pet inventory",
                "user1", "user1", baseTime, baseTime + 1000,
                Map.of("env", "production", "api-version", "v3"),
                "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Pet Store\"}}");

        // Version 2: group-a/pet-api v2.0.0 (OPENAPI, ENABLED)
        indexVersion(1002L, 502L, "group-a", "pet-api", "2.0.0", 2, "OPENAPI",
                VersionState.ENABLED, "Pet Store API v2", "Updated pet API with new features",
                "user2", "user2", baseTime + 2000, baseTime + 3000,
                Map.of("env", "staging"),
                "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Pet Store v2\"}}");

        // Version 3: group-a/order-schema v1.0.0 (AVRO, DEPRECATED)
        indexVersion(1003L, 503L, "group-a", "order-schema", "1.0.0", 1, "AVRO",
                VersionState.DEPRECATED, "Order Schema", "Avro schema for order events",
                "user1", "user1", baseTime + 4000, baseTime + 5000,
                Map.of("env", "production", "team", "orders"),
                "{\"type\":\"record\",\"name\":\"Order\"}");

        // Version 4: group-b/user-api v1.0.0 (OPENAPI, ENABLED)
        indexVersion(1004L, 504L, "group-b", "user-api", "1.0.0", 1, "OPENAPI",
                VersionState.ENABLED, "User Management API", "API for user management",
                "user3", "user3", baseTime + 6000, baseTime + 7000,
                Map.of("env", "production"),
                "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"User API\"}}");

        // Version 5: group-b/event-schema v1.0.0 (JSON, ENABLED)
        indexVersion(1005L, 505L, "group-b", "event-schema", "1.0.0", 1, "JSON",
                VersionState.ENABLED, null, "JSON schema for system events",
                "user2", "user2", baseTime + 8000, baseTime + 9000,
                null,
                "{\"type\":\"object\",\"title\":\"Event\"}");

        indexWriter.commit();
        indexSearcher.refresh();
    }

    private void indexVersion(long globalId, long contentId, String groupId, String artifactId,
            String version, int versionOrder, String artifactType, VersionState state,
            String name, String description, String owner, String modifiedBy,
            long createdOn, long modifiedOn, Map<String, String> labels, String content)
            throws IOException {

        ArtifactVersionMetaDataDto metadata = ArtifactVersionMetaDataDto.builder()
                .globalId(globalId)
                .contentId(contentId)
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .versionOrder(versionOrder)
                .artifactType(artifactType)
                .state(state)
                .name(name)
                .description(description)
                .owner(owner)
                .modifiedBy(modifiedBy)
                .createdOn(createdOn)
                .modifiedOn(modifiedOn)
                .labels(labels)
                .build();

        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        org.apache.lucene.document.Document doc = documentBuilder.buildVersionDocument(
                metadata, contentBytes);
        indexWriter.updateDocument(
                new Term("globalId", String.valueOf(globalId)), doc);
    }

    /**
     * Indexes an OpenAPI document with structured content extraction for structure filter tests.
     */
    private void indexOpenApiWithStructure() throws IOException {
        String openApiContent = """
                {
                  "openapi": "3.0.2",
                  "info": { "title": "Pet Store", "version": "1.0" },
                  "paths": {
                    "/pets": {
                      "get": { "operationId": "listPets" }
                    }
                  },
                  "components": {
                    "schemas": {
                      "Pet": { "type": "object" },
                      "Order": { "type": "object" }
                    }
                  }
                }
                """;

        ArtifactVersionMetaDataDto metadata = ArtifactVersionMetaDataDto.builder()
                .globalId(9001L)
                .contentId(901L)
                .groupId("struct-group")
                .artifactId("pet-store-api")
                .version("1.0.0")
                .versionOrder(1)
                .artifactType("OPENAPI")
                .state(VersionState.ENABLED)
                .name("Pet Store")
                .description("Pet Store API with structured content")
                .owner("user1")
                .createdOn(System.currentTimeMillis())
                .build();

        byte[] contentBytes = openApiContent.getBytes(StandardCharsets.UTF_8);
        StructuredContentExtractor extractor = new OpenApiStructuredContentExtractor();

        org.apache.lucene.document.Document doc = documentBuilder.buildVersionDocument(
                metadata, contentBytes, extractor);
        indexWriter.updateDocument(
                new Term("globalId", String.valueOf(9001L)), doc);

        indexWriter.commit();
        indexSearcher.refresh();
    }

    /**
     * Gets the effective name for sorting comparison (name if present, version as fallback).
     */
    private String getEffectiveName(SearchedVersionDto v) {
        return v.getName() != null ? v.getName() : v.getVersion();
    }

    /**
     * Sets a private field via reflection for dependency injection in tests.
     */
    private void injectField(Object target, Class<?> clazz, String fieldName, Object value) {
        try {
            var field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject field: " + fieldName, e);
        }
    }
}
