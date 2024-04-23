package io.apicurio.registry.noprofile;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class VersionStateTest extends AbstractResourceTestBase {

    private static final EditableVersionMetaData toEditableVersionMetaData(VersionState state) {
        EditableVersionMetaData evmd = new EditableVersionMetaData();
        evmd.setState(state);
        return evmd;
    }

    @Test
    public void testSmoke() throws Exception {
        String groupId = "VersionStateTest_testSmoke";
        String artifactId = generateArtifactId();

        ArtifactContent content = new ArtifactContent();
        content.setContent("{\"type\": \"string\"}");
        clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
        });

        content.setContent("{\"type\": \"int\"}");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(content);

        content.setContent("{\"type\": \"float\"}");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(content);

        VersionMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
        Assertions.assertEquals("3", amd.getVersion());

        // disable latest
        
        EditableVersionMetaData evmd = toEditableVersionMetaData(VersionState.DISABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(amd.getVersion()).put(evmd);

        VersionMetaData tvmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("3").get();
        Assertions.assertEquals("3", tvmd.getVersion());
        Assertions.assertEquals(VersionState.DISABLED, tvmd.getState());

        // Latest artifact version (3) is disabled, this will return a previous version
        VersionMetaData tamd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
        Assertions.assertEquals("2", tamd.getVersion());
        Assertions.assertNull(tamd.getDescription());

        // cannot get a disabled artifact version *content*

        var exception = assertThrows(io.apicurio.registry.rest.client.models.Error.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("3").content().get();
        });
        Assertions.assertEquals(404, exception.getErrorCode());
        Assertions.assertEquals("VersionNotFoundException", exception.getName());

        // can update and get metadata for a disabled artifact, but must specify version
        EditableVersionMetaData emd = new EditableVersionMetaData();
        String description = "Testing artifact state";
        emd.setDescription(description);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("3").put(emd);

        {
            VersionMetaData innerAvmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("3").get();
            Assertions.assertEquals("3", innerAvmd.getVersion());
            Assertions.assertEquals(description, innerAvmd.getDescription());
        }

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("3").put(toEditableVersionMetaData(VersionState.DEPRECATED));

        tamd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
        Assertions.assertEquals("3", tamd.getVersion()); // should be back to v3
        Assertions.assertEquals(tamd.getDescription(), description);

        InputStream latestArtifact = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").content().get();
        Assertions.assertNotNull(latestArtifact);
        latestArtifact.close();
        InputStream version = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("2").content().get();
        Assertions.assertNotNull(version);
        version.close();

        {
            VersionMetaData innerAmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
            Assertions.assertEquals("3", innerAmd.getVersion());
            Assertions.assertEquals(description, innerAmd.getDescription());
        }

        // can revert back to enabled from deprecated
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("3").put(toEditableVersionMetaData(VersionState.ENABLED));

        {
            VersionMetaData innerAmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").get();
            Assertions.assertEquals("3", innerAmd.getVersion()); // should still be latest (aka 3)
            Assertions.assertEquals(description, innerAmd.getDescription());

            VersionMetaData innerVmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("1").get();
            Assertions.assertNull(innerVmd.getDescription());
        }
    }

}