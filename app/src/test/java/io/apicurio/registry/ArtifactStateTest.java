/*
 * Copyright 2019 Red Hat
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

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.UpdateState;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.tests.RegistryServiceTest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;

import static io.apicurio.registry.utils.tests.TestUtils.assertWebError;
import static io.apicurio.registry.utils.tests.TestUtils.retry;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

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
        String artifactId = generateArtifactId();

        CompletionStage<ArtifactMetaData> a1 = supplier.get().createArtifact(
            ArtifactType.JSON,
            artifactId,
            null,
            new ByteArrayInputStream("{\"type\": \"string\"}".getBytes(StandardCharsets.UTF_8))
        );
        ConcurrentUtil.result(a1);

        CompletionStage<ArtifactMetaData> a2 = supplier.get().updateArtifact(
            artifactId,
            ArtifactType.JSON,
            new ByteArrayInputStream("\"type\": \"int\"".getBytes(StandardCharsets.UTF_8))
        );
        ConcurrentUtil.result(a2);

        CompletionStage<ArtifactMetaData> a3 = supplier.get().updateArtifact(
            artifactId,
            ArtifactType.JSON,
            new ByteArrayInputStream("\"type\": \"float\"".getBytes(StandardCharsets.UTF_8))
        );
        ConcurrentUtil.result(a3);

        ArtifactMetaData amd = supplier.get().getArtifactMetaData(artifactId);
        Assertions.assertEquals(3, amd.getVersion());

        // disable latest
        supplier.get().updateArtifactState(artifactId, toUpdateState(ArtifactState.DISABLED));

        // retries are here due to possible async nature of storage; e.g. Kafka, Streams, ...

        retry(() -> {
            VersionMetaData tvmd = supplier.get().getArtifactVersionMetaData(3, artifactId);
                  Assertions.assertEquals(3, tvmd.getVersion());
                  Assertions.assertEquals(ArtifactState.DISABLED, tvmd.getState());
                  return null;
              }
        );

        retry(() -> {
                  ArtifactMetaData tamd = supplier.get().getArtifactMetaData(artifactId);
                  Assertions.assertEquals(2, tamd.getVersion()); // should still be latest active (aka 2)
                  Assertions.assertEquals(ArtifactState.ENABLED, tamd.getState());
                  Assertions.assertNull(tamd.getDescription());
                  return null;
              }
        );

        EditableMetaData emd = new EditableMetaData();
        String description = "Testing artifact state";
        emd.setDescription(description);

        // cannot get, update disabled artifact
        assertWebError(400, () -> supplier.get().getArtifactVersion(3, artifactId));
        assertWebError(400, () -> supplier.get().updateArtifactVersionMetaData(3, artifactId, emd));

        supplier.get().updateArtifactMetaData(artifactId, emd);

        retry(() -> {
                  ArtifactMetaData tamd = supplier.get().getArtifactMetaData(artifactId);
                  Assertions.assertEquals(2, tamd.getVersion()); // should still be latest (aka 2)
                  Assertions.assertEquals(description, tamd.getDescription());
                  return null;
              }
        );

        supplier.get().updateArtifactVersionState(3, artifactId, toUpdateState(ArtifactState.DEPRECATED));

        retry(() -> {
            ArtifactMetaData tamd = supplier.get().getArtifactMetaData(artifactId);
            Assertions.assertEquals(3, tamd.getVersion()); // should be back to v3
            Assertions.assertEquals(ArtifactState.DEPRECATED, tamd.getState());
            Assertions.assertNull(tamd.getDescription());

            Response avr = supplier.get().getLatestArtifact(artifactId);
            Assertions.assertEquals(200, avr.getStatus());
            avr = supplier.get().getArtifactVersion(2, artifactId);
            Assertions.assertEquals(200, avr.getStatus());

            // cannot go back from deprecated ...
            assertWebError(400, () -> supplier.get().updateArtifactState(artifactId, toUpdateState(ArtifactState.ENABLED)));
            return null;
        });

        supplier.get().updateArtifactMetaData(artifactId, emd); // should be allowed for deprecated

        retry(() -> {
            ArtifactMetaData tamd = supplier.get().getArtifactMetaData(artifactId);
                  Assertions.assertEquals(3, tamd.getVersion()); // should still be latest (aka 3)
                  Assertions.assertEquals(description, tamd.getDescription());

            VersionMetaData tvmd = supplier.get().getArtifactVersionMetaData(1, artifactId);
                  Assertions.assertNull(tvmd.getDescription());

                  return null;
              }
        );

        supplier.get().updateArtifactState(artifactId, toUpdateState(ArtifactState.DELETED));

        retry(() -> {
            ArtifactMetaData tamd = supplier.get().getArtifactMetaData(artifactId);
                  Assertions.assertEquals(2, tamd.getVersion());
                  Assertions.assertEquals(ArtifactState.ENABLED, tamd.getState());
                  return null;
              }
        );
    }

    @RegistryServiceTest
    void testEnableDisableArtifact(Supplier<RegistryService> supplier) {
        String artifactId = generateArtifactId();

        // Create the artifact
        CompletionStage<ArtifactMetaData> a1 = supplier.get().createArtifact(
            ArtifactType.JSON,
            artifactId,
            null,
            new ByteArrayInputStream("{\"type\": \"string\"}".getBytes(StandardCharsets.UTF_8))
        );
        ArtifactMetaData md = ConcurrentUtil.result(a1);

        // Get the meta-data
        ArtifactMetaData actualMD = supplier.get().getArtifactMetaData(artifactId);
        assertEquals(md.getGlobalId(), actualMD.getGlobalId());

        // Set to disabled
        UpdateState state = new UpdateState();
        state.setState(ArtifactState.DISABLED);
        supplier.get().updateArtifactState(artifactId, state);

        // Get the meta-data again - should be a 404
        try {
            supplier.get().getArtifactMetaData(artifactId);
        } catch (WebApplicationException e) {
            assertEquals(404, e.getResponse().getStatus());
        }

        // Now re-enable the artifact
        state.setState(ArtifactState.ENABLED);
        supplier.get().updateArtifactState(artifactId, state);

        // Get the meta-data
        actualMD = supplier.get().getArtifactMetaData(artifactId);
        assertEquals(md.getGlobalId(), actualMD.getGlobalId());
    }

}