package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.AddVersionToBranch;
import io.apicurio.registry.rest.client.models.BranchMetaData;
import io.apicurio.registry.rest.client.models.BranchSearchResults;
import io.apicurio.registry.rest.client.models.CreateBranch;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.EditableBranchMetaData;
import io.apicurio.registry.rest.client.models.ReplaceBranchVersions;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
public class BranchesTest extends AbstractResourceTestBase {

    @Test
    public void testLatestBranch() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, "{}", ContentTypes.APPLICATION_JSON);

        BranchMetaData latest = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .branches().byBranchId("latest").get();
        Assertions.assertNotNull(latest);
        Assertions.assertEquals("latest", latest.getBranchId());
        Assertions.assertEquals(true, latest.getSystemDefined());

        VersionSearchResults versions = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).branches().byBranchId("latest").versions().get();
        Assertions.assertEquals(2, versions.getCount());
    }

    @Test
    public void testCreateBranch() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);

        CreateBranch createBranch = new CreateBranch();
        createBranch.setBranchId("1.x");
        createBranch.setDescription("Version 1.x");
        BranchMetaData branch = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .branches().post(createBranch);

        Assertions.assertNotNull(branch);
        Assertions.assertEquals(groupId, branch.getGroupId());
        Assertions.assertEquals(artifactId, branch.getArtifactId());
        Assertions.assertEquals("1.x", branch.getBranchId());
        Assertions.assertEquals("Version 1.x", branch.getDescription());
        Assertions.assertEquals(false, branch.getSystemDefined());
    }

    @Test
    public void testCreateBranchWithVersions() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, "{}", ContentTypes.APPLICATION_JSON);

        CreateBranch createBranch = new CreateBranch();
        createBranch.setBranchId("1.x");
        createBranch.setDescription("Version 1.x");
        createBranch.setVersions(List.of("1", "2"));
        BranchMetaData branch = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .branches().post(createBranch);

        Assertions.assertNotNull(branch);
        Assertions.assertEquals(groupId, branch.getGroupId());
        Assertions.assertEquals(artifactId, branch.getArtifactId());
        Assertions.assertEquals("1.x", branch.getBranchId());
        Assertions.assertEquals("Version 1.x", branch.getDescription());

        VersionSearchResults versions = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).branches().byBranchId("1.x").versions().get();
        Assertions.assertEquals(2, versions.getCount());

        // Try to create branch with versions that do not exist.
        Assertions.assertThrows(Exception.class, () -> {
            CreateBranch cb = new CreateBranch();
            cb.setBranchId("invalid");
            cb.setVersions(List.of("77", "99"));
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches().post(cb);
        });
    }

    @Test
    public void testGetBranch() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);

        // Create a branch
        CreateBranch createBranch = new CreateBranch();
        createBranch.setBranchId("1.x");
        createBranch.setDescription("Version 1.x");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        // Fetch that branch and assert
        BranchMetaData branch = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .branches().byBranchId("1.x").get();
        Assertions.assertNotNull(branch);
        Assertions.assertEquals(groupId, branch.getGroupId());
        Assertions.assertEquals(artifactId, branch.getArtifactId());
        Assertions.assertEquals("1.x", branch.getBranchId());
        Assertions.assertEquals("Version 1.x", branch.getDescription());

        // Get a branch that does not exist
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                    .byBranchId("invalid").get();
        });

        // Get a branch from an artifact that doesn't exist.
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("invalid").branches()
                    .byBranchId("1.x").get();
        });
    }

    @Test
    public void testGetBranches() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, "{}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, "{}", ContentTypes.APPLICATION_JSON);

        CreateBranch createBranch = new CreateBranch();
        createBranch.setBranchId("1.x");
        createBranch.setDescription("Version 1.x");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        createBranch = new CreateBranch();
        createBranch.setBranchId("2.x");
        createBranch.setDescription("Version 2.x");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        createBranch = new CreateBranch();
        createBranch.setBranchId("3.x");
        createBranch.setDescription("Version 3.x");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        BranchSearchResults results = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).branches().get();
        Assertions.assertNotNull(results);
        // There should be FOUR: latest, 1.x, 2.x, 3.x
        Assertions.assertEquals(4, results.getCount());

        Assertions.assertEquals("1.x", results.getBranches().get(0).getBranchId());
        Assertions.assertEquals("2.x", results.getBranches().get(1).getBranchId());
        Assertions.assertEquals("3.x", results.getBranches().get(2).getBranchId());
        Assertions.assertEquals("latest", results.getBranches().get(3).getBranchId());

        // Get a branch from an artifact that doesn't exist.
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("invalid").branches().get();
        });
    }

    @Test
    public void testUpdateBranch() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);

        // Create a branch
        CreateBranch createBranch = new CreateBranch();
        createBranch.setBranchId("1.x");
        createBranch.setDescription("Version 1.x");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        // Make sure it really exists
        BranchMetaData branch = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .branches().byBranchId("1.x").get();
        Assertions.assertNotNull(branch);
        Assertions.assertEquals(groupId, branch.getGroupId());
        Assertions.assertEquals(artifactId, branch.getArtifactId());
        Assertions.assertEquals("1.x", branch.getBranchId());
        Assertions.assertEquals("Version 1.x", branch.getDescription());

        // Update it
        EditableBranchMetaData update = new EditableBranchMetaData();
        update.setDescription("Updated version 1.x branch.");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches().byBranchId("1.x")
                .put(update);

        // Check it now
        branch = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("1.x").get();
        Assertions.assertNotNull(branch);
        Assertions.assertEquals(groupId, branch.getGroupId());
        Assertions.assertEquals(artifactId, branch.getArtifactId());
        Assertions.assertEquals("Updated version 1.x branch.", branch.getDescription());

        // Update a branch that does not exist
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                    .byBranchId("invalid").put(update);
        });

        // Update a branch in an artifact that does not exist
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("invalid").branches()
                    .byBranchId("1.x").put(update);
        });
    }

    @Test
    public void testDeleteBranch() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, "{}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, "{}", ContentTypes.APPLICATION_JSON);

        // Create a branch
        CreateBranch createBranch = new CreateBranch();
        createBranch.setBranchId("1.x");
        createBranch.setDescription("Version 1.x");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        // Create a branch
        createBranch = new CreateBranch();
        createBranch.setBranchId("2.x");
        createBranch.setDescription("Version 2.x");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        // Create a branch
        createBranch = new CreateBranch();
        createBranch.setBranchId("3.x");
        createBranch.setDescription("Version 3.x");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        var results = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .get();
        Assertions.assertEquals(4, results.getCount());

        var bmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("2.x").get();
        Assertions.assertEquals("2.x", bmd.getBranchId());

        // Now delete branch 2.x
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches().byBranchId("2.x")
                .delete();

        // Assert that it's gone
        results = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches().get();
        Assertions.assertEquals(3, results.getCount());

        // Try to get the branch that was deleted
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                    .byBranchId("2.x").get();
        });

        // Try to delete a branch that does not exist
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                    .byBranchId("invalid").delete();
        });

        // Try to delete a branch of an artifact that does not exist
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("invalid").branches()
                    .byBranchId("2.x").delete();
        });
    }

    @Test
    public void testGetVersionsInBranch() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create artifact
        createArtifact(groupId, artifactId, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);
        // Create v2
        CreateVersion createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v2");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);
        // Create v3
        createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v3");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);
        // Create v4
        createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v4");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);
        // Create v5
        createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v5");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);

        // Create a branch
        CreateBranch createBranch = new CreateBranch();
        createBranch.setBranchId("2-3");
        createBranch.setDescription("Contains versions 2, 3");
        createBranch.setVersions(List.of("2", "3"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        // Create a branch
        createBranch = new CreateBranch();
        createBranch.setBranchId("2-4");
        createBranch.setDescription("Contains versions 2, 3, 4");
        createBranch.setVersions(List.of("2", "3", "4"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        // Create a branch
        createBranch = new CreateBranch();
        createBranch.setBranchId("5");
        createBranch.setDescription("Contains versions 2, 3, 4");
        createBranch.setVersions(List.of("5"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        // Now make sure the branches contain the versions we think.
        var results = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("2-3").versions().get();
        Assertions.assertEquals(2, results.getCount());
        Assertions.assertEquals("v3", results.getVersions().get(0).getName());
        Assertions.assertEquals("v2", results.getVersions().get(1).getName());

        results = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("2-4").versions().get();
        Assertions.assertEquals(3, results.getCount());
        Assertions.assertEquals("v4", results.getVersions().get(0).getName());
        Assertions.assertEquals("v3", results.getVersions().get(1).getName());
        Assertions.assertEquals("v2", results.getVersions().get(2).getName());

        results = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("5").versions().get();
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("v5", results.getVersions().get(0).getName());

        // Get versions in a branch that does not exist.
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                    .byBranchId("invalid").versions().get();
        });

        // Get versions in a branch of an artifact that does not exist
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("invalid").branches()
                    .byBranchId("2-4").versions().get();
        });
    }

    @Test
    public void testReplaceBranchVersions() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create artifact
        createArtifact(groupId, artifactId, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON, req -> {
            req.getFirstVersion().setName("v1");
        });
        // Create v2
        CreateVersion createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v2");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);
        // Create v3
        createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v3");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);
        // Create v4
        createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v4");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);
        // Create v5
        createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v5");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);

        // Create a branch
        CreateBranch createBranch = new CreateBranch();
        createBranch.setBranchId("test-branch");
        createBranch.setVersions(List.of("2", "3", "4"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        // Make sure the branch has 2,3,4 on it.
        var results = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("test-branch").versions().get();
        Assertions.assertEquals(3, results.getCount());
        Assertions.assertEquals("v4", results.getVersions().get(0).getName());
        Assertions.assertEquals("v3", results.getVersions().get(1).getName());
        Assertions.assertEquals("v2", results.getVersions().get(2).getName());

        // Now replace the versions on the branch
        ReplaceBranchVersions newVersions = replaceVersions("1", "3", "5");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("test-branch").versions().put(newVersions);

        // Make sure the branch now has 1,3,5 on it.
        results = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("test-branch").versions().get();
        Assertions.assertEquals(3, results.getCount());
        Assertions.assertEquals("v5", results.getVersions().get(0).getName());
        Assertions.assertEquals("v3", results.getVersions().get(1).getName());
        Assertions.assertEquals("v1", results.getVersions().get(2).getName());

        // Replace versions in a branch that does not exist
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                    .byBranchId("invalid-branch").versions().put(newVersions);
        });

        // Replace versions in a branch of an artifact that does not exist
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("invalid").branches()
                    .byBranchId("test-branch").versions().put(newVersions);
        });

    }

    @Test
    public void testAppendVersionToBranch() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create artifact
        createArtifact(groupId, artifactId, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON, req -> {
            req.getFirstVersion().setName("v1");
        });
        // Create v2
        CreateVersion createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v2");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);
        // Create v3
        createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v3");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);
        // Create v4
        createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v4");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);
        // Create v5
        createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v5");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);

        // Create a branch
        CreateBranch createBranch = new CreateBranch();
        createBranch.setBranchId("1-4");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        // Create a branch
        createBranch = new CreateBranch();
        createBranch.setBranchId("2-3");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        // Create a branch
        createBranch = new CreateBranch();
        createBranch.setBranchId("3-5");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        // Append some versions to the branches
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches().byBranchId("1-4")
                .versions().post(addVersion("1"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches().byBranchId("1-4")
                .versions().post(addVersion("2"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches().byBranchId("1-4")
                .versions().post(addVersion("3"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches().byBranchId("1-4")
                .versions().post(addVersion("4"));

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches().byBranchId("2-3")
                .versions().post(addVersion("2"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches().byBranchId("2-3")
                .versions().post(addVersion("3"));

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches().byBranchId("3-5")
                .versions().post(addVersion("3"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches().byBranchId("3-5")
                .versions().post(addVersion("4"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches().byBranchId("3-5")
                .versions().post(addVersion("5"));

        // Check the results - make sure the versions are on the branches
        var results = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("1-4").versions().get();
        Assertions.assertEquals(4, results.getCount());
        Assertions.assertEquals("v4", results.getVersions().get(0).getName());
        Assertions.assertEquals("v3", results.getVersions().get(1).getName());
        Assertions.assertEquals("v2", results.getVersions().get(2).getName());
        Assertions.assertEquals("v1", results.getVersions().get(3).getName());

        results = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("2-3").versions().get();
        Assertions.assertEquals(2, results.getCount());
        Assertions.assertEquals("v3", results.getVersions().get(0).getName());
        Assertions.assertEquals("v2", results.getVersions().get(1).getName());

        results = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("3-5").versions().get();
        Assertions.assertEquals(3, results.getCount());
        Assertions.assertEquals("v5", results.getVersions().get(0).getName());
        Assertions.assertEquals("v4", results.getVersions().get(1).getName());
        Assertions.assertEquals("v3", results.getVersions().get(2).getName());

        // Append a version to a branch that does not exist
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                    .byBranchId("invalid").versions().post(addVersion("3"));
        });

        // Append a version to a branch of an artifact that does not exist
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("invalid").branches()
                    .byBranchId("2-3").versions().post(addVersion("3"));
        });
    }

    @Test
    public void testGetMostRecentVersionFromBranch() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create artifact
        createArtifact(groupId, artifactId, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON, req -> {
            req.getFirstVersion().setName("v1");
        });
        // Create v2
        CreateVersion createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v2");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);
        // Create v3
        createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v3");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);
        // Create v4
        createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v4");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);
        // Create v5
        createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v5");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);
        // Create v6
        createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setName("v6");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(createVersion);

        // Create a branch
        CreateBranch createBranch = new CreateBranch();
        createBranch.setBranchId("evens");
        createBranch.setVersions(List.of("2", "4"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        // Create a branch
        createBranch = new CreateBranch();
        createBranch.setBranchId("odds");
        createBranch.setVersions(List.of("1", "3"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        // Get the most recent version from each branch
        VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("branch=evens").get();
        Assertions.assertEquals("v4", vmd.getName());
        vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("branch=odds").get();
        Assertions.assertEquals("v3", vmd.getName());
        vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("branch=latest").get();
        Assertions.assertEquals("v6", vmd.getName());

        // Append versions to the branches.
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("evens").versions().post(addVersion("6"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .byBranchId("odds").versions().post(addVersion("5"));

        // Get the most recent version from each branch
        vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("branch=evens").get();
        Assertions.assertEquals("v6", vmd.getName());
        vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("branch=odds").get();
        Assertions.assertEquals("v5", vmd.getName());
        vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("branch=latest").get();
        Assertions.assertEquals("v6", vmd.getName());

        // Get the most recent version from a branch that does not exist
        Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .byVersionExpression("branch=invalid").get();
        });
    }

    private static AddVersionToBranch addVersion(String version) {
        AddVersionToBranch rval = new AddVersionToBranch();
        rval.setVersion(version);
        return rval;
    }

    private static ReplaceBranchVersions replaceVersions(String... versions) {
        ReplaceBranchVersions rval = new ReplaceBranchVersions();
        rval.setVersions(List.of(versions));
        return rval;
    }

}
