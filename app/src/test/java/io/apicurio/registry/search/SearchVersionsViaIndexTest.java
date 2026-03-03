package io.apicurio.registry.search;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rest.client.models.VersionSortBy;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Integration tests for searching versions via the Elasticsearch index. Uses a QuarkusTestProfile
 * that enables Elasticsearch search indexing, so that all version searches are routed through
 * the Elasticsearch index instead of SQL. Tests cover filtering, pagination, sorting, and result
 * mapping. Quarkus Dev Services auto-starts an Elasticsearch container for the tests.
 */
@QuarkusTest
@TestProfile(ElasticsearchSearchTestProfile.class)
public class SearchVersionsViaIndexTest extends AbstractResourceTestBase {

    @Test
    public void testSearchVersionsByGroupId() throws Exception {
        String group1 = TestUtils.generateGroupId();
        String group2 = TestUtils.generateGroupId();

        // Create 5 artifacts in group 1
        for (int idx = 0; idx < 5; idx++) {
            String artifactId = "testSearchByGroupId_G1_" + idx;
            createArtifact(group1, artifactId, ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}",
                    ContentTypes.APPLICATION_JSON);
        }
        // Create 3 artifacts in group 2
        for (int idx = 0; idx < 3; idx++) {
            String artifactId = "testSearchByGroupId_G2_" + idx;
            createArtifact(group2, artifactId, ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}",
                    ContentTypes.APPLICATION_JSON);
        }

        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group1;
        });
        Assertions.assertEquals(5, results.getCount());
        for (SearchedVersion version : results.getVersions()) {
            Assertions.assertEquals(group1, version.getGroupId());
        }

        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group2;
        });
        Assertions.assertEquals(3, results.getCount());
        for (SearchedVersion version : results.getVersions()) {
            Assertions.assertEquals(group2, version.getGroupId());
        }
    }

    @Test
    public void testSearchVersionsByArtifactId() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create 2 artifacts, one with 2 versions
        createArtifact(group, "testSearchByArtifactId_api-1", ArtifactType.OPENAPI,
                "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(group, "testSearchByArtifactId_api-1", "{\"openapi\":\"3.0.1\"}",
                ContentTypes.APPLICATION_JSON);
        createArtifact(group, "testSearchByArtifactId_api-2", ArtifactType.OPENAPI,
                "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);

        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.artifactId = "testSearchByArtifactId_api-1";
        });
        Assertions.assertEquals(2, results.getCount());
        for (SearchedVersion v : results.getVersions()) {
            Assertions.assertEquals("testSearchByArtifactId_api-1", v.getArtifactId());
        }

        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.artifactId = "testSearchByArtifactId_api-2";
        });
        Assertions.assertEquals(1, results.getCount());
    }

    @Test
    public void testSearchVersionsByName() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create artifacts and set names via metadata update
        CreateArtifactResponse car1 = createArtifact(group, "testSearchByName_api-1",
                ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        EditableVersionMetaData emd1 = new EditableVersionMetaData();
        emd1.setName("Pet Store API");
        clientV3.groups().byGroupId(group).artifacts().byArtifactId("testSearchByName_api-1")
                .versions().byVersionExpression(car1.getVersion().getVersion()).put(emd1);

        CreateArtifactResponse car2 = createArtifact(group, "testSearchByName_api-2",
                ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        EditableVersionMetaData emd2 = new EditableVersionMetaData();
        emd2.setName("User Management API");
        clientV3.groups().byGroupId(group).artifacts().byArtifactId("testSearchByName_api-2")
                .versions().byVersionExpression(car2.getVersion().getVersion()).put(emd2);

        CreateArtifactResponse car3 = createArtifact(group, "testSearchByName_api-3",
                ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        EditableVersionMetaData emd3 = new EditableVersionMetaData();
        emd3.setName("Pet Inventory Service");
        clientV3.groups().byGroupId(group).artifacts().byArtifactId("testSearchByName_api-3")
                .versions().byVersionExpression(car3.getVersion().getVersion()).put(emd3);

        // Search for "Pet" - should match 2 versions
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.name = "Pet";
        });
        Assertions.assertEquals(2, results.getCount());

        // Search for "User Management" - should match 1 version
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.name = "User Management";
        });
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("User Management API", results.getVersions().get(0).getName());
    }

    @Test
    public void testSearchVersionsByDescription() throws Exception {
        String group = TestUtils.generateGroupId();

        CreateArtifactResponse car1 = createArtifact(group, "testSearchByDesc_api-1",
                ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        EditableVersionMetaData emd1 = new EditableVersionMetaData();
        emd1.setDescription("API for managing pet inventory and orders");
        clientV3.groups().byGroupId(group).artifacts().byArtifactId("testSearchByDesc_api-1")
                .versions().byVersionExpression(car1.getVersion().getVersion()).put(emd1);

        CreateArtifactResponse car2 = createArtifact(group, "testSearchByDesc_api-2",
                ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        EditableVersionMetaData emd2 = new EditableVersionMetaData();
        emd2.setDescription("API for user authentication and authorization");
        clientV3.groups().byGroupId(group).artifacts().byArtifactId("testSearchByDesc_api-2")
                .versions().byVersionExpression(car2.getVersion().getVersion()).put(emd2);

        // Search for "inventory" in description
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.description = "inventory";
        });
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("testSearchByDesc_api-1",
                results.getVersions().get(0).getArtifactId());

        // Search for "authentication" in description
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.description = "authentication";
        });
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("testSearchByDesc_api-2",
                results.getVersions().get(0).getArtifactId());
    }

    @Test
    public void testSearchVersionsByArtifactType() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create OPENAPI artifacts
        for (int idx = 0; idx < 3; idx++) {
            createArtifact(group, "testSearchByType_openapi_" + idx, ArtifactType.OPENAPI,
                    "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        }
        // Create AVRO artifacts
        for (int idx = 0; idx < 2; idx++) {
            createArtifact(group, "testSearchByType_avro_" + idx, ArtifactType.AVRO,
                    "{\"type\":\"record\",\"name\":\"Test\",\"fields\":[]}",
                    ContentTypes.APPLICATION_JSON);
        }

        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.artifactType = ArtifactType.OPENAPI;
        });
        Assertions.assertEquals(3, results.getCount());
        for (SearchedVersion v : results.getVersions()) {
            Assertions.assertEquals(ArtifactType.OPENAPI, v.getArtifactType());
        }

        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.artifactType = ArtifactType.AVRO;
        });
        Assertions.assertEquals(2, results.getCount());
        for (SearchedVersion v : results.getVersions()) {
            Assertions.assertEquals(ArtifactType.AVRO, v.getArtifactType());
        }
    }

    @Test
    public void testSearchVersionsByState() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create 3 artifacts
        CreateArtifactResponse car1 = createArtifact(group, "testSearchByState_api-1",
                ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        createArtifact(group, "testSearchByState_api-2", ArtifactType.OPENAPI,
                "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        createArtifact(group, "testSearchByState_api-3", ArtifactType.OPENAPI,
                "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);

        // Deprecate the first one
        WrappedVersionState deprecatedState = new WrappedVersionState();
        deprecatedState.setState(VersionState.DEPRECATED);
        clientV3.groups().byGroupId(group).artifacts().byArtifactId("testSearchByState_api-1")
                .versions().byVersionExpression(car1.getVersion().getVersion()).state()
                .put(deprecatedState);

        // Search for DEPRECATED versions
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.state = VersionState.DEPRECATED;
        });
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("testSearchByState_api-1",
                results.getVersions().get(0).getArtifactId());

        // Search for ENABLED versions - should be 2
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.state = VersionState.ENABLED;
        });
        Assertions.assertEquals(2, results.getCount());
    }

    @Test
    public void testSearchVersionsByLabels_KeyOnly() throws Exception {
        String group = TestUtils.generateGroupId();

        CreateArtifactResponse car1 = createArtifact(group, "testSearchByLabelsKey_api-1",
                ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car2 = createArtifact(group, "testSearchByLabelsKey_api-2",
                ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        createArtifact(group, "testSearchByLabelsKey_api-3", ArtifactType.OPENAPI,
                "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);

        // Add labels to first two versions
        EditableVersionMetaData emd1 = new EditableVersionMetaData();
        emd1.setLabels(new Labels());
        emd1.getLabels().setAdditionalData(Map.of("env", "production", "team", "platform"));
        clientV3.groups().byGroupId(group).artifacts().byArtifactId("testSearchByLabelsKey_api-1")
                .versions().byVersionExpression(car1.getVersion().getVersion()).put(emd1);

        EditableVersionMetaData emd2 = new EditableVersionMetaData();
        emd2.setLabels(new Labels());
        emd2.getLabels().setAdditionalData(Map.of("env", "staging"));
        clientV3.groups().byGroupId(group).artifacts().byArtifactId("testSearchByLabelsKey_api-2")
                .versions().byVersionExpression(car2.getVersion().getVersion()).put(emd2);

        // Search for versions with "env" label (key-only) - should match 2
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.labels = new String[] { "env" };
        });
        Assertions.assertEquals(2, results.getCount());

        // Search for versions with "team" label (key-only) - should match 1
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.labels = new String[] { "team" };
        });
        Assertions.assertEquals(1, results.getCount());
    }

    @Test
    public void testSearchVersionsByLabels_KeyAndValue() throws Exception {
        String group = TestUtils.generateGroupId();

        CreateArtifactResponse car1 = createArtifact(group, "testSearchByLabelsKV_api-1",
                ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car2 = createArtifact(group, "testSearchByLabelsKV_api-2",
                ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);

        // Add labels
        EditableVersionMetaData emd1 = new EditableVersionMetaData();
        emd1.setLabels(new Labels());
        emd1.getLabels().setAdditionalData(Map.of("env", "production"));
        clientV3.groups().byGroupId(group).artifacts().byArtifactId("testSearchByLabelsKV_api-1")
                .versions().byVersionExpression(car1.getVersion().getVersion()).put(emd1);

        EditableVersionMetaData emd2 = new EditableVersionMetaData();
        emd2.setLabels(new Labels());
        emd2.getLabels().setAdditionalData(Map.of("env", "staging"));
        clientV3.groups().byGroupId(group).artifacts().byArtifactId("testSearchByLabelsKV_api-2")
                .versions().byVersionExpression(car2.getVersion().getVersion()).put(emd2);

        // Search for env=production - should match 1
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.labels = new String[] { "env:production" };
        });
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("testSearchByLabelsKV_api-1",
                results.getVersions().get(0).getArtifactId());

        // Search for env=staging - should match 1
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.labels = new String[] { "env:staging" };
        });
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("testSearchByLabelsKV_api-2",
                results.getVersions().get(0).getArtifactId());

        // Search for env=nonexistent - should match 0
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.labels = new String[] { "env:nonexistent" };
        });
        Assertions.assertEquals(0, results.getCount());
    }

    @Test
    public void testSearchVersionsByLabels_ReturnedInResults() throws Exception {
        String group = TestUtils.generateGroupId();

        CreateArtifactResponse car = createArtifact(group, "testSearchByLabelsResults_api-1",
                ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);

        EditableVersionMetaData emd = new EditableVersionMetaData();
        emd.setLabels(new Labels());
        emd.getLabels().setAdditionalData(
                Map.of("env", "production", "api-version", "v3"));
        clientV3.groups().byGroupId(group)
                .artifacts().byArtifactId("testSearchByLabelsResults_api-1")
                .versions().byVersionExpression(car.getVersion().getVersion()).put(emd);

        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.labels = new String[] { "env:production" };
        });
        Assertions.assertEquals(1, results.getCount());

        // Verify labels are included in search results
        SearchedVersion version = results.getVersions().get(0);
        Assertions.assertNotNull(version.getLabels());
        Assertions.assertEquals("production",
                version.getLabels().getAdditionalData().get("env"));
        Assertions.assertEquals("v3",
                version.getLabels().getAdditionalData().get("api-version"));
    }

    @Test
    public void testSearchVersionsByGlobalId() throws Exception {
        String group = TestUtils.generateGroupId();

        CreateArtifactResponse car = createArtifact(group, "testSearchByGlobalId_api-1",
                ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        Long globalId = car.getVersion().getGlobalId();

        // Also create another artifact to ensure we only get 1 result
        createArtifact(group, "testSearchByGlobalId_api-2", ArtifactType.OPENAPI,
                "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);

        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.globalId = globalId;
        });
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(globalId, results.getVersions().get(0).getGlobalId());
        Assertions.assertEquals("testSearchByGlobalId_api-1",
                results.getVersions().get(0).getArtifactId());
    }

    @Test
    public void testSearchVersionsByContentId() throws Exception {
        String group = TestUtils.generateGroupId();
        String sharedContent = "{\"shared\":\"testSearchByContentId-content\"}";

        // Create multiple artifacts with the same content (they will share contentId)
        createArtifact(group, "testSearchByContentId_api-1", ArtifactType.OPENAPI, sharedContent,
                ContentTypes.APPLICATION_JSON);
        createArtifact(group, "testSearchByContentId_api-2", ArtifactType.OPENAPI, sharedContent,
                ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse car3 = createArtifact(group, "testSearchByContentId_api-3",
                ArtifactType.OPENAPI, sharedContent, ContentTypes.APPLICATION_JSON);
        Long contentId = car3.getVersion().getContentId();

        // Also create an artifact with different content
        createArtifact(group, "testSearchByContentId_api-4", ArtifactType.OPENAPI,
                "{\"different\":\"testSearchByContentId-other\"}",
                ContentTypes.APPLICATION_JSON);

        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.contentId = contentId;
        });
        // All 3 artifacts with the same content share the contentId
        Assertions.assertEquals(3, results.getCount());
        for (SearchedVersion v : results.getVersions()) {
            Assertions.assertEquals(contentId, v.getContentId());
        }
    }

    @Test
    public void testSearchVersionsPagination() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create 7 artifacts
        for (int idx = 0; idx < 7; idx++) {
            createArtifact(group, "testSearchPagination_api-" + idx, ArtifactType.OPENAPI,
                    "{\"openapi\":\"3.0.0\",\"idx\":" + idx + "}",
                    ContentTypes.APPLICATION_JSON);
        }

        // Get all results
        VersionSearchResults all = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.limit = 100;
        });
        Assertions.assertEquals(7, all.getCount());

        // Page 1: offset=0, limit=3
        VersionSearchResults page1 = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 3;
        });
        Assertions.assertEquals(7, page1.getCount()); // Total count remains the same
        Assertions.assertEquals(3, page1.getVersions().size());

        // Page 2: offset=3, limit=3
        VersionSearchResults page2 = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.offset = 3;
            config.queryParameters.limit = 3;
        });
        Assertions.assertEquals(7, page2.getCount());
        Assertions.assertEquals(3, page2.getVersions().size());

        // Page 3: offset=6, limit=3
        VersionSearchResults page3 = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.offset = 6;
            config.queryParameters.limit = 3;
        });
        Assertions.assertEquals(7, page3.getCount());
        Assertions.assertEquals(1, page3.getVersions().size()); // Only 1 remaining

        // Verify no overlap between pages
        for (SearchedVersion v1 : page1.getVersions()) {
            for (SearchedVersion v2 : page2.getVersions()) {
                Assertions.assertNotEquals(v1.getGlobalId(), v2.getGlobalId(),
                        "Pages should not have overlapping results");
            }
        }
    }

    @Test
    public void testSearchVersionsSortedByName() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create artifacts with distinct names
        String[] names = { "Charlie API", "Alpha API", "Bravo API" };
        for (int idx = 0; idx < names.length; idx++) {
            String artifactId = "testSearchSorted_api-" + idx;
            CreateArtifactResponse car = createArtifact(group, artifactId, ArtifactType.OPENAPI,
                    "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);

            EditableVersionMetaData emd = new EditableVersionMetaData();
            emd.setName(names[idx]);
            clientV3.groups().byGroupId(group).artifacts().byArtifactId(artifactId)
                    .versions().byVersionExpression(car.getVersion().getVersion()).put(emd);
        }

        // Sort by name ascending
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.orderby = VersionSortBy.Name;
            config.queryParameters.order = SortOrder.Asc;
        });
        Assertions.assertEquals(3, results.getCount());
        Assertions.assertEquals("Alpha API", results.getVersions().get(0).getName());
        Assertions.assertEquals("Bravo API", results.getVersions().get(1).getName());
        Assertions.assertEquals("Charlie API", results.getVersions().get(2).getName());

        // Sort by name descending
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.orderby = VersionSortBy.Name;
            config.queryParameters.order = SortOrder.Desc;
        });
        Assertions.assertEquals(3, results.getCount());
        Assertions.assertEquals("Charlie API", results.getVersions().get(0).getName());
        Assertions.assertEquals("Bravo API", results.getVersions().get(1).getName());
        Assertions.assertEquals("Alpha API", results.getVersions().get(2).getName());
    }

    @Test
    public void testSearchVersionsSortedByGlobalId() throws Exception {
        String group = TestUtils.generateGroupId();

        for (int idx = 0; idx < 4; idx++) {
            createArtifact(group, "testSearchSortGlobalId_api-" + idx, ArtifactType.OPENAPI,
                    "{\"openapi\":\"3.0.0\",\"idx\":" + idx + "}",
                    ContentTypes.APPLICATION_JSON);
        }

        // Sort by globalId ascending
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.orderby = VersionSortBy.GlobalId;
            config.queryParameters.order = SortOrder.Asc;
        });
        Assertions.assertEquals(4, results.getCount());
        for (int i = 1; i < results.getVersions().size(); i++) {
            long prev = results.getVersions().get(i - 1).getGlobalId();
            long curr = results.getVersions().get(i).getGlobalId();
            Assertions.assertTrue(prev < curr,
                    "GlobalIds should be in ascending order: " + prev + " < " + curr);
        }

        // Sort by globalId descending
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.orderby = VersionSortBy.GlobalId;
            config.queryParameters.order = SortOrder.Desc;
        });
        Assertions.assertEquals(4, results.getCount());
        for (int i = 1; i < results.getVersions().size(); i++) {
            long prev = results.getVersions().get(i - 1).getGlobalId();
            long curr = results.getVersions().get(i).getGlobalId();
            Assertions.assertTrue(prev > curr,
                    "GlobalIds should be in descending order: " + prev + " > " + curr);
        }
    }

    @Test
    public void testSearchVersionsMultipleFilters() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create OPENAPI and AVRO artifacts
        createArtifact(group, "testMultiFilter_openapi-1", ArtifactType.OPENAPI,
                "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        createArtifact(group, "testMultiFilter_openapi-2", ArtifactType.OPENAPI,
                "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        createArtifact(group, "testMultiFilter_avro-1", ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"Test\",\"fields\":[]}",
                ContentTypes.APPLICATION_JSON);

        // Search with groupId + artifactType = OPENAPI
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.artifactType = ArtifactType.OPENAPI;
        });
        Assertions.assertEquals(2, results.getCount());
        for (SearchedVersion v : results.getVersions()) {
            Assertions.assertEquals(group, v.getGroupId());
            Assertions.assertEquals(ArtifactType.OPENAPI, v.getArtifactType());
        }

        // Search with groupId + artifactType = AVRO
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.artifactType = ArtifactType.AVRO;
        });
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("testMultiFilter_avro-1",
                results.getVersions().get(0).getArtifactId());
    }

    @Test
    public void testSearchVersionsByVersion() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create an artifact with multiple versions
        createArtifact(group, "testSearchByVersion_api-1", ArtifactType.OPENAPI,
                "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(group, "testSearchByVersion_api-1", "{\"openapi\":\"3.0.1\"}",
                ContentTypes.APPLICATION_JSON);

        // Search for version "1" (first auto-generated version)
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.artifactId = "testSearchByVersion_api-1";
            config.queryParameters.version = "1";
        });
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("1", results.getVersions().get(0).getVersion());
    }

    @Test
    public void testSearchVersionsResultMapping() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create an artifact with full metadata
        CreateArtifactResponse car = createArtifact(group, "testResultMapping_api-1",
                ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        Long globalId = car.getVersion().getGlobalId();
        Long contentId = car.getVersion().getContentId();

        // Update version metadata with name, description, and labels
        EditableVersionMetaData emd = new EditableVersionMetaData();
        emd.setName("Result Mapping Test API");
        emd.setDescription("API to verify search result field mapping");
        emd.setLabels(new Labels());
        emd.getLabels().setAdditionalData(Map.of("env", "test", "version-tag", "v1"));
        clientV3.groups().byGroupId(group).artifacts().byArtifactId("testResultMapping_api-1")
                .versions().byVersionExpression(car.getVersion().getVersion()).put(emd);

        // Search and verify all result fields are populated
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.globalId = globalId;
        });
        Assertions.assertEquals(1, results.getCount());

        SearchedVersion version = results.getVersions().get(0);
        Assertions.assertEquals(globalId, version.getGlobalId());
        Assertions.assertEquals(contentId, version.getContentId());
        Assertions.assertEquals(group, version.getGroupId());
        Assertions.assertEquals("testResultMapping_api-1", version.getArtifactId());
        Assertions.assertEquals("1", version.getVersion());
        Assertions.assertEquals(ArtifactType.OPENAPI, version.getArtifactType());
        Assertions.assertEquals(VersionState.ENABLED, version.getState());
        Assertions.assertEquals("Result Mapping Test API", version.getName());
        Assertions.assertEquals("API to verify search result field mapping",
                version.getDescription());
        Assertions.assertNotNull(version.getCreatedOn());
        Assertions.assertNotNull(version.getLabels());
        Assertions.assertEquals("test",
                version.getLabels().getAdditionalData().get("env"));
        Assertions.assertEquals("v1",
                version.getLabels().getAdditionalData().get("version-tag"));
    }
}
