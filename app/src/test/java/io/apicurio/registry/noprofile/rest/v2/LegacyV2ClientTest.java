package io.apicurio.registry.noprofile.rest.v2;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.v2.models.ArtifactContent;
import io.apicurio.registry.rest.client.v2.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.v2.models.IfExists;
import io.apicurio.registry.rest.client.v2.models.VersionMetaData;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests using the v2 Java client SDK against the v2 REST API endpoints
 * with default server configurations (current ISO-8601 compliant date format).
 */
@QuarkusTest
class LegacyV2ClientTest extends AbstractResourceTestBase {

    /**
     * Test that the v2 client can successfully create and retrieve an artifact
     * using the default (ISO-8601 compliant) date format configuration.
     */
    @Test
    void testClientV2WithDefaultDateFormat() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactContentString = resourceToString("openapi-empty.json");
        String artifactId = "testDefaultDateFormat";

        // Create artifact content
        ArtifactContent artifactContent = new ArtifactContent();
        artifactContent.setContent(artifactContentString);

        // Create artifact using v2 client directly
        ArtifactMetaData metadata = clientV2.groups()
                .byGroupId(groupId)
                .artifacts()
                .post(artifactContent,
                        requestConfig -> {
                            requestConfig.headers.add("X-Registry-ArtifactId", artifactId);
                            requestConfig.headers.add("X-Registry-ArtifactType", "OPENAPI");
                            requestConfig.queryParameters.ifExists = IfExists.FAIL;
                        });

        // Verify metadata was returned successfully
        assertNotNull(metadata, "Artifact metadata should not be null");
        assertNotNull(metadata.getGlobalId(), "Global ID should not be null");
        assertNotNull(metadata.getCreatedOn(), "CreatedOn timestamp should not be null");

        // Retrieve the artifact version metadata
        VersionMetaData versionMetadata = clientV2.groups()
                .byGroupId(groupId)
                .artifacts()
                .byArtifactId(artifactId)
                .versions()
                .byVersion("latest")
                .meta()
                .get();

        // Verify version metadata
        assertNotNull(versionMetadata, "Version metadata should not be null");
        assertNotNull(versionMetadata.getGlobalId(), "Global ID should not be null");
        assertNotNull(versionMetadata.getCreatedOn(), "CreatedOn timestamp should not be null");

        // Verify date fields are valid OffsetDateTime instances
        OffsetDateTime createdOn = versionMetadata.getCreatedOn();
        assertNotNull(createdOn, "CreatedOn should be parsed as OffsetDateTime");
    }

    /**
     * Test that the v2 client can successfully retrieve artifact metadata
     * and all date fields are properly parsed.
     */
    @Test
    void testClientV2DateFieldsParsing() {
        String groupId = TestUtils.generateGroupId();
        String artifactContentString = resourceToString("openapi-empty.json");
        String artifactId = "testDateFieldsParsing";

        // Create artifact content
        ArtifactContent artifactContent = new ArtifactContent();
        artifactContent.setContent(artifactContentString);

        // Create artifact using v2 client directly
        clientV2.groups()
                .byGroupId(groupId)
                .artifacts()
                .post(artifactContent,
                        requestConfig -> {
                            requestConfig.headers.add("X-Registry-ArtifactId", artifactId);
                            requestConfig.headers.add("X-Registry-ArtifactType", "OPENAPI");
                            requestConfig.queryParameters.ifExists = IfExists.FAIL;
                        });

        // Retrieve artifact metadata using direct client
        ArtifactMetaData artifactMetadata = clientV2.groups()
                .byGroupId(groupId)
                .artifacts()
                .byArtifactId(artifactId)
                .meta()
                .get();

        // Verify all date fields are properly parsed
        assertNotNull(artifactMetadata.getCreatedOn(),
                "ArtifactMetaData createdOn should be parsed");
        assertNotNull(artifactMetadata.getModifiedOn(),
                "ArtifactMetaData modifiedOn should be parsed");

        // Retrieve version metadata
        VersionMetaData versionMetadata = clientV2.groups()
                .byGroupId(groupId)
                .artifacts()
                .byArtifactId(artifactId)
                .versions()
                .byVersion("1")
                .meta()
                .get();

        // Verify version date fields
        assertNotNull(versionMetadata.getCreatedOn(),
                "VersionMetaData createdOn should be parsed");
    }
}