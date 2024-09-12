package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.BranchMetaData;
import io.apicurio.registry.rest.client.models.BranchSearchResults;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.SemanticVersioningProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(SemanticVersioningProfile.class)
public class SemVerBranchesTest extends AbstractResourceTestBase {

    @Test
    public void testSemVerBranches() throws Exception {
        String groupId = "SemVerBranchesTest";
        String artifactId = "testSemVerBranches";

        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        createSemVerArtifact(groupId, artifactId);
        createSemVerVersion(groupId, artifactId, "1.0.1");
        createSemVerVersion(groupId, artifactId, "1.0.2");
        createSemVerVersion(groupId, artifactId, "1.0.3");
        createSemVerVersion(groupId, artifactId, "1.1.0");
        createSemVerVersion(groupId, artifactId, "1.1.1");
        createSemVerVersion(groupId, artifactId, "2.5.1");
        createSemVerVersion(groupId, artifactId, "2.5.2");
        createSemVerVersion(groupId, artifactId, "3.0.0");

        BranchSearchResults results = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).branches().get();
        // The following branches should have been automatically created:
        // latest, 1.x, 2.x, 3.x, 1.0.x, 1.1.x, 2.5.x, 3.0.x
        Assertions.assertEquals(8, results.getCount());

        validateSemVerBranch(groupId, artifactId, "latest", 9, "3.0.0");
        validateSemVerBranch(groupId, artifactId, "1.x", 6, "1.1.1");
        validateSemVerBranch(groupId, artifactId, "1.0.x", 4, "1.0.3");
        validateSemVerBranch(groupId, artifactId, "2.x", 2, "2.5.2");
        validateSemVerBranch(groupId, artifactId, "2.5.x", 2, "2.5.2");
        validateSemVerBranch(groupId, artifactId, "3.x", 1, "3.0.0");
        validateSemVerBranch(groupId, artifactId, "3.0.x", 1, "3.0.0");

        // 4.x does not exist.
        ProblemDetails error = Assertions.assertThrows(ProblemDetails.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                    .byBranchId("4.x").versions().get();
        });
        Assertions.assertEquals("No branch '4.x' was found in SemVerBranchesTest/testSemVerBranches.",
                error.getTitle());
    }

    private void validateSemVerBranch(String groupId, String artifactId, String branchId,
            int expectedVersionCount, String expectedLatestVersion) {
        BranchMetaData bmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .branches().byBranchId(branchId).get();
        Assertions.assertNotNull(bmd);
        Assertions.assertEquals(branchId, bmd.getBranchId());
        Assertions.assertEquals(Boolean.TRUE, bmd.getSystemDefined());

        VersionSearchResults versions = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).branches().byBranchId(branchId).versions().get();
        Assertions.assertEquals(expectedVersionCount, versions.getCount());
        Assertions.assertEquals(expectedLatestVersion, versions.getVersions().get(0).getVersion());

        ProblemDetails error = Assertions.assertThrows(ProblemDetails.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                    .byBranchId(branchId).delete();
        });
        Assertions.assertEquals("System generated branches cannot be deleted.", error.getTitle());
    }

    private void createSemVerArtifact(String groupId, String artifactId) {
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO, "{}",
                ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setVersion("1.0.0");
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }

    private void createSemVerVersion(String groupId, String artifactId, String version) throws Exception {
        CreateVersion cv = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        cv.setVersion(version);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(cv);
    }

}
