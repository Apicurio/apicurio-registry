package io.apicurio.registry.noprofile.rest.v2;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.v2.models.ArtifactContent;
import io.apicurio.registry.rest.client.v2.models.IfExists;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Tests that the v2 Java client SDK can successfully parse date fields using ISO-8601 format.
 *
 * This test verifies that the server now uses ISO-8601 compliant date format
 * (e.g., "2025-10-29T21:54:37Z") which is compatible with generated client SDKs.
 *
 * Prior to v4.0.0, the server supported custom date formats via the deprecated
 * {@code apicurio.apis.date-format} property, which caused SDK incompatibility issues.
 *
 * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/6799">Issue #6799</a>
 */
@QuarkusTest
class LegacyV2ApiDateFormatTest extends AbstractResourceTestBase {

    /**
     * Test that creating an artifact successfully parses the returned metadata
     * using ISO-8601 date format.
     */
    @Test
    void testCreateArtifactParsesIso8601DateFormat() {
        String groupId  = TestUtils.generateGroupId();
        String artifactContentString = resourceToString("openapi-empty.json");
        String artifactId = "testCreateArtifact";

        ArtifactContent artifactContent = new ArtifactContent();
        artifactContent.setContent(artifactContentString);

        // Create artifact - should successfully parse the returned ArtifactMetaData with ISO-8601 dates
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
     * Test that retrieving artifact metadata successfully parses date fields
     * using ISO-8601 date format.
     */
    @Test
    void testGetArtifactMetadataParsesIso8601DateFormat() {
        String groupId  = TestUtils.generateGroupId();
        String artifactId = "testGetArtifactMetadata";

        // Create artifact
        createArtifact(groupId, artifactId);

        // Get artifact metadata - should successfully parse ISO-8601 dates
        clientV2.groups()
                .byGroupId(groupId)
                .artifacts()
                .byArtifactId(artifactId)
                .meta()
                .get();
    }

    /**
     * Test that retrieving version metadata successfully parses date fields
     * using ISO-8601 date format.
     */
    @Test
    void testGetVersionMetadataParsesIso8601DateFormat() {
        String groupId  = TestUtils.generateGroupId();
        String artifactId = "testGetVersionMetadata";

        // Create artifact
        createArtifact(groupId, artifactId);

        // Get version metadata - should successfully parse ISO-8601 dates
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
     * Test that listing artifact versions successfully parses date fields
     * using ISO-8601 date format.
     */
    @Test
    void testListVersionsParsesIso8601DateFormat() {
        String groupId  = TestUtils.generateGroupId();
        String artifactId = "testListVersions";

        // Create artifact
        createArtifact(groupId, artifactId);

        // List versions - should successfully parse ISO-8601 dates
        clientV2.groups()
                .byGroupId(groupId)
                .artifacts()
                .byArtifactId(artifactId)
                .versions()
                .get();
    }

    /**
     * Test that searching for artifacts successfully parses date fields
     * using ISO-8601 date format.
     */
    @Test
    void testSearchArtifactsParsesIso8601DateFormat() {
        String groupId  = TestUtils.generateGroupId();
        String artifactId = "testSearchArtifacts";

        // Create artifact
        createArtifact(groupId, artifactId);

        // Search artifacts - should successfully parse ISO-8601 dates
        clientV2.search()
                .artifacts()
                .get(requestConfig -> {
                    requestConfig.queryParameters.group = groupId;
                });
    }

    /**
     * Helper method to create an artifact using the REST API.
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
