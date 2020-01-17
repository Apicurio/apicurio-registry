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

import io.apicurio.registry.client.RegistryClient;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletionStage;
import javax.ws.rs.core.Response;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class ArtifactStateTest extends AbstractResourceTestBase {

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

            ArtifactMetaData amd = service.getArtifactMetaData(artifactId);
            Assertions.assertEquals(2, amd.getVersion());

            // disable latest
            service.updateArtifactState(artifactId, ArtifactState.DISABLED);

            // retries are here due to possible async nature of storage; e.g. Kafka, Streams, ...

            retry(() -> {
                      ArtifactMetaData tamd = service.getArtifactMetaData(artifactId);
                      Assertions.assertEquals(2, tamd.getVersion()); // should still be latest (aka 2)
                      Assertions.assertEquals(ArtifactState.DISABLED, tamd.getState());
                      return null;
                  }
            );

            EditableMetaData emd = new EditableMetaData();
            emd.setDescription("Testing artifact state");

            // cannot get, update disabled artifact
            assertWebError(400, () -> service.getArtifactVersion(2, artifactId));
            assertWebError(400, () -> service.updateArtifactMetaData(artifactId, emd));

            service.updateArtifactState(artifactId, ArtifactState.DEPRECATED);

            // cannot go back from deprecated ...
            retry(() -> {
                assertWebError(400, () -> service.updateArtifactState(artifactId, ArtifactState.ENABLED));
                return null;
            });

            retry(() -> {
                      ArtifactMetaData tamd = service.getArtifactMetaData(artifactId);
                      Assertions.assertEquals(2, tamd.getVersion()); // should still be latest (aka 2)
                      Assertions.assertEquals(ArtifactState.DEPRECATED, tamd.getState());

                      Response avr = service.getArtifactVersion(2, artifactId);
                      Assertions.assertEquals(200, avr.getStatus());

                      return null;
                  }
            );

            service.updateArtifactMetaData(artifactId, emd); // should be allowed

            service.updateArtifactState(artifactId, ArtifactState.DELETED);

            retry(() -> {
                      ArtifactMetaData tamd = service.getArtifactMetaData(artifactId);
                      Assertions.assertEquals(1, tamd.getVersion());
                      Assertions.assertEquals(ArtifactState.ENABLED, tamd.getState());
                      return null;
                  }
            );
        }
    }
}