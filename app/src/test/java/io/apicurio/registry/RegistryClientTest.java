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
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class RegistryClientTest extends AbstractResourceTestBase {

    @Test
    public void testSmoke() throws Exception {
        try (RegistryService service = RegistryClient.create("http://localhost:8081")) {
            service.deleteAllGlobalRules();
        }
    }

    @Test
    public void testAsyncCRUD() throws Exception {
        try (RegistryService service = RegistryClient.create("http://localhost:8081")) {
            String artifactId = UUID.randomUUID().toString();
            try {
                ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes());
                CompletionStage<ArtifactMetaData> csResult = service.createArtifact(ArtifactType.JSON, artifactId, stream);
                ConcurrentUtil.result(csResult);

                EditableMetaData emd = new EditableMetaData();
                emd.setName("myname");
                service.updateArtifactMetaData(artifactId, emd);
                retry(() -> {
                    ArtifactMetaData artifactMetaData = service.getArtifactMetaData(artifactId);
                    Assertions.assertNotNull(artifactMetaData);
                    Assertions.assertEquals("myname", artifactMetaData.getName());
                    return null;
                });

                stream = new ByteArrayInputStream("{\"name\":\"ibm\"}".getBytes());
                csResult = service.updateArtifact(artifactId, ArtifactType.JSON, stream);
                ConcurrentUtil.result(csResult);
            } finally {
                service.deleteArtifact(artifactId);
            }
        }
    }
}