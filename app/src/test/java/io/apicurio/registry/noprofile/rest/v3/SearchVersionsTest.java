package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SearchVersionsTest extends AbstractResourceTestBase {

    @Test
    public void testSearchVersionsByGroupId() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String group1 = TestUtils.generateGroupId();
        String group2 = TestUtils.generateGroupId();

        // Create 5 artifacts in group 1
        for (int idx = 0; idx < 5; idx++) {
            String artifactId = "testSearchVersionsByGroupId_Group1_Artifact_" + idx;
            createArtifact(group1, artifactId, ArtifactType.OPENAPI, artifactContent,
                    ContentTypes.APPLICATION_JSON);
        }
        // Create 3 artifacts in group 2
        for (int idx = 0; idx < 3; idx++) {
            String artifactId = "testSearchVersionsByGroupId_Group2_Artifact_" + idx;
            this.createArtifact(group2, artifactId, ArtifactType.OPENAPI, artifactContent,
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
        String artifactContent = resourceToString("openapi-empty.json");
        String group1 = TestUtils.generateGroupId();
        String group2 = TestUtils.generateGroupId();

        // Create 5 artifacts in group 1 (two versions each)
        for (int idx = 0; idx < 5; idx++) {
            String artifactId = "testSearchVersionsByArtifactId_Group1_Artifact_" + idx;
            createArtifact(group1, artifactId, ArtifactType.OPENAPI, artifactContent,
                    ContentTypes.APPLICATION_JSON);
            createArtifactVersion(group1, artifactId, artifactContent, ContentTypes.APPLICATION_JSON);
        }
        // Create 3 artifacts in group 2
        for (int idx = 0; idx < 3; idx++) {
            String artifactId = "testSearchVersionsByArtifactId_Group2_Artifact_" + idx;
            createArtifact(group2, artifactId, ArtifactType.OPENAPI, artifactContent,
                    ContentTypes.APPLICATION_JSON);
        }

        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.artifactId = "testSearchVersionsByArtifactId_Group1_Artifact_1";
        });
        Assertions.assertEquals(2, results.getCount());

        results = clientV3.search().versions().get(config -> {
            config.queryParameters.artifactId = "testSearchVersionsByArtifactId_Group2_Artifact_0";
        });
        Assertions.assertEquals(1, results.getCount());
    }

    @Test
    public void testSearchVersionsByContent() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String group = TestUtils.generateGroupId();
        String searchByCommonContent = artifactContent.replaceAll("Empty API",
                "testSearchVersionsByContent-empty-api");
        String searchByUniqueContent = artifactContent.replaceAll("Empty API",
                "testSearchVersionsByContent-empty-api-2");
        String searchByUnknownContent = artifactContent.replaceAll("\\{", "   {\n");

        // Create 5 artifacts with two versions each in the test group
        for (int idx = 0; idx < 5; idx++) {
            String name = "testSearchVersionsByContent-empty-api-" + idx;
            String artifactId = TestUtils.generateArtifactId();
            String uniqueContent = artifactContent.replaceAll("Empty API", name);
            String commonContent = searchByCommonContent;
            // First version is common content (same for every artifact)
            createArtifact(group, artifactId, ArtifactType.OPENAPI, commonContent,
                    ContentTypes.APPLICATION_JSON);
            // Second version is unique to each artifact
            createArtifactVersion(group, artifactId, uniqueContent, ContentTypes.APPLICATION_JSON);
        }

        VersionSearchResults results = clientV3.search().versions().post(asInputStream(searchByCommonContent),
                ContentTypes.APPLICATION_JSON);
        Assertions.assertEquals(5, results.getCount());

        results = clientV3.search().versions().post(asInputStream(searchByUniqueContent),
                ContentTypes.APPLICATION_JSON);
        Assertions.assertEquals(1, results.getCount());

        results = clientV3.search().versions().post(asInputStream(searchByUnknownContent),
                ContentTypes.APPLICATION_JSON);
        Assertions.assertEquals(0, results.getCount());
    }

    @Test
    public void testSearchVersionsByCanonicalContent() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String group = TestUtils.generateGroupId();
        String searchByCommonContent = artifactContent.replaceAll("Empty API",
                "testSearchVersionsByCanonicalContent-empty-api");
        String searchByUniqueContent = artifactContent.replaceAll("Empty API",
                "testSearchVersionsByCanonicalContent-empty-api-2");
        String searchByCanonicalContent = searchByUniqueContent.replaceAll("\\{", "   {\n");

        // Create 5 artifacts with two versions each in the test group
        for (int idx = 0; idx < 5; idx++) {
            String name = "testSearchVersionsByCanonicalContent-empty-api-" + idx;
            String artifactId = TestUtils.generateArtifactId();
            String uniqueContent = artifactContent.replaceAll("Empty API", name);
            String commonContent = searchByCommonContent;
            // First version is common content (same for every artifact)
            createArtifact(group, artifactId, ArtifactType.OPENAPI, commonContent,
                    ContentTypes.APPLICATION_JSON);
            // Second version is unique to each artifact
            createArtifactVersion(group, artifactId, uniqueContent, ContentTypes.APPLICATION_JSON);
        }

        VersionSearchResults results = clientV3.search().versions().post(asInputStream(searchByCommonContent),
                ContentTypes.APPLICATION_JSON);
        Assertions.assertEquals(5, results.getCount());

        results = clientV3.search().versions().post(asInputStream(searchByUniqueContent),
                ContentTypes.APPLICATION_JSON);
        Assertions.assertEquals(1, results.getCount());

        results = clientV3.search().versions().post(asInputStream(searchByCanonicalContent),
                ContentTypes.APPLICATION_JSON);
        Assertions.assertEquals(0, results.getCount());

        results = clientV3.search().versions().post(asInputStream(searchByCanonicalContent),
                ContentTypes.APPLICATION_JSON, (config) -> {
                    config.queryParameters.canonical = true;
                    config.queryParameters.artifactType = ArtifactType.OPENAPI;
                });
        Assertions.assertEquals(1, results.getCount());
    }

    @Test
    public void testSearchVersionsByContentInGA() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String group = TestUtils.generateGroupId();
        String searchByCommonContent = artifactContent.replaceAll("Empty API",
                "testSearchVersionsByContentInGA-empty-api");
        String searchByArtifactId = "";

        // Create 5 artifacts with two versions each in the test group
        for (int idx = 0; idx < 5; idx++) {
            String name = "testSearchVersionsByContentInGA-empty-api-" + idx;
            String artifactId = TestUtils.generateArtifactId();
            String uniqueContent = artifactContent.replaceAll("Empty API", name);
            String commonContent = searchByCommonContent;
            // First version is common content (same for every artifact)
            createArtifact(group, artifactId, ArtifactType.OPENAPI, commonContent,
                    ContentTypes.APPLICATION_JSON);
            // Second version is unique to each artifact
            createArtifactVersion(group, artifactId, uniqueContent, ContentTypes.APPLICATION_JSON);

            // Save the final artifactId to filter by
            searchByArtifactId = artifactId;
        }

        VersionSearchResults results = clientV3.search().versions().post(asInputStream(searchByCommonContent),
                ContentTypes.APPLICATION_JSON);
        Assertions.assertEquals(5, results.getCount());

        // Same search, but also filter by groupId and artifactId - should be just 1
        final String aid = searchByArtifactId;
        results = clientV3.search().versions().post(asInputStream(searchByCommonContent),
                ContentTypes.APPLICATION_JSON, config -> {
                    config.queryParameters.groupId = group;
                    config.queryParameters.artifactId = aid;
                });
        Assertions.assertEquals(1, results.getCount());
    }

    @Test
    public void testSearchVersionsByIds() throws Exception {
        String artifactContent = "testSearchVersionsByIds-content";
        String group1 = TestUtils.generateGroupId();
        String group2 = TestUtils.generateGroupId();

        // Create 5 artifacts in group 1 (two versions each)
        for (int idx = 0; idx < 5; idx++) {
            String artifactId = "testSearchVersionsByIds_Group1_Artifact_" + idx;
            createArtifact(group1, artifactId, ArtifactType.OPENAPI, artifactContent,
                    ContentTypes.APPLICATION_JSON);
            createArtifactVersion(group1, artifactId, artifactContent, ContentTypes.APPLICATION_JSON);
        }
        // Create 3 artifacts in group 2
        CreateArtifactResponse createArtifactResponse = null;
        for (int idx = 0; idx < 3; idx++) {
            String artifactId = "testSearchVersionsByIds_Group2_Artifact_" + idx;
            createArtifactResponse = createArtifact(group2, artifactId, ArtifactType.OPENAPI, artifactContent,
                    ContentTypes.APPLICATION_JSON);
        }

        final Long contentId = createArtifactResponse.getVersion().getContentId();

        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.contentId = contentId;
        });

        // 13 artifacts are sharing the same contentId, 10 created in group 1, and 3 created in group 2.
        Assertions.assertEquals(13, results.getCount());

        final Long globalId = createArtifactResponse.getVersion().getGlobalId();

        results = clientV3.search().versions().get(config -> {
            config.queryParameters.globalId = globalId;
        });

        Assertions.assertEquals(1, results.getCount());
    }

}
