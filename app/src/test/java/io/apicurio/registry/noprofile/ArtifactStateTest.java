package io.apicurio.registry.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactState;
import io.apicurio.registry.rest.client.models.EditableMetaData;
import io.apicurio.registry.rest.client.models.UpdateState;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.io.InputStream;

import static io.apicurio.registry.utils.tests.TestUtils.retry;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class ArtifactStateTest extends AbstractResourceTestBase {

    private static final UpdateState toUpdateState(ArtifactState state) {
        UpdateState us = new UpdateState();
        us.setState(state);
        return us;
    }

    @Test
    public void testSmoke() throws Exception {
        String groupId = "ArtifactStateTest_testSmoke";
        String artifactId = generateArtifactId();

        ArtifactContent content = new ArtifactContent();
        content.setContent("{\"type\": \"string\"}");
        clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
        });

        content.setContent("{\"type\": \"int\"}");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(content);

        content.setContent("{\"type\": \"float\"}");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(content);

        ArtifactMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get();
        Assertions.assertEquals("3", amd.getVersion());

        // disable latest
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).state().put(toUpdateState(ArtifactState.DISABLED));

        VersionMetaData tvmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion("3").meta().get();
        Assertions.assertEquals("3", tvmd.getVersion());
        Assertions.assertEquals(ArtifactState.DISABLED, tvmd.getState());

        // Latest artifact version (3) is disabled, this will return a previous version
        ArtifactMetaData tamd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get();
        Assertions.assertEquals("2", tamd.getVersion());
        Assertions.assertEquals(ArtifactState.ENABLED, tamd.getState());
        Assertions.assertNull(tamd.getDescription());

        EditableMetaData emd = new EditableMetaData();
        String description = "Testing artifact state";
        emd.setDescription(description);

        // cannot get a disabled artifact version

        var exception = assertThrows(io.apicurio.registry.rest.client.models.Error.class, () -> {
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion("3").get();
        });
        Assertions.assertEquals(404, exception.getErrorCode());
        Assertions.assertEquals("VersionNotFoundException", exception.getName());

        // can update and get metadata for a disabled artifact, but must specify version
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion("3").meta().put(emd);

        retry(() -> {
            VersionMetaData innerAvmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion("3").meta().get();
            Assertions.assertEquals("3", innerAvmd.getVersion());
            Assertions.assertEquals(description, innerAvmd.getDescription());
            return null;
        });

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion("3").state().put(toUpdateState(ArtifactState.DEPRECATED));

        tamd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get();
        Assertions.assertEquals("3", tamd.getVersion()); // should be back to v3
        Assertions.assertEquals(ArtifactState.DEPRECATED, tamd.getState());
        Assertions.assertEquals(tamd.getDescription(), description);

        InputStream latestArtifact = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        Assertions.assertNotNull(latestArtifact);
        latestArtifact.close();
        InputStream version = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion("2").get();
        Assertions.assertNotNull(version);
        version.close();

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().put(emd); // should be allowed for deprecated

        retry(() -> {
            ArtifactMetaData innerAmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get();
            Assertions.assertEquals("3", innerAmd.getVersion());
            Assertions.assertEquals(description, innerAmd.getDescription());
            Assertions.assertEquals(ArtifactState.DEPRECATED, innerAmd.getState());
            return null;
        });

        // can revert back to enabled from deprecated
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion("3").state().put(toUpdateState(ArtifactState.ENABLED));

        retry(() -> {
            ArtifactMetaData innerAmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get();
            Assertions.assertEquals("3", innerAmd.getVersion()); // should still be latest (aka 3)
            Assertions.assertEquals(description, innerAmd.getDescription());

            VersionMetaData innerVmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion("1").meta().get();
            Assertions.assertNull(innerVmd.getDescription());

            return null;
        });
    }

    @Test
    void testEnableDisableArtifact() throws Exception {
        String groupId = "ArtifactStateTest_testEnableDisableArtifact";
        String artifactId = generateArtifactId();

        // Create the artifact
        ArtifactContent content = new ArtifactContent();
        content.setContent("{\"type\": \"string\"}");
        ArtifactMetaData md = clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
        });

        retry(() -> {
            // Get the meta-data
            ArtifactMetaData actualMD = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get();
            assertEquals(md.getGlobalId(), actualMD.getGlobalId());
        });

        // Set to disabled
        UpdateState state = new UpdateState();
        state.setState(ArtifactState.DISABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).state().put(state);
        retry(() -> {
            given()
                    .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", groupId)
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/meta")
                    .then()
                    .statusCode(404);
        });

        retry(() -> {
            // Get the latest meta-data again - should not be accessible because it's DISABLED
            // and there is only a single version.
            try {
                clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
                Assertions.fail("ArtifactNotFoundException expected");
            } catch (io.apicurio.registry.rest.client.models.Error ex) {
                assertEquals("ArtifactNotFoundException", ex.getName());
                // OK
            }

            // Get the specific version meta-data - should be accessible even though it's DISABLED.
            VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(md.getVersion())).meta().get();
            Assertions.assertEquals(ArtifactState.DISABLED, vmd.getState());
        });

        // Now re-enable the artifact
        state.setState(ArtifactState.ENABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(md.getVersion())).state().put(state);

        // Get the meta-data
        // Should be accessible now
        ArtifactMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get();
        Assertions.assertEquals(ArtifactState.ENABLED, amd.getState());
        VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(md.getVersion())).meta().get();
        Assertions.assertEquals(ArtifactState.ENABLED, vmd.getState());
    }

    @Test
    void testDeprecateDisableArtifact() throws Exception {
        String groupId = "ArtifactStateTest_testDeprecateDisableArtifact";
        String artifactId = generateArtifactId();

        // Create the artifact
        ArtifactContent content = new ArtifactContent();
        content.setContent("{\"type\": \"string\"}");
        ArtifactMetaData md = clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
        });

        retry(() -> {
            // Get the meta-data
            ArtifactMetaData actualMD = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get();
            assertEquals(md.getGlobalId(), actualMD.getGlobalId());
        });

        // Set to deprecated
        UpdateState state = new UpdateState();
        state.setState(ArtifactState.DEPRECATED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).state().put(state);

        retry(() -> {
            // Get the meta-data again - should be DEPRECATED
            ArtifactMetaData actualMD = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get();
            assertEquals(md.getGlobalId(), actualMD.getGlobalId());
            Assertions.assertEquals(ArtifactState.DEPRECATED, actualMD.getState());
        });

        // Set to disabled
        state.setState(ArtifactState.DISABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).state().put(state);
        retry(() -> {
            given()
                    .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", groupId)
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/meta")
                    .then()
                    .statusCode(404);
        });

        retry(() -> {
            // Get the latest meta-data again - should not be accessible because it's DISABLED
            // and there is only a single version.
            try {
                clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get();
                Assertions.fail("ArtifactNotFoundException expected");
            } catch (io.apicurio.registry.rest.client.models.Error ex) {
                assertEquals("ArtifactNotFoundException", ex.getName());
                // OK
            }

            // Get the specific version meta-data - should be accessible even though it's DISABLED.
            VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(String.valueOf(md.getVersion())).meta().get();
            Assertions.assertEquals(ArtifactState.DISABLED, vmd.getState());
        });
    }

}