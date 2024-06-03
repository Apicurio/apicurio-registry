package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class EmptyArtifactTest extends AbstractResourceTestBase {

    @Test
    public void testCreateEmptyArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.JSON);

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        ArtifactMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        Assertions.assertNotNull(amd);

        VersionSearchResults versions = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get();
        Assertions.assertNotNull(versions);
        Assertions.assertEquals(0, versions.getCount());
        Assertions.assertEquals(0, versions.getVersions().size());
    }

    @Test
    public void testCreateFirstVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.JSON);

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        CreateVersion createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(createVersion);

        VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1").get();
        Assertions.assertNotNull(vmd);
        Assertions.assertEquals("1", vmd.getVersion());

        vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
        Assertions.assertNotNull(vmd);
        Assertions.assertEquals("1", vmd.getVersion());
    }

    @Test
    public void testCreateFirstCustomVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.JSON);

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        CreateVersion createVersion = TestUtils.clientCreateVersion("{}", ContentTypes.APPLICATION_JSON);
        createVersion.setVersion("1.0");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(createVersion);

        VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1.0").get();
        Assertions.assertNotNull(vmd);
        Assertions.assertEquals("1.0", vmd.getVersion());

        vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
        Assertions.assertNotNull(vmd);
        Assertions.assertEquals("1.0", vmd.getVersion());
    }

}
