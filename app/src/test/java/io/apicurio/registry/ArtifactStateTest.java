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

import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletionStage;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.client.RegistryClient;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.UpdateState;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
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

    @Test
    public void testSmoke() throws Exception {
        try (RegistryService service = RegistryClient.create("http://localhost:8081")) {

            String artifactId = generateArtifactId();

            CompletionStage<ArtifactMetaData> a1 = service.createArtifact(
                ArtifactType.JSON,
                artifactId,
                new ByteArrayInputStream("{\"type\": \"string\"}".getBytes())
            );
            ConcurrentUtil.result(a1);

            CompletionStage<ArtifactMetaData> a2 = service.updateArtifact(
                artifactId,
                ArtifactType.JSON,
                new ByteArrayInputStream("\"type\": \"int\"".getBytes())
            );
            ConcurrentUtil.result(a2);

            CompletionStage<ArtifactMetaData> a3 = service.updateArtifact(
                artifactId,
                ArtifactType.JSON,
                new ByteArrayInputStream("\"type\": \"float\"".getBytes())
            );
            ConcurrentUtil.result(a3);

            ArtifactMetaData amd = service.getArtifactMetaData(artifactId);
            Assertions.assertEquals(3, amd.getVersion());

            // disable latest
            service.updateArtifactState(artifactId, toUpdateState(ArtifactState.DISABLED));

            // retries are here due to possible async nature of storage; e.g. Kafka, Streams, ...

            retry(() -> {
                      VersionMetaData tvmd = service.getArtifactVersionMetaData(3, artifactId);
                      Assertions.assertEquals(3, tvmd.getVersion());
                      Assertions.assertEquals(ArtifactState.DISABLED, tvmd.getState());
                      return null;
                  }
            );

            retry(() -> {
                      ArtifactMetaData tamd = service.getArtifactMetaData(artifactId);
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
            assertWebError(400, () -> service.getArtifactVersion(3, artifactId));
            assertWebError(400, () -> service.updateArtifactVersionMetaData(3, artifactId, emd));

            service.updateArtifactMetaData(artifactId, emd);

            retry(() -> {
                      ArtifactMetaData tamd = service.getArtifactMetaData(artifactId);
                      Assertions.assertEquals(2, tamd.getVersion()); // should still be latest (aka 2)
                      Assertions.assertEquals(description, tamd.getDescription());
                      return null;
                  }
            );

            service.updateArtifactVersionState(3, artifactId, toUpdateState(ArtifactState.DEPRECATED));

            retry(() -> {
                ArtifactMetaData tamd = service.getArtifactMetaData(artifactId);
                Assertions.assertEquals(3, tamd.getVersion()); // should be back to v3
                Assertions.assertEquals(ArtifactState.DEPRECATED, tamd.getState());
                Assertions.assertNull(tamd.getDescription());

                Response avr = service.getLatestArtifact(artifactId);
                Assertions.assertEquals(200, avr.getStatus());
                avr = service.getArtifactVersion(2, artifactId);
                Assertions.assertEquals(200, avr.getStatus());

                // cannot go back from deprecated ...
                assertWebError(400, () -> service.updateArtifactState(artifactId, toUpdateState(ArtifactState.ENABLED)));
                return null;
            });

            service.updateArtifactMetaData(artifactId, emd); // should be allowed for deprecated

            retry(() -> {
                      ArtifactMetaData tamd = service.getArtifactMetaData(artifactId);
                      Assertions.assertEquals(3, tamd.getVersion()); // should still be latest (aka 3)
                      Assertions.assertEquals(description, tamd.getDescription());

                      VersionMetaData tvmd = service.getArtifactVersionMetaData(1, artifactId);
                      Assertions.assertNull(tvmd.getDescription());

                      return null;
                  }
            );

            service.updateArtifactState(artifactId, toUpdateState(ArtifactState.DELETED));

            retry(() -> {
                      ArtifactMetaData tamd = service.getArtifactMetaData(artifactId);
                      Assertions.assertEquals(2, tamd.getVersion());
                      Assertions.assertEquals(ArtifactState.ENABLED, tamd.getState());
                      return null;
                  }
            );
        }
    }
}