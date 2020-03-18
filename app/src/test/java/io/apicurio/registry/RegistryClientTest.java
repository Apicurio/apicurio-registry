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
import io.apicurio.registry.ext.RegistryServiceTest;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class RegistryClientTest extends AbstractResourceTestBase {

    @RegistryServiceTest
    public void testSmoke(RegistryService service) {
        service.deleteAllGlobalRules();
    }

    @RegistryServiceTest
    public void testAsyncCRUD(RegistryService service) throws Exception {
        String artifactId = generateArtifactId();
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
            CompletionStage<ArtifactMetaData> csResult = service.createArtifact(ArtifactType.JSON, artifactId, stream);
            ConcurrentUtil.result(csResult);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("myname");
            service.updateArtifactMetaData(artifactId, emd);
            retry(() -> {
                ArtifactMetaData artifactMetaData = service.getArtifactMetaData(artifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("myname", artifactMetaData.getName());
            });

            stream = new ByteArrayInputStream("{\"name\":\"ibm\"}".getBytes(StandardCharsets.UTF_8));
            csResult = service.updateArtifact(artifactId, ArtifactType.JSON, stream);
            ConcurrentUtil.result(csResult);
        } finally {
            service.deleteArtifact(artifactId);
        }
    }

    @RegistryServiceTest
    void deleteArtifactSpecificVersion(RegistryService service) throws Exception {
        ByteArrayInputStream artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecordx\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        String artifactId = generateArtifactId();
        ConcurrentUtil.result(service.createArtifact(ArtifactType.AVRO, artifactId, artifactData));

        for (int x = 0; x < 9; x++) {
            String artifactDefinition = "{\"type\":\"record\",\"name\":\"myrecordx\",\"fields\":[{\"name\":\"foo" + x + "\",\"type\":\"string\"}]}";
            artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
            ConcurrentUtil.result(service.updateArtifact(artifactId, ArtifactType.AVRO, artifactData));
        }

        retry(() -> {
            List<Long> artifactVersions = service.listArtifactVersions(artifactId);
            List<Long> expectedVersions = LongStream.range(1, 11).boxed().collect(Collectors.toList());
            Assertions.assertEquals(artifactVersions, expectedVersions);
        });

        service.deleteArtifactVersion(4, artifactId);

        retry(() -> {
            List<Long> artifactVersions = service.listArtifactVersions(artifactId);
            List<Long> expectedVersions = LongStream.range(1, 11).boxed().filter(l -> l != 4).collect(Collectors.toList());
            Assertions.assertEquals(artifactVersions, expectedVersions);
        });

        assertWebError(404, () -> service.getArtifactVersion(4, artifactId));

        artifactData = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecordx\",\"fields\":[{\"name\":\"foo11\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
        service.updateArtifact(artifactId, ArtifactType.AVRO, artifactData);

        retry(() -> {
            List<Long> artifactVersions = service.listArtifactVersions(artifactId);
            List<Long> expectedVersions = LongStream.range(1, 12).boxed().filter(l -> l != 4).collect(Collectors.toList());
            Assertions.assertEquals(artifactVersions, expectedVersions);
        });
    }

}