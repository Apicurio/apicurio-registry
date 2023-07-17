/*
 * Copyright 2023 Red Hat
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

package io.apicurio.tests.migration;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.EntityWriter;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.serdes.apicurio.JsonSchemaMsgFactory;
import io.apicurio.tests.utils.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusIntegrationTest
@Tag(Constants.MIGRATION)
public class GenerateCanonicalHashImportIT extends ApicurioRegistryBaseIT {

    @Test
    public void testGeneratingCanonicalHashOnImport() throws Exception {
        RegistryClient dest = RegistryClientFactory.create(ApicurioRegistryBaseIT.getRegistryV2ApiUrl());

        Map<String, String> artifacts = new HashMap<>();

        JsonSchemaMsgFactory jsonSchema = new JsonSchemaMsgFactory();
        for (int i = 0; i < 20; i++) {
            String artifactId = i + "-" + UUID.randomUUID();
            String content = IoUtil.toString(jsonSchema.getSchemaStream());
            artifacts.put(artifactId, content);
        }
        dest.importData(generateExportedZip(artifacts), false, false);

        retry(() -> {
            for (var entry : artifacts.entrySet()) {
                String groupId = "default";
                String artifactId = entry.getKey();
                String content = entry.getValue();

                /*
                TODO: Check if the canonical hash is generated correctly.
                      The only way is to generate canonical hash and then search artifact by it. But that needs apicurio-registry-app module as dependency.
                 */

                var registryContent = dest.getLatestArtifact(groupId, artifactId);
                assertNotNull(registryContent);
                assertEquals(content, IoUtil.toString(registryContent));
            }
        });

    }

    public InputStream generateExportedZip(Map<String, String> artifacts) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8);
            EntityWriter writer = new EntityWriter(zip);

            Map<String, Long> contentIndex = new HashMap<>();

            AtomicInteger globalIdSeq = new AtomicInteger(1);
            AtomicInteger contentIdSeq = new AtomicInteger(1);

            for (var entry : artifacts.entrySet()) {
                String artifactId = entry.getKey();
                String content = entry.getValue();
                byte[] contentBytes = IoUtil.toBytes(content);
                String contentHash = DigestUtils.sha256Hex(contentBytes);

                String artifactType = ArtifactType.JSON;

                Long contentId = contentIndex.computeIfAbsent(contentHash, k -> {
                    ContentEntity contentEntity = new ContentEntity();
                    contentEntity.contentId = contentIdSeq.getAndIncrement();
                    contentEntity.contentHash = contentHash;
                    contentEntity.canonicalHash = null;
                    contentEntity.contentBytes = contentBytes;
                    contentEntity.artifactType = artifactType;

                    try {
                        writer.writeEntity(contentEntity);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    return contentEntity.contentId;
                });

                ArtifactVersionEntity versionEntity = new ArtifactVersionEntity();
                versionEntity.artifactId = artifactId;
                versionEntity.artifactType = artifactType;
                versionEntity.contentId = contentId;
                versionEntity.createdBy = "integration-tests";
                versionEntity.createdOn = System.currentTimeMillis();
                versionEntity.description = null;
                versionEntity.globalId = globalIdSeq.getAndIncrement();
                versionEntity.groupId = null;
                versionEntity.isLatest = true;
                versionEntity.labels = null;
                versionEntity.name = null;
                versionEntity.properties = null;
                versionEntity.state = ArtifactState.ENABLED;
                versionEntity.version = "1";
                versionEntity.versionId = 1;

                writer.writeEntity(versionEntity);
            }

            zip.flush();
            zip.close();

            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
