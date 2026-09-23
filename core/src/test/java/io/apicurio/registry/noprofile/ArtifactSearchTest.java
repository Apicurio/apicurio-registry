package io.apicurio.registry.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.ArtifactSortBy;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@QuarkusTest
public class ArtifactSearchTest extends AbstractResourceTestBase {

    private static final String OPENAPI_CONTENT_TEMPLATE = "{\r\n" + "    \"openapi\": \"3.0.2\",\r\n"
            + "    \"info\": {\r\n" + "        \"title\": \"TITLE\",\r\n"
            + "        \"version\": \"1.0.0\",\r\n" + "        \"description\": \"DESCRIPTION\"\r\n"
            + "    }\r\n" + "}";

    @Test
    void testCaseInsensitiveSearch() throws Exception {
        String groupId = "ArtifactSearchTest_testCaseInsensitiveSearch";
        // warm-up
        clientV3.groups().byGroupId(groupId).artifacts().get();

        String artifactId = UUID.randomUUID().toString();
        String title = "testCaseInsensitiveSearch";
        String description = "The quick brown FOX jumped over the Lazy dog.";
        String content = OPENAPI_CONTENT_TEMPLATE.replace("TITLE", title).replace("DESCRIPTION", description);

        createArtifact(groupId, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON,
                (createArtifact) -> {
                    createArtifact.setName(title);
                    createArtifact.setDescription(description);
                    createArtifact.getFirstVersion().setName(title);
                    createArtifact.getFirstVersion().setDescription(description);
                });

        // Search against the name, with the exact name of the artifact
        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = title;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

        // Update the meta-data for the artifact
        EditableArtifactMetaData metaData = new EditableArtifactMetaData();
        metaData.setName(title);
        metaData.setDescription(description);
        Labels labels = new Labels();
        labels.setAdditionalData(
                Collections.singletonMap("testCaseInsensitiveSearchKey", "testCaseInsensitiveSearchValue"));
        metaData.setLabels(labels);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(metaData);

        // Now try various cases when searching by labels
        ArtifactSearchResults ires = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "testCaseInsensitiveSearchKey" };
        });
        Assertions.assertNotNull(ires);
        Assertions.assertEquals(1, ires.getCount());
        ires = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "testCaseInsensitiveSearchKey".toLowerCase() };
        });
        Assertions.assertNotNull(ires);
        Assertions.assertEquals(1, ires.getCount());
        ires = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "testCaseInsensitiveSearchKey".toUpperCase() };
        });
        Assertions.assertNotNull(ires);
        Assertions.assertEquals(1, ires.getCount());
        ires = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "TESTCaseInsensitiveSEARCHKey" };
        });
        Assertions.assertNotNull(ires);
        Assertions.assertEquals(1, ires.getCount());

        // Now try various cases when searching by properties and values
        ArtifactSearchResults propertiesSearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] {
                    "testCaseInsensitiveSearchKey:testCaseInsensitiveSearchValue" };
        });
        Assertions.assertNotNull(propertiesSearch);
        Assertions.assertEquals(1, propertiesSearch.getCount());
        propertiesSearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] {
                    "testCaseInsensitiveSearchKey:testCaseInsensitiveSearchValue".toLowerCase() };
        });
        Assertions.assertNotNull(propertiesSearch);
        Assertions.assertEquals(1, propertiesSearch.getCount());
        propertiesSearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] {
                    "testCaseInsensitiveSearchKey:testCaseInsensitiveSearchValue".toUpperCase() };
        });
        Assertions.assertNotNull(propertiesSearch);
        Assertions.assertEquals(1, propertiesSearch.getCount());
        propertiesSearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] {
                    "TESTCaseInsensitiveSEARCHKey:TESTCaseInsensitiveSearchVALUE".toUpperCase() };
        });
        Assertions.assertNotNull(propertiesSearch);
        Assertions.assertEquals(1, propertiesSearch.getCount());

        // Now try various cases when searching by properties
        ArtifactSearchResults propertiesKeySearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "testCaseInsensitiveSearchKey" };
        });
        Assertions.assertNotNull(propertiesKeySearch);
        Assertions.assertEquals(1, propertiesKeySearch.getCount());
        propertiesKeySearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "testCaseInsensitiveSearchKey".toLowerCase() };
        });
        Assertions.assertNotNull(propertiesKeySearch);
        Assertions.assertEquals(1, propertiesKeySearch.getCount());
        propertiesKeySearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "testCaseInsensitiveSearchKey".toUpperCase() };
        });
        Assertions.assertNotNull(propertiesKeySearch);
        Assertions.assertEquals(1, propertiesKeySearch.getCount());
        propertiesKeySearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "TESTCaseInsensitiveSEARCHKey" };
        });
        Assertions.assertNotNull(propertiesKeySearch);
        Assertions.assertEquals(1, propertiesSearch.getCount());
    }

    @Test
    void testFilterByArtifactType() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createArtifact(groupId, "avro-artifact", ArtifactType.AVRO, "{}", ContentTypes.APPLICATION_JSON);
        createArtifact(groupId, "json-artifact", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);

        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getCount());

        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.artifactType = ArtifactType.AVRO;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.artifactType = ArtifactType.JSON;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
    }

    /**
     * Tests that artifact name search supports exact matching and wildcard patterns.
     * This test verifies the fix for issue #6298 where substring matching was incorrectly
     * performed for all name searches. Now:
     * - "name" performs exact match
     * - "name*" matches names starting with "name"
     * - "*name" matches names ending with "name"
     * - "*name*" matches names containing "name"
     */
    @Test
    void testArtifactNameSearchWildcards() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create test artifacts with various names
        createArtifact(groupId, "artifact-1", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON,
                (createArtifact) -> {
                    createArtifact.setName("name");
                });
        createArtifact(groupId, "artifact-2", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON,
                (createArtifact) -> {
                    createArtifact.setName("name-test");
                });
        createArtifact(groupId, "artifact-3", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON,
                (createArtifact) -> {
                    createArtifact.setName("test-name");
                });
        createArtifact(groupId, "artifact-4", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON,
                (createArtifact) -> {
                    createArtifact.setName("some-name-here");
                });
        createArtifact(groupId, "artifact-5", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON,
                (createArtifact) -> {
                    createArtifact.setName("other");
                });

        // Test 1: Exact match - should only return artifact with name exactly "name"
        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = "name";
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount(), "Exact match for 'name' should return 1 result");
        Assertions.assertEquals("name", results.getArtifacts().get(0).getName());

        // Test 2: End wildcard - should return artifacts starting with "name"
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = "name*";
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getCount(),
                "Wildcard 'name*' should return 2 results (name, name-test)");

        // Test 3: Start wildcard - should return artifacts ending with "name"
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = "*name";
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getCount(),
                "Wildcard '*name' should return 2 results (name, test-name)");

        // Test 4: Both wildcards - should return all artifacts containing "name"
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = "*name*";
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(4, results.getCount(),
                "Wildcard '*name*' should return 4 results (name, name-test, test-name, some-name-here)");

        // Test 5: Exact match with no results
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = "nonexistent";
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(0, results.getCount(),
                "Exact match for 'nonexistent' should return 0 results");

        // Test 6: Wildcard with specific prefix
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = "test*";
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount(),
                "Wildcard 'test*' should return 1 result (test-name)");

        // Test 7: Wildcard with specific suffix
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = "*test";
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount(),
                "Wildcard '*test' should return 1 result (name-test)");
    }

    @Test
    void testArtifactIdSearchWildcards() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createArtifact(groupId, "user-profile-cli", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);
        createArtifact(groupId, "user-profile-web", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);
        createArtifact(groupId, "admin-profile-cli", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);
        createArtifact(groupId, "order-service", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);

        // Exact match
        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.artifactId = "user-profile-cli";
        });
        Assertions.assertEquals(1, results.getCount(), "Exact match should return 1 result");

        // Prefix wildcard
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.artifactId = "user*";
        });
        Assertions.assertEquals(2, results.getCount(), "Wildcard 'user*' should return 2 results");

        // Suffix wildcard
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.artifactId = "*cli";
        });
        Assertions.assertEquals(2, results.getCount(), "Wildcard '*cli' should return 2 results");

        // Substring wildcard
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.artifactId = "*profile*";
        });
        Assertions.assertEquals(3, results.getCount(), "Wildcard '*profile*' should return 3 results");

        // No match
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.artifactId = "nonexistent*";
        });
        Assertions.assertEquals(0, results.getCount(), "Wildcard 'nonexistent*' should return 0 results");
    }

    @Test
    void testGroupIdSearchWildcards() throws Exception {
        String prefix = "WildcardGroupTest_" + UUID.randomUUID().toString().substring(0, 8);

        createArtifact(prefix + "_alpha", "artifact-1", ArtifactType.JSON, "{}",
                ContentTypes.APPLICATION_JSON);
        createArtifact(prefix + "_beta", "artifact-2", ArtifactType.JSON, "{}",
                ContentTypes.APPLICATION_JSON);
        createArtifact(prefix + "_gamma", "artifact-3", ArtifactType.JSON, "{}",
                ContentTypes.APPLICATION_JSON);

        // Prefix wildcard on groupId
        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = prefix + "*";
        });
        Assertions.assertEquals(3, results.getCount(),
                "Wildcard groupId prefix should return all 3 artifacts");

        // Suffix wildcard
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = "*_alpha";
        });
        Assertions.assertTrue(results.getCount() >= 1,
                "Wildcard '*_alpha' should return at least 1 result");

        // Exact match
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = prefix + "_beta";
        });
        Assertions.assertEquals(1, results.getCount(), "Exact groupId match should return 1 result");
    }

    @Test
    void testLabelSearchWildcards() throws Exception {
        String groupId = TestUtils.generateGroupId();

        String artifactId1 = "label-wildcard-1";
        String artifactId2 = "label-wildcard-2";
        String artifactId3 = "label-wildcard-3";

        createArtifact(groupId, artifactId1, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);
        createArtifact(groupId, artifactId2, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);
        createArtifact(groupId, artifactId3, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);

        // Set labels on each artifact
        EditableArtifactMetaData meta1 = new EditableArtifactMetaData();
        Labels labels1 = new Labels();
        labels1.setAdditionalData(Map.of("contract.status", "active", "team", "platform"));
        meta1.setLabels(labels1);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId1).put(meta1);

        EditableArtifactMetaData meta2 = new EditableArtifactMetaData();
        Labels labels2 = new Labels();
        labels2.setAdditionalData(Map.of("contract.owner", "team-a", "team", "backend"));
        meta2.setLabels(labels2);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId2).put(meta2);

        EditableArtifactMetaData meta3 = new EditableArtifactMetaData();
        Labels labels3 = new Labels();
        labels3.setAdditionalData(Map.of("version", "v2", "team", "frontend"));
        meta3.setLabels(labels3);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId3).put(meta3);

        // Wildcard on label key: contract.* should match artifacts 1 and 2
        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.labels = new String[] { "contract.*" };
        });
        Assertions.assertEquals(2, results.getCount(),
                "Wildcard label key 'contract.*' should match 2 artifacts");

        // Wildcard on label value: team:*end* should match backend and frontend
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.labels = new String[] { "team:*end*" };
        });
        Assertions.assertEquals(2, results.getCount(),
                "Wildcard label value 'team:*end*' should match 2 artifacts");

        // Exact label key without wildcard
        results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.labels = new String[] { "team" };
        });
        Assertions.assertEquals(3, results.getCount(),
                "Exact label key 'team' should match all 3 artifacts");
    }
}