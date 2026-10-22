package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(DisableAutomaticGroupCreationProfile.class)
public class DisableAutomaticGroupCreationTest extends AbstractResourceTestBase {

    @Test
    public void testGroupCreation() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Try to create an artifact in a group that does not exist.
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType("JSON");
        createArtifact.setFirstVersion(new CreateVersion());
        createArtifact.getFirstVersion().setVersion("1.0");
        createArtifact.getFirstVersion().setContent(new VersionContent());
        createArtifact.getFirstVersion().getContent().setContentType(ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().getContent().setContent("{}");
        Exception e = Assertions.assertThrows(Exception.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
        });
        assertNotFound(e);

        // Now create the group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        GroupMetaData gmd = clientV3.groups().byGroupId(groupId).get();
        Assertions.assertNotNull(gmd);
        Assertions.assertEquals(groupId, gmd.getGroupId());

        // Now that the group exists, it's OK to create the artifact.
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }

}
