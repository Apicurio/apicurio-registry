/*
 * Copyright 2022 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.client.exception.RestClientException;
import io.apicurio.registry.rest.client.exception.VersionNotFoundException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.rest.v2.beans.UpdateState;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static io.apicurio.registry.utils.tests.TestUtils.assertClientError;
import static io.apicurio.registry.utils.tests.TestUtils.retry;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Ales Justin
 */
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

        clientV2.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{\"type\": \"string\"}".getBytes(StandardCharsets.UTF_8)));

        clientV2.updateArtifact(
                groupId,
                artifactId,
                new ByteArrayInputStream("\"type\": \"int\"".getBytes(StandardCharsets.UTF_8))
        );

        clientV2.updateArtifact(
                groupId,
                artifactId,
                new ByteArrayInputStream("\"type\": \"float\"".getBytes(StandardCharsets.UTF_8))
        );

        ArtifactMetaData amd = clientV2.getArtifactMetaData(groupId, artifactId);
        Assertions.assertEquals("3", amd.getVersion());

        // disable latest
        clientV2.updateArtifactState(groupId, artifactId, toUpdateState(ArtifactState.DISABLED));

        VersionMetaData tvmd = clientV2.getArtifactVersionMetaData(groupId, artifactId, "3");
        Assertions.assertEquals("3", tvmd.getVersion());
        Assertions.assertEquals(ArtifactState.DISABLED, tvmd.getState());

        // Latest artifact version (3) is disabled, this will return a previous version
        ArtifactMetaData tamd = clientV2.getArtifactMetaData(groupId, artifactId);
        Assertions.assertEquals("2", tamd.getVersion());
        Assertions.assertEquals(ArtifactState.ENABLED, tamd.getState());
        Assertions.assertNull(tamd.getDescription());

        EditableMetaData emd = new EditableMetaData();
        String description = "Testing artifact state";
        emd.setDescription(description);

        // cannot get a disabled artifact version
        Function<Exception, Integer> errorCodeExtractor = (e) -> {
            return ((RestClientException) e).getError().getErrorCode();
        };
        assertClientError(VersionNotFoundException.class.getSimpleName(), 404, () -> clientV2.getArtifactVersion(groupId, artifactId, "3"), errorCodeExtractor);

        // can update and get metadata for a disabled artifact, but must specify version
        clientV2.updateArtifactVersionMetaData(groupId, artifactId, "3", emd);

        retry(() -> {
            VersionMetaData innerAvmd = clientV2.getArtifactVersionMetaData(groupId, artifactId, "3");
            Assertions.assertEquals("3", innerAvmd.getVersion());
            Assertions.assertEquals(description, innerAvmd.getDescription());
            return null;
        });

        clientV2.updateArtifactVersionState(groupId, artifactId, "3", toUpdateState(ArtifactState.DEPRECATED));

        tamd = clientV2.getArtifactMetaData(groupId, artifactId);
        Assertions.assertEquals("3", tamd.getVersion()); // should be back to v3
        Assertions.assertEquals(ArtifactState.DEPRECATED, tamd.getState());
        Assertions.assertEquals(tamd.getDescription(), description);

        InputStream latestArtifact = clientV2.getLatestArtifact(groupId, artifactId);
        Assertions.assertNotNull(latestArtifact);
        latestArtifact.close();
        InputStream version = clientV2.getArtifactVersion(groupId, artifactId, "2");
        Assertions.assertNotNull(version);
        version.close();

        clientV2.updateArtifactMetaData(groupId, artifactId, emd); // should be allowed for deprecated

        retry(() -> {
            ArtifactMetaData innerAmd = clientV2.getArtifactMetaData(groupId, artifactId);
            Assertions.assertEquals("3", innerAmd.getVersion());
            Assertions.assertEquals(description, innerAmd.getDescription());
            Assertions.assertEquals(ArtifactState.DEPRECATED, innerAmd.getState());
            return null;
        });

        // can revert back to enabled from deprecated
        clientV2.updateArtifactVersionState(groupId, artifactId, "3", toUpdateState(ArtifactState.ENABLED));

        retry(() -> {
            ArtifactMetaData innerAmd = clientV2.getArtifactMetaData(groupId, artifactId);
            Assertions.assertEquals("3", innerAmd.getVersion()); // should still be latest (aka 3)
            Assertions.assertEquals(description, innerAmd.getDescription());

            VersionMetaData innerVmd = clientV2.getArtifactVersionMetaData(groupId, artifactId, "1");
            Assertions.assertNull(innerVmd.getDescription());

            return null;
        });
    }

    @Test
    void testEnableDisableArtifact() throws Exception {
        String groupId = "ArtifactStateTest_testEnableDisableArtifact";
        String artifactId = generateArtifactId();

        // Create the artifact
        ArtifactMetaData md = clientV2.createArtifact(
                groupId,
                artifactId,
                ArtifactType.JSON,
                new ByteArrayInputStream("{\"type\": \"string\"}".getBytes(StandardCharsets.UTF_8))
        );

        retry(() -> {
            // Get the meta-data
            ArtifactMetaData actualMD = clientV2.getArtifactMetaData(groupId, artifactId);
            assertEquals(md.getGlobalId(), actualMD.getGlobalId());
        });

        // Set to disabled
        UpdateState state = new UpdateState();
        state.setState(ArtifactState.DISABLED);
        clientV2.updateArtifactState(groupId, artifactId, state);
        retry(() -> {
            given()
                    .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", groupId)
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                    .then()
                    .statusCode(404);
        });

        retry(() -> {
            // Get the latest meta-data again - should not be accessible because it's DISABLED
            // and there is only a single version.
            try {
                clientV2.getArtifactMetaData(groupId, artifactId);
                Assertions.fail("ArtifactNotFoundException expected");
            } catch (ArtifactNotFoundException ex) {
                // OK
            }

            // Get the specific version meta-data - should be accessible even though it's DISABLED.
            VersionMetaData vmd = clientV2.getArtifactVersionMetaData(groupId, artifactId, String.valueOf(md.getVersion()));
            Assertions.assertEquals(ArtifactState.DISABLED, vmd.getState());
        });

        // Now re-enable the artifact
        state.setState(ArtifactState.ENABLED);
        clientV2.updateArtifactVersionState(groupId, artifactId, md.getVersion(), state);

        // Get the meta-data
        // Should be accessible now
        ArtifactMetaData amd = clientV2.getArtifactMetaData(groupId, artifactId);
        Assertions.assertEquals(ArtifactState.ENABLED, amd.getState());
        VersionMetaData vmd = clientV2.getArtifactVersionMetaData(groupId, artifactId, String.valueOf(md.getVersion()));
        Assertions.assertEquals(ArtifactState.ENABLED, vmd.getState());
    }

    @Test
    void testDeprecateDisableArtifact() throws Exception {
        String groupId = "ArtifactStateTest_testDeprecateDisableArtifact";
        String artifactId = generateArtifactId();

        // Create the artifact
        ArtifactMetaData md = clientV2.createArtifact(
                groupId,
                artifactId,
                ArtifactType.JSON,
                new ByteArrayInputStream("{\"type\": \"string\"}".getBytes(StandardCharsets.UTF_8))
        );

        retry(() -> {
            // Get the meta-data
            ArtifactMetaData actualMD = clientV2.getArtifactMetaData(groupId, artifactId);
            assertEquals(md.getGlobalId(), actualMD.getGlobalId());
        });

        // Set to deprecated
        UpdateState state = new UpdateState();
        state.setState(ArtifactState.DEPRECATED);
        clientV2.updateArtifactState(groupId, artifactId, state);

        retry(() -> {
            // Get the meta-data again - should be DEPRECATED
            ArtifactMetaData actualMD = clientV2.getArtifactMetaData(groupId, artifactId);
            assertEquals(md.getGlobalId(), actualMD.getGlobalId());
            Assertions.assertEquals(ArtifactState.DEPRECATED, actualMD.getState());
        });

        // Set to disabled
        state.setState(ArtifactState.DISABLED);
        clientV2.updateArtifactState(groupId, artifactId, state);
        retry(() -> {
            given()
                    .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", groupId)
                    .pathParam("artifactId", artifactId)
                    .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                    .then()
                    .statusCode(404);
        });

        retry(() -> {
            // Get the latest meta-data again - should not be accessible because it's DISABLED
            // and there is only a single version.
            try {
                clientV2.getArtifactMetaData(groupId, artifactId);
                Assertions.fail("ArtifactNotFoundException expected");
            } catch (ArtifactNotFoundException ex) {
                // OK
            }

            // Get the specific version meta-data - should be accessible even though it's DISABLED.
            VersionMetaData vmd = clientV2.getArtifactVersionMetaData(groupId, artifactId, String.valueOf(md.getVersion()));
            Assertions.assertEquals(ArtifactState.DISABLED, vmd.getState());
        });
    }

}