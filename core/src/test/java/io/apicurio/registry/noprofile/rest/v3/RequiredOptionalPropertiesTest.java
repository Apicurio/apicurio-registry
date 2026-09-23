package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.GroupSearchResults;
import io.apicurio.registry.rest.client.models.SearchedGroup;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test to verify that required and optional properties are handled correctly for Group, Artifact, and
 * Version metadata in accordance with the OpenAPI specification.
 */
@QuarkusTest
public class RequiredOptionalPropertiesTest extends AbstractResourceTestBase {

    private static final String OPENAPI_CONTENT = """
            {
              "openapi": "3.0.2",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              }
            }
            """;

    /**
     * Tests that when creating a Group without providing optional fields (description, labels), the
     * required field (modifiedBy) is still present in the response.
     */
    @Test
    public void testGroupRequiredOptionalProperties() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create a group with only required fields (no description, no labels)
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);

        GroupMetaData createdGroup = clientV3.groups().post(createGroup);

        // Verify required fields are present
        Assertions.assertNotNull(createdGroup.getGroupId(), "groupId should be present");
        Assertions.assertNotNull(createdGroup.getOwner(), "owner should be present");
        Assertions.assertNotNull(createdGroup.getCreatedOn(), "createdOn should be present");
        Assertions.assertNotNull(createdGroup.getModifiedBy(), "modifiedBy should always be present");
        Assertions.assertNotNull(createdGroup.getModifiedOn(), "modifiedOn should always be present");

        // Verify modifiedBy defaults to owner when not explicitly set
        Assertions.assertEquals(createdGroup.getOwner(), createdGroup.getModifiedBy(),
                "modifiedBy should default to owner");

        // Verify optional fields can be absent
        Assertions.assertNull(createdGroup.getDescription(),
                "description should be null when not provided");
        Assertions.assertNull(createdGroup.getLabels(), "labels should be null when not provided");

        // Verify the group appears correctly in search results
        GroupSearchResults searchResults = clientV3.groups().get();
        SearchedGroup searchedGroup = searchResults.getGroups().stream()
                .filter(g -> groupId.equals(g.getGroupId())).findFirst().orElse(null);

        Assertions.assertNotNull(searchedGroup, "Group should appear in search results");
        Assertions.assertNotNull(searchedGroup.getModifiedBy(),
                "modifiedBy should be present in search results");
        Assertions.assertEquals(searchedGroup.getOwner(), searchedGroup.getModifiedBy(),
                "modifiedBy should match owner in search results");
    }

    /**
     * Tests that when creating an Artifact without providing optional metadata fields, the required
     * fields (including modifiedBy) are still present in the response.
     */
    @Test
    public void testArtifactRequiredOptionalProperties() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create artifact with minimal metadata (no name, no description, no labels)
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.OPENAPI);
        createArtifact.setFirstVersion(TestUtils.clientCreateVersion(OPENAPI_CONTENT,
                ContentTypes.APPLICATION_JSON));

        CreateArtifactResponse response = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        ArtifactMetaData artifact = response.getArtifact();

        // Verify required fields are present
        Assertions.assertNotNull(artifact.getGroupId(), "groupId should be present");
        Assertions.assertNotNull(artifact.getArtifactId(), "artifactId should be present");
        Assertions.assertNotNull(artifact.getOwner(), "owner should be present");
        Assertions.assertNotNull(artifact.getCreatedOn(), "createdOn should be present");
        Assertions.assertNotNull(artifact.getModifiedBy(), "modifiedBy should always be present");
        Assertions.assertNotNull(artifact.getModifiedOn(), "modifiedOn should always be present");

        // Verify modifiedBy defaults to owner when not explicitly set
        Assertions.assertEquals(artifact.getOwner(), artifact.getModifiedBy(),
                "modifiedBy should default to owner");

        // Verify optional fields can be absent
        Assertions.assertNull(artifact.getName(), "name should be null when not provided");
        Assertions.assertNull(artifact.getDescription(), "description should be null when not provided");
        Assertions.assertNull(artifact.getLabels(), "labels should be null when not provided");
    }

    /**
     * Tests that when creating a Version without providing optional metadata fields, the required fields
     * (including modifiedBy) are still present in the response.
     */
    @Test
    public void testVersionRequiredOptionalProperties() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // First create an artifact
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.OPENAPI);
        createArtifact.setFirstVersion(TestUtils.clientCreateVersion(OPENAPI_CONTENT,
                ContentTypes.APPLICATION_JSON));

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Now create a new version with minimal metadata (no name, no description, no labels)
        String updatedContent = OPENAPI_CONTENT.replace("1.0.0", "1.0.1");
        CreateVersion createVersion = TestUtils.clientCreateVersion(updatedContent,
                ContentTypes.APPLICATION_JSON);

        VersionMetaData version = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().post(createVersion);

        // Verify required fields are present
        Assertions.assertNotNull(version.getGroupId(), "groupId should be present");
        Assertions.assertNotNull(version.getArtifactId(), "artifactId should be present");
        Assertions.assertNotNull(version.getVersion(), "version should be present");
        Assertions.assertNotNull(version.getGlobalId(), "globalId should be present");
        Assertions.assertNotNull(version.getContentId(), "contentId should be present");
        Assertions.assertNotNull(version.getOwner(), "owner should be present");
        Assertions.assertNotNull(version.getCreatedOn(), "createdOn should be present");
        Assertions.assertNotNull(version.getModifiedBy(), "modifiedBy should always be present");
        Assertions.assertNotNull(version.getModifiedOn(), "modifiedOn should always be present");

        // Verify modifiedBy defaults to owner when not explicitly set
        Assertions.assertEquals(version.getOwner(), version.getModifiedBy(),
                "modifiedBy should default to owner");

        // Verify optional fields can be absent
        Assertions.assertNull(version.getName(), "name should be null when not provided");
        Assertions.assertNull(version.getDescription(), "description should be null when not provided");
        Assertions.assertNull(version.getLabels(), "labels should be null when not provided");
    }

    /**
     * Tests that when creating a Group WITH optional fields provided, all fields are properly stored and
     * returned.
     */
    @Test
    public void testGroupWithOptionalProperties() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create a group with optional fields
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        createGroup.setDescription("Test group description");

        GroupMetaData createdGroup = clientV3.groups().post(createGroup);

        // Verify all fields are present
        Assertions.assertNotNull(createdGroup.getGroupId(), "groupId should be present");
        Assertions.assertNotNull(createdGroup.getOwner(), "owner should be present");
        Assertions.assertNotNull(createdGroup.getCreatedOn(), "createdOn should be present");
        Assertions.assertNotNull(createdGroup.getModifiedBy(), "modifiedBy should be present");
        Assertions.assertNotNull(createdGroup.getModifiedOn(), "modifiedOn should be present");
        Assertions.assertEquals("Test group description", createdGroup.getDescription(),
                "description should match provided value");
    }
}