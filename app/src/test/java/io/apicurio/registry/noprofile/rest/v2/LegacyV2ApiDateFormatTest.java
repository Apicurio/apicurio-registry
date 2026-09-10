package io.apicurio.registry.noprofile.rest.v2;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.v2.models.ArtifactContent;
import io.apicurio.registry.rest.client.v2.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.v2.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.v2.models.IfExists;
import io.apicurio.registry.rest.client.v2.models.VersionMetaData;
import io.apicurio.registry.rest.client.v2.models.VersionSearchResults;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that the v2 Java client SDK correctly parses date fields when the server
 * is configured with the legacy (non-ISO-8601 compliant) date format
 * (yyyy-MM-dd'T'HH:mm:ssZ, e.g. "2025-10-29T21:54:37+0000").
 *
 * This originally documented the parsing failure reported in issue #6799. That bug
 * has since been fixed via a fallback parser (see DateTimeUtil.getOffsetDateTimeValue
 * and the post-process-kiota build step in java-sdk/client-v2/pom.xml), so these
 * tests now assert that legacy-format dates parse successfully, not that they fail.
 */
@QuarkusTest
@TestProfile(LegacyV2ApiDateFormatTest.LegacyV2DateFormatTestProfile.class)
class LegacyV2ApiDateFormatTest extends AbstractResourceTestBase {

    public static class LegacyV2DateFormatTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.apis.date-format", "yyyy-MM-dd'T'HH:mm:ssZ");
        }
    }

    @Test
    void testCreateArtifactParsesLegacyDateFormat() {
        String groupId = TestUtils.generateGroupId();
        String artifactContentString = resourceToString("openapi-empty.json");
        String artifactId = "testCreateArtifact";

        ArtifactContent artifactContent = new ArtifactContent();
        artifactContent.setContent(artifactContentString);

        ArtifactMetaData metadata = assertDoesNotThrow(() ->
                clientV2.groups()
                        .byGroupId(groupId)
                        .artifacts()
                        .post(artifactContent, requestConfig -> {
                            requestConfig.headers.add("X-Registry-ArtifactId", artifactId);
                            requestConfig.headers.add("X-Registry-ArtifactType", "OPENAPI");
                            requestConfig.queryParameters.ifExists = IfExists.FAIL;
                        }),
                "Creating an artifact should succeed with the legacy date format enabled");

        assertNotNull(metadata.getCreatedOn(), "createdOn should be parsed despite the legacy date format");
    }

    @Test
    void testGetArtifactMetadataParsesLegacyDateFormat() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "testGetArtifactMetadata";

        createArtifact(groupId, artifactId);

        ArtifactMetaData metadata = assertDoesNotThrow(() ->
                clientV2.groups()
                        .byGroupId(groupId)
                        .artifacts()
                        .byArtifactId(artifactId)
                        .meta()
                        .get(),
                "Getting artifact metadata should succeed with the legacy date format enabled");

        assertNotNull(metadata.getCreatedOn(), "createdOn should be parsed despite the legacy date format");
        assertNotNull(metadata.getModifiedOn(), "modifiedOn should be parsed despite the legacy date format");
    }

    @Test
    void testGetVersionMetadataParsesLegacyDateFormat() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "testGetVersionMetadata";

        createArtifact(groupId, artifactId);

        VersionMetaData versionMetadata = assertDoesNotThrow(() ->
                clientV2.groups()
                        .byGroupId(groupId)
                        .artifacts()
                        .byArtifactId(artifactId)
                        .versions()
                        .byVersion("1")
                        .meta()
                        .get(),
                "Getting version metadata should succeed with the legacy date format enabled");

        assertNotNull(versionMetadata.getCreatedOn(), "createdOn should be parsed despite the legacy date format");
    }

    @Test
    void testListVersionsParsesLegacyDateFormat() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "testListVersions";

        createArtifact(groupId, artifactId);

        VersionSearchResults results = assertDoesNotThrow(() ->
                clientV2.groups()
                        .byGroupId(groupId)
                        .artifacts()
                        .byArtifactId(artifactId)
                        .versions()
                        .get(),
                "Listing versions should succeed with the legacy date format enabled");

        assertFalse(results.getVersions().isEmpty(), "Expected at least one version");
        results.getVersions().forEach(v ->
                assertNotNull(v.getCreatedOn(), "createdOn should be parsed despite the legacy date format"));
    }

    @Test
    void testSearchArtifactsParsesLegacyDateFormat() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "testSearchArtifacts";

        createArtifact(groupId, artifactId);

        ArtifactSearchResults results = assertDoesNotThrow(() ->
                clientV2.search()
                        .artifacts()
                        .get(requestConfig -> {
                            requestConfig.queryParameters.group = groupId;
                        }),
                "Searching artifacts should succeed with the legacy date format enabled");

        assertFalse(results.getArtifacts().isEmpty(), "Expected at least one search result");
        results.getArtifacts().forEach(a ->
                assertNotNull(a.getCreatedOn(), "createdOn should be parsed despite the legacy date format"));
    }

    private void createArtifact(String groupId, String artifactId) {
        try {
            String artifactContent = resourceToString("openapi-empty.json");
            createArtifact(groupId, artifactId, io.apicurio.registry.types.ArtifactType.OPENAPI,
                    artifactContent, io.apicurio.registry.types.ContentTypes.APPLICATION_JSON, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create artifact", e);
        }
    }
}