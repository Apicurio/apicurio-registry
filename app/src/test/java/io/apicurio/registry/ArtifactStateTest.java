/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry;

import static io.apicurio.registry.utils.tests.TestUtils.assertWebError;
import static io.apicurio.registry.utils.tests.TestUtils.retry;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Assertions;

import io.apicurio.registry.service.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.UpdateState;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.tests.RegistryServiceTest;
import io.quarkus.test.junit.QuarkusTest;

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

    @RegistryServiceTest
    public void testSmoke(Supplier<RegistryService> supplier) throws Exception {
        RegistryService service = supplier.get();
        String artifactId = generateArtifactId();

        CompletionStage<ArtifactMetaData> a1 = service.createArtifact(
                ArtifactType.JSON,
                artifactId,
                null,
                new ByteArrayInputStream("{\"type\": \"string\"}".getBytes(StandardCharsets.UTF_8))
        );
        ArtifactMetaData amd1 = ConcurrentUtil.result(a1);

        this.waitForGlobalId(amd1.getGlobalId());

        CompletionStage<ArtifactMetaData> a2 = service.updateArtifact(
                artifactId,
                ArtifactType.JSON,
                new ByteArrayInputStream("\"type\": \"int\"".getBytes(StandardCharsets.UTF_8))
        );
        ArtifactMetaData amd2 = ConcurrentUtil.result(a2);
        this.waitForGlobalId(amd2.getGlobalId());

        CompletionStage<ArtifactMetaData> a3 = service.updateArtifact(
                artifactId,
                ArtifactType.JSON,
                new ByteArrayInputStream("\"type\": \"float\"".getBytes(StandardCharsets.UTF_8))
        );
        ArtifactMetaData amd3 = ConcurrentUtil.result(a3);
        this.waitForGlobalId(amd3.getGlobalId());

        ArtifactMetaData amd = service.getArtifactMetaData(artifactId);
        Assertions.assertEquals(3, amd.getVersion());

        // disable latest
        service.updateArtifactState(artifactId, toUpdateState(ArtifactState.DISABLED));
        this.waitForVersionState(artifactId, 3, ArtifactState.DISABLED);

        VersionMetaData tvmd = service.getArtifactVersionMetaData(3, artifactId);
        Assertions.assertEquals(3, tvmd.getVersion());
        Assertions.assertEquals(ArtifactState.DISABLED, tvmd.getState());

        ArtifactMetaData tamd = service.getArtifactMetaData(artifactId);
        Assertions.assertEquals(2, tamd.getVersion()); // should still be latest active (aka 2)
        Assertions.assertEquals(ArtifactState.ENABLED, tamd.getState());
        Assertions.assertNull(tamd.getDescription());

        EditableMetaData emd = new EditableMetaData();
        String description = "Testing artifact state";
        emd.setDescription(description);

        // cannot get, update disabled artifact
        assertWebError(400, () -> service.getArtifactVersion(3, artifactId));
        assertWebError(400, () -> service.updateArtifactVersionMetaData(3, artifactId, emd));

        service.updateArtifactMetaData(artifactId, emd);

        retry(() -> {
            service.reset();
            ArtifactMetaData innerAmd = service.getArtifactMetaData(artifactId);
            Assertions.assertEquals(2, innerAmd.getVersion()); // should still be latest (aka 2)
            Assertions.assertEquals(description, innerAmd.getDescription());
            return null;
        });

        service.updateArtifactVersionState(3, artifactId, toUpdateState(ArtifactState.DEPRECATED));
        this.waitForVersionState(artifactId, 3, ArtifactState.DEPRECATED);

        tamd = service.getArtifactMetaData(artifactId);
        Assertions.assertEquals(3, tamd.getVersion()); // should be back to v3
        Assertions.assertEquals(ArtifactState.DEPRECATED, tamd.getState());
        Assertions.assertNull(tamd.getDescription());

        Response avr = service.getLatestArtifact(artifactId);
        Assertions.assertEquals(200, avr.getStatus());
        avr = service.getArtifactVersion(2, artifactId);
        Assertions.assertEquals(200, avr.getStatus());

        // cannot go back from deprecated ...
        assertWebError(400, () -> service.updateArtifactState(artifactId, toUpdateState(ArtifactState.ENABLED)));

        service.updateArtifactMetaData(artifactId, emd); // should be allowed for deprecated

        retry(() -> {
            service.reset();
            ArtifactMetaData innerAmd = service.getArtifactMetaData(artifactId);
            Assertions.assertEquals(3, innerAmd.getVersion()); // should still be latest (aka 3)
            Assertions.assertEquals(description, innerAmd.getDescription());

            VersionMetaData innerVmd = service.getArtifactVersionMetaData(1, artifactId);
            Assertions.assertNull(innerVmd.getDescription());

            return null;
        });

        service.updateArtifactState(artifactId, toUpdateState(ArtifactState.DELETED));

        retry(() -> {
            ArtifactMetaData innerAmd = service.getArtifactMetaData(artifactId);
            Assertions.assertEquals(2, innerAmd.getVersion());
            Assertions.assertEquals(ArtifactState.ENABLED, innerAmd.getState());
            return null;
        });
    }

    @RegistryServiceTest
    void testEnableDisableArtifact(Supplier<RegistryService> supplier) throws Exception {
        RegistryService service = supplier.get();
        String artifactId = generateArtifactId();

        // Create the artifact
        CompletionStage<ArtifactMetaData> a1 = service.createArtifact(
                ArtifactType.JSON,
                artifactId,
                null,
                new ByteArrayInputStream("{\"type\": \"string\"}".getBytes(StandardCharsets.UTF_8))
        );
        ArtifactMetaData md = ConcurrentUtil.result(a1);

        retry(() -> {
            // Get the meta-data
            ArtifactMetaData actualMD = service.getArtifactMetaData(artifactId);
            assertEquals(md.getGlobalId(), actualMD.getGlobalId());
        });

        // Set to disabled
        UpdateState state = new UpdateState();
        state.setState(ArtifactState.DISABLED);
        service.updateArtifactState(artifactId, state);
        this.waitForArtifactState(artifactId, ArtifactState.DISABLED);

        // Get the meta-data again - should be a 404
        assertWebError(404, () -> service.getArtifactMetaData(artifactId), true);
        VersionMetaData vmd = service.getArtifactVersionMetaData(md.getVersion(), artifactId);
        Assertions.assertEquals(ArtifactState.DISABLED, vmd.getState());

        // Now re-enable the artifact
        state.setState(ArtifactState.ENABLED);
        service.updateArtifactState(artifactId, state);
        this.waitForArtifactState(artifactId, ArtifactState.ENABLED);

        // Get the meta-data
        ArtifactMetaData amd = service.getArtifactMetaData(artifactId);
        Assertions.assertEquals(ArtifactState.ENABLED, amd.getState());
        vmd = service.getArtifactVersionMetaData(md.getVersion(), artifactId);
        Assertions.assertEquals(ArtifactState.ENABLED, vmd.getState());
    }

}