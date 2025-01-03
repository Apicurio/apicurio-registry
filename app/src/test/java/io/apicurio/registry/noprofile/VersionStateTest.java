package io.apicurio.registry.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
public class VersionStateTest extends AbstractResourceTestBase {

    @Test
    public void testSmoke() throws Exception {
        String groupId = "VersionStateTest_testSmoke";
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{\"type\": \"string\"}",
                ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, "{\"type\": \"int\"}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, "{\"type\": \"float\"}", ContentTypes.APPLICATION_JSON);

        VersionMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("branch=latest").get();
        Assertions.assertEquals("3", amd.getVersion());

        // disable latest

        WrappedVersionState vs = new WrappedVersionState();
        vs.setState(VersionState.DISABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression(amd.getVersion()).state().put(vs);

        VersionMetaData tvmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("3").get();
        Assertions.assertEquals("3", tvmd.getVersion());
        Assertions.assertEquals(VersionState.DISABLED, tvmd.getState());

        // Latest artifact version (3) is disabled, this will return a previous version
        VersionMetaData tamd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("branch=latest").get();
        Assertions.assertEquals("2", tamd.getVersion());
        Assertions.assertNull(tamd.getDescription());

        // cannot get a disabled artifact version *content*

        var exception = assertThrows(io.apicurio.registry.rest.client.models.ProblemDetails.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .byVersionExpression("3").content().get();
        });
        Assertions.assertEquals(404, exception.getStatus());
        Assertions.assertEquals("VersionNotFoundException", exception.getName());

        // can update and get metadata for a disabled artifact, but must specify version
        EditableVersionMetaData emd = new EditableVersionMetaData();
        String description = "Testing artifact state";
        emd.setDescription(description);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("3").put(emd);

        {
            VersionMetaData innerAvmd = clientV3.groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactId).versions().byVersionExpression("3").get();
            Assertions.assertEquals("3", innerAvmd.getVersion());
            Assertions.assertEquals(description, innerAvmd.getDescription());
        }

        vs = new WrappedVersionState();
        vs.setState(VersionState.DEPRECATED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("3").state().put(vs);

        tamd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("branch=latest").get();
        Assertions.assertEquals("3", tamd.getVersion()); // should be back to v3
        Assertions.assertEquals(tamd.getDescription(), description);

        InputStream latestArtifact = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("branch=latest").content().get();
        Assertions.assertNotNull(latestArtifact);
        latestArtifact.close();
        InputStream version = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("2").content().get();
        Assertions.assertNotNull(version);
        version.close();

        {
            VersionMetaData innerAmd = clientV3.groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
            Assertions.assertEquals("3", innerAmd.getVersion());
            Assertions.assertEquals(description, innerAmd.getDescription());
        }

        // can revert back to enabled from deprecated
        vs = new WrappedVersionState();
        vs.setState(VersionState.ENABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("3").state().put(vs);

        {
            VersionMetaData innerAmd = clientV3.groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
            Assertions.assertEquals("3", innerAmd.getVersion()); // should still be latest (aka 3)
            Assertions.assertEquals(description, innerAmd.getDescription());

            VersionMetaData innerVmd = clientV3.groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactId).versions().byVersionExpression("1").get();
            Assertions.assertNull(innerVmd.getDescription());
        }

        // cannot change to DRAFT (not allowed)
        Assertions.assertThrows(io.apicurio.registry.rest.client.models.ProblemDetails.class, () -> {
            WrappedVersionState vstate = new WrappedVersionState();
            vstate.setState(VersionState.DRAFT);
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .byVersionExpression("3").state().put(vstate);
        });
    }

}