package io.apicurio.registry.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.v2.models.ArtifactContent;
import io.apicurio.registry.rest.client.v2.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.v2.models.SearchedVersion;
import io.apicurio.registry.rest.client.v2.models.VersionMetaData;
import io.apicurio.registry.rest.client.v2.models.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * Test for GitHub issue #6818: X-Registry-Name header value ignored for v2 API 'create artifact' request with v3.1.1 server.
 *
 * This test reproduces the bug where the X-Registry-Name header is ignored when creating artifacts via the v2 API.
 * In v2.6.x, the version name would be set to the value of X-Registry-Name, but in v3.1.1 it is ignored and
 * the name is derived from the content instead.
 *
 * The test scenario:
 * 1. Create an artifact using v2 API with X-Registry-Name set to "1.0.0"
 * 2. Verify that the version name is "1.0.0" (not the artifact ID or content-derived name)
 * 3. Create a new version with X-Registry-Name and verify it works for version updates too
 * 4. Test X-Registry-Description header as well
 *
 * Expected behavior: Version name should match X-Registry-Name header value
 * Actual behavior: Version name is derived from content or artifact ID
 *
 * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/6818">GitHub Issue #6818</a>
 */
@QuarkusTest
public class Issue6818Test extends AbstractResourceTestBase {

    private static final String AVRO_SCHEMA = """
    {
      "type": "record",
      "name": "person",
      "fields": [
        {
          "name": "name",
          "type": "string"
        },
        {
          "name": "age",
          "type": "int"
        }
      ]
    }
    """;

    private static final String AVRO_SCHEMA_V2 = """
    {
      "type": "record",
      "name": "person",
      "fields": [
        {
          "name": "name",
          "type": "string"
        },
        {
          "name": "age",
          "type": "int"
        },
        {
          "name": "email",
          "type": ["null", "string"],
          "default": null
        }
      ]
    }
    """;

    /**
     * Test that X-Registry-Name header is properly used when creating a new artifact.
     * This is the main test for issue #6818.
     */
    @Test
    public void testXRegistryNameOnCreateArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "person";
        String versionName = "1.0.0";
        String versionDescription = "Initial version";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Create an artifact with X-Registry-Name and X-Registry-Description headers
        ArtifactContent body = new ArtifactContent();
        body.setContent(AVRO_SCHEMA);
        ArtifactMetaData amd = clientV2.groups().byGroupId(groupId).artifacts().post(body, config -> {
            config.headers.put("X-Registry-ArtifactId", Set.of(artifactId));
            config.headers.put("X-Registry-ArtifactType", Set.of(ArtifactType.AVRO));
            config.headers.put("X-Registry-Name", Set.of(versionName));
            config.headers.put("X-Registry-Description", Set.of(versionDescription));
        });

        // Verify artifact metadata
        Assertions.assertNotNull(amd);
        Assertions.assertEquals(groupId, amd.getGroupId());
        Assertions.assertEquals(artifactId, amd.getId());
        Assertions.assertEquals(ArtifactType.AVRO, amd.getType());
        Assertions.assertEquals("1", amd.getVersion());

        // THIS IS THE KEY ASSERTION: The name should be "1.0.0", not "person" or derived from content
        Assertions.assertEquals(versionName, amd.getName(),
            "Version name should match X-Registry-Name header value");
        Assertions.assertEquals(versionDescription, amd.getDescription(),
            "Version description should match X-Registry-Description header value");

        // Also verify by fetching the versions list
        VersionSearchResults versions = clientV2.groups().byGroupId(groupId)
            .artifacts().byArtifactId(artifactId)
            .versions().get();

        Assertions.assertEquals(1, versions.getCount());
        SearchedVersion searchedVersion = versions.getVersions().get(0);
        Assertions.assertEquals(versionName, searchedVersion.getName(),
            "Version name in versions list should match X-Registry-Name header value");
        Assertions.assertEquals(versionDescription, searchedVersion.getDescription(),
            "Version description in versions list should match X-Registry-Description header value");
    }

    /**
     * Test that X-Registry-Name header is properly used when creating a new version of an existing artifact.
     */
    @Test
    public void testXRegistryNameOnCreateVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "person-v2";
        String version1Name = "1.0.0";
        String version1Description = "Initial version";
        String version2Name = "2.0.0";
        String version2Description = "Second version with email field";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Create initial artifact with version 1
        ArtifactContent body = new ArtifactContent();
        body.setContent(AVRO_SCHEMA);
        ArtifactMetaData amd = clientV2.groups().byGroupId(groupId).artifacts().post(body, config -> {
            config.headers.put("X-Registry-ArtifactId", Set.of(artifactId));
            config.headers.put("X-Registry-ArtifactType", Set.of(ArtifactType.AVRO));
            config.headers.put("X-Registry-Name", Set.of(version1Name));
            config.headers.put("X-Registry-Description", Set.of(version1Description));
        });

        Assertions.assertEquals(version1Name, amd.getName());
        Assertions.assertEquals(version1Description, amd.getDescription());

        // Create version 2 with different name and description
        body.setContent(AVRO_SCHEMA_V2);
        VersionMetaData vmd = clientV2.groups().byGroupId(groupId)
            .artifacts().byArtifactId(artifactId)
            .versions().post(body, config -> {
                config.headers.put("X-Registry-Name", Set.of(version2Name));
                config.headers.put("X-Registry-Description", Set.of(version2Description));
            });

        // Verify version 2 metadata
        Assertions.assertEquals(version2Name, vmd.getName(),
            "Version 2 name should match X-Registry-Name header value");
        Assertions.assertEquals(version2Description, vmd.getDescription(),
            "Version 2 description should match X-Registry-Description header value");

        // Verify both versions have correct names
        VersionSearchResults versions = clientV2.groups().byGroupId(groupId)
            .artifacts().byArtifactId(artifactId)
            .versions().get();

        Assertions.assertEquals(2, versions.getCount());

        // Find version 1 and verify
        SearchedVersion v1 = versions.getVersions().stream()
            .filter(v -> "1".equals(v.getVersion()))
            .findFirst()
            .orElseThrow();
        Assertions.assertEquals(version1Name, v1.getName());
        Assertions.assertEquals(version1Description, v1.getDescription());

        // Find version 2 and verify
        SearchedVersion v2 = versions.getVersions().stream()
            .filter(v -> "2".equals(v.getVersion()))
            .findFirst()
            .orElseThrow();
        Assertions.assertEquals(version2Name, v2.getName());
        Assertions.assertEquals(version2Description, v2.getDescription());
    }

    /**
     * Test that empty X-Registry-Name header does not override extracted name.
     * If the header is empty or whitespace-only, it should be ignored.
     */
    @Test
    public void testEmptyXRegistryNameIgnored() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "person-empty-name";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Create an artifact with empty X-Registry-Name header
        ArtifactContent body = new ArtifactContent();
        body.setContent(AVRO_SCHEMA);
        ArtifactMetaData amd = clientV2.groups().byGroupId(groupId).artifacts().post(body, config -> {
            config.headers.put("X-Registry-ArtifactId", Set.of(artifactId));
            config.headers.put("X-Registry-ArtifactType", Set.of(ArtifactType.AVRO));
            config.headers.put("X-Registry-Name", Set.of("   ")); // Empty/whitespace
        });

        // The name should be extracted from content (the record name "person")
        // Not the empty string from the header
        Assertions.assertNotNull(amd.getName());
        Assertions.assertNotEquals("   ", amd.getName());
        Assertions.assertEquals("person", amd.getName(),
            "When X-Registry-Name is empty, name should be extracted from content");
    }
}
