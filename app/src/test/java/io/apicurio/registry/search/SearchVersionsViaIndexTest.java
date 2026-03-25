package io.apicurio.registry.search;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.storage.impl.search.ElasticsearchIndexUpdater;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

/**
 * Integration tests for searching versions via the Elasticsearch index. Uses a QuarkusTestProfile
 * that enables Elasticsearch search indexing. Tests verify ES index sync behavior (deletes) and
 * content search, which requires the ES index. SQL-capable filter tests are covered by
 * {@code SearchVersionsTest} and are not duplicated here.
 */
@QuarkusTest
@TestProfile(ElasticsearchSearchTestProfile.class)
public class SearchVersionsViaIndexTest extends AbstractResourceTestBase {

    @Inject
    ElasticsearchIndexUpdater indexUpdater;

    @Test
    public void testSearchWithNoFiltersExcludesInternalDocs() throws Exception {
        // When no artifacts exist, a search with no filters should return 0 results.
        // This verifies that internal metadata documents (e.g. _mapping_version,
        // _reindex_lock) are excluded from search results.
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.limit = 100;
        });
        // The count should not include internal metadata documents. It may be > 0 if
        // other tests have already created artifacts (tests share an index), but the
        // important thing is that internal docs are not returned as search hits.
        for (SearchedVersion version : results.getVersions()) {
            Assertions.assertNotNull(version.getGlobalId(),
                    "Internal metadata documents should not appear in search results");
            Assertions.assertNotNull(version.getArtifactId(),
                    "Internal metadata documents should not appear in search results");
        }
    }

    @Test
    public void testSearchVersionsByContentFilter() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create artifacts with distinct content containing searchable terms
        String content1 = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Pet Store API\","
                + "\"description\":\"An API for managing pets in the store\"}}";
        String content2 = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"User Management API\","
                + "\"description\":\"An API for managing user accounts\"}}";
        String content3 = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Inventory Service\","
                + "\"description\":\"Service for tracking inventory of pets\"}}";

        createArtifact(group, "testContentFilter_api-1", ArtifactType.OPENAPI.value(),
                content1, ContentTypes.APPLICATION_JSON);
        createArtifact(group, "testContentFilter_api-2", ArtifactType.OPENAPI.value(),
                content2, ContentTypes.APPLICATION_JSON);
        createArtifact(group, "testContentFilter_api-3", ArtifactType.OPENAPI.value(),
                content3, ContentTypes.APPLICATION_JSON);

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        // Search for "pets" in content — should match api-1 and api-3
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.content = "pets";
        });
        Assertions.assertEquals(2, results.getCount(),
                "Content search for 'pets' should match 2 artifacts");

        // Search for "user accounts" in content — should match api-2
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.content = "user accounts";
        });
        Assertions.assertEquals(1, results.getCount(),
                "Content search for 'user accounts' should match 1 artifact");
        Assertions.assertEquals("testContentFilter_api-2",
                results.getVersions().get(0).getArtifactId());

        // Search for a term not in any content — should match 0
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.content = "nonexistent-xyz-term";
        });
        Assertions.assertEquals(0, results.getCount(),
                "Content search for non-matching term should return 0 results");
    }

    @Test
    public void testDeleteArtifactVersion() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create an artifact with 2 versions
        createArtifact(group, "testDeleteVersion_api-1", ArtifactType.OPENAPI.value(),
                "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(group, "testDeleteVersion_api-1", "{\"openapi\":\"3.0.1\"}",
                ContentTypes.APPLICATION_JSON);

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        // Verify both versions are in the index
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.artifactId = "testDeleteVersion_api-1";
        });
        Assertions.assertEquals(2, results.getCount());

        // Delete version "1"
        clientV3.groups().byGroupId(group).artifacts().byArtifactId("testDeleteVersion_api-1")
                .versions().byVersionExpression("1").delete();

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        // Verify only 1 version remains in the index
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.artifactId = "testDeleteVersion_api-1";
        });
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("2", results.getVersions().get(0).getVersion());
    }

    @Test
    public void testDeleteArtifact() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create 2 artifacts, one with multiple versions
        createArtifact(group, "testDeleteArtifact_api-1", ArtifactType.OPENAPI.value(),
                "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(group, "testDeleteArtifact_api-1", "{\"openapi\":\"3.0.1\"}",
                ContentTypes.APPLICATION_JSON);
        createArtifact(group, "testDeleteArtifact_api-2", ArtifactType.OPENAPI.value(),
                "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        // Verify all 3 versions are in the index
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
        });
        Assertions.assertEquals(3, results.getCount());

        // Delete the first artifact (which has 2 versions)
        clientV3.groups().byGroupId(group).artifacts()
                .byArtifactId("testDeleteArtifact_api-1").delete();

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        // Verify only the second artifact's version remains
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
        });
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("testDeleteArtifact_api-2",
                results.getVersions().get(0).getArtifactId());
    }

    @Test
    public void testDeleteArtifactsInGroup() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create 3 artifacts in the group
        for (int idx = 0; idx < 3; idx++) {
            createArtifact(group, "testDeleteArtifactsInGroup_api-" + idx,
                    ArtifactType.OPENAPI.value(), "{\"openapi\":\"3.0.0\",\"idx\":" + idx + "}",
                    ContentTypes.APPLICATION_JSON);
        }

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        // Verify all 3 are in the index
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
        });
        Assertions.assertEquals(3, results.getCount());

        // Delete all artifacts in the group
        clientV3.groups().byGroupId(group).artifacts().delete();

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        // Verify the index is empty for this group
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
        });
        Assertions.assertEquals(0, results.getCount());
    }

    @Test
    public void testDeleteGroup() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create the group explicitly so we can delete it
        var createGroup = new io.apicurio.registry.rest.client.models.CreateGroup();
        createGroup.setGroupId(group);
        clientV3.groups().post(createGroup);

        // Create 3 artifacts in the group
        for (int idx = 0; idx < 3; idx++) {
            createArtifact(group, "testDeleteGroup_api-" + idx, ArtifactType.OPENAPI.value(),
                    "{\"openapi\":\"3.0.0\",\"idx\":" + idx + "}",
                    ContentTypes.APPLICATION_JSON);
        }

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        // Verify all 3 are in the index
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
        });
        Assertions.assertEquals(3, results.getCount());

        // Delete the group (cascades to all artifacts)
        clientV3.groups().byGroupId(group).delete();

        indexUpdater.awaitIdle(10, TimeUnit.SECONDS);

        // Verify the index is empty for this group
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
        });
        Assertions.assertEquals(0, results.getCount());
    }
}

