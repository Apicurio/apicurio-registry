package io.apicurio.registry.noprofile.rest.v2;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.v2.models.ArtifactContent;
import io.apicurio.registry.rest.client.v2.models.IfExists;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Tests that the v2 Java client SDK fails to parse date fields when the server
 * uses the legacy (non-ISO-8601 compliant) date format. This test demonstrates
 * the bug reported in issue #6799 where dates formatted as "2025-10-29T21:54:37+0000"
 * (without colon in timezone) cannot be parsed by OffsetDateTime.
 */
@QuarkusTest
@TestProfile(LegacyV2ApiDateFormatTest.LegacyV2DateFormatTestProfile.class)
class LegacyV2ApiDateFormatTest extends AbstractResourceTestBase {

    /**
     * Test profile that configures the server to use the legacy date format
     * (yyyy-MM-dd'T'HH:mm:ssZ) which produces dates like "2025-10-29T21:54:37+0000"
     * instead of ISO-8601 compliant "2025-10-29T21:54:37Z" or "2025-10-29T21:54:37+00:00".
     */
    public static class LegacyV2DateFormatTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.apis.date-format", "yyyy-MM-dd'T'HH:mm:ssZ");
        }
    }

    /**
     * Test that creating an artifact fails to parse the returned metadata
     * when using the legacy date format.
     */
    @Test
    void testCreateArtifactFailsLegacyDateFormatParsing() {
        String groupId  = TestUtils.generateGroupId();
        String artifactContentString = resourceToString("openapi-empty.json");
        String artifactId = "testCreateArtifact";

        ArtifactContent artifactContent = new ArtifactContent();
        artifactContent.setContent(artifactContentString);

        // Attempt to create artifact - should fail when parsing the returned ArtifactMetaData
        clientV2.groups()
                .byGroupId(groupId)
                .artifacts()
                .post(artifactContent, requestConfig -> {
                    requestConfig.headers.add("X-Registry-ArtifactId", artifactId);
                    requestConfig.headers.add("X-Registry-ArtifactType", "OPENAPI");
                    requestConfig.queryParameters.ifExists = IfExists.FAIL;
                });
    }

    /**
     * Test that retrieving artifact metadata fails to parse date fields
     * when using the legacy date format.
     */
    @Test
    void testGetArtifactMetadataFailsLegacyDateFormatParsing() {
        String groupId  = TestUtils.generateGroupId();
        String artifactId = "testGetArtifactMetadata";

        // Create artifact using REST API directly (bypassing client SDK)
        createArtifact(groupId, artifactId);

        // Attempt to get artifact metadata - should be able to parse the dateTime
        clientV2.groups()
                .byGroupId(groupId)
                .artifacts()
                .byArtifactId(artifactId)
                .meta()
                .get();
    }

    /**
     * Test that retrieving version metadata fails to parse date fields
     * when using the legacy date format.
     */
    @Test
    void testGetVersionMetadataFailsLegacyDateFormatParsing() {
        String groupId  = TestUtils.generateGroupId();
        String artifactId = "testGetVersionMetadata";

        // Create artifact using REST API directly (bypassing client SDK)
        createArtifact(groupId, artifactId);

        // Attempt to get version metadata - should be able to parse the dateTime
        clientV2.groups()
                .byGroupId(groupId)
                .artifacts()
                .byArtifactId(artifactId)
                .versions()
                .byVersion("1")
                .meta()
                .get();
    }

    /**
     * Test that listing artifact versions fails to parse date fields
     * when using the legacy date format.
     */
    @Test
    void testListVersionsFailsLegacyDateFormatParsing() {
        String groupId  = TestUtils.generateGroupId();
        String artifactId = "testListVersions";

        // Create artifact using REST API directly (bypassing client SDK)
        createArtifact(groupId, artifactId);

        // Attempt to list versions - should be able to parse the dateTime
        clientV2.groups()
                .byGroupId(groupId)
                .artifacts()
                .byArtifactId(artifactId)
                .versions()
                .get();
    }

    /**
     * Test that searching for artifacts fails to parse date fields
     * when using the legacy date format.
     */
    @Test
    void testSearchArtifactsFailsLegacyDateFormatParsing() {
        String groupId  = TestUtils.generateGroupId();
        String artifactId = "testSearchArtifacts";

        // Create artifact using REST API directly (bypassing client SDK)
        createArtifact(groupId, artifactId);

        // Attempt to search artifacts - should be able to parse the dateTime
        clientV2.search()
                .artifacts()
                .get(requestConfig -> {
                    requestConfig.queryParameters.group = groupId;
                });
    }

    /**
     * Helper method to create an artifact using the REST API directly,
     * bypassing the v2 client SDK to avoid date parsing issues.
     */
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
