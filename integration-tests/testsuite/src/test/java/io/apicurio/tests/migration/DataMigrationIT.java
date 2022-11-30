/*
 * Copyright 2021 Red Hat
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

import static io.apicurio.registry.utils.tests.TestUtils.retry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipOutputStream;

import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.EntityWriter;
import io.apicurio.tests.common.ApicurioRegistryBaseIT;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.types.RuleType;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.RegistryFacade;
import io.apicurio.tests.serdes.apicurio.AvroGenericRecordSchemaFactory;
import io.apicurio.tests.serdes.apicurio.JsonSchemaMsgFactory;

/**
 * @author Fabian Martinez
 */
@Tag(Constants.MIGRATION)
public class DataMigrationIT extends ApicurioRegistryBaseIT {

    private final RegistryFacade registryFacade = RegistryFacade.getInstance();

    @Test
    public void migrate() throws Exception {
        RegistryClient source = RegistryClientFactory.create(registryFacade.getSourceRegistryUrl());

        List<Long> globalIds = new ArrayList<>();
        HashMap<Long, List<ArtifactReference>> referencesMap = new HashMap<>();

        JsonSchemaMsgFactory jsonSchema = new JsonSchemaMsgFactory();
        for (int idx = 0; idx < 50; idx++) {
            String artifactId = idx + "-" + UUID.randomUUID().toString();
            var amd = source.createArtifact("default", artifactId, jsonSchema.getSchemaStream());
            retry(() -> source.getContentByGlobalId(amd.getGlobalId()));
            globalIds.add(amd.getGlobalId());
        }

        for (int idx = 0; idx < 15; idx++) {
            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(List.of("a" + idx));
            String artifactId = "avro-" + idx;
            List<ArtifactReference> references = idx > 0 ? getSingletonRefList("avro-schemas", "avro-" + (idx - 1), "1", "myRef" + idx) : Collections.emptyList();
            var amd = source.createArtifact("avro-schemas", artifactId, avroSchema.generateSchemaStream(), references);
            retry(() -> source.getContentByGlobalId(amd.getGlobalId()));
            assertTrue(matchesReferences(references, source.getArtifactReferencesByGlobalId(amd.getGlobalId())));
            referencesMap.put(amd.getGlobalId(), references);
            globalIds.add(amd.getGlobalId());

            avroSchema = new AvroGenericRecordSchemaFactory(List.of("u" + idx));
            List<ArtifactReference> updatedReferences = idx > 0 ? getSingletonRefList("avro-schemas", "avro-" + (idx - 1), "2", "myRef" + idx) : Collections.emptyList();
            var vmd = source.updateArtifact("avro-schemas", artifactId, null, null, null, avroSchema.generateSchemaStream(), updatedReferences);
            retry(() -> source.getContentByGlobalId(vmd.getGlobalId()));
            assertTrue(matchesReferences(updatedReferences, source.getArtifactReferencesByGlobalId(vmd.getGlobalId())));
            referencesMap.put(vmd.getGlobalId(), updatedReferences);
            globalIds.add(vmd.getGlobalId());
        }

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");
        source.createArtifactRule("avro-schemas", "avro-0", rule);

        rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        source.createGlobalRule(rule);

        RegistryClient dest = RegistryClientFactory.create(registryFacade.getDestRegistryUrl());

        dest.importData(source.exportData());

        retry(() -> {
            for (long gid : globalIds) {
                dest.getContentByGlobalId(gid);
                if (referencesMap.containsKey(gid)) {
                    List<ArtifactReference> srcReferences = referencesMap.get(gid);
                    List<ArtifactReference> destReferences = dest.getArtifactReferencesByGlobalId(gid);
                    assertTrue(matchesReferences(srcReferences, destReferences));
                }
            }
            assertEquals("SYNTAX_ONLY", dest.getArtifactRuleConfig("avro-schemas", "avro-0", RuleType.VALIDITY).getConfig());
            assertEquals("BACKWARD", dest.getGlobalRuleConfig(RuleType.COMPATIBILITY).getConfig());
        });
    }

    @Test
    public void testDoNotPreserveIdsImport() throws Exception {
        RegistryClient source = RegistryClientFactory.create(registryFacade.getSourceRegistryUrl());
        RegistryClient dest = RegistryClientFactory.create(registryFacade.getDestRegistryUrl());

        Map<String, String> artifacts = new HashMap<>();

        // Fill the source registry with data
        JsonSchemaMsgFactory jsonSchema = new JsonSchemaMsgFactory();
        for (int idx = 0; idx < 50; idx++) {
            String artifactId = idx + "-" + UUID.randomUUID().toString();
            String content = IoUtil.toString(jsonSchema.getSchemaStream());
            var amd = source.createArtifact("default", artifactId, IoUtil.toStream(content));
            retry(() -> source.getContentByGlobalId(amd.getGlobalId()));
            artifacts.put("default:" + artifactId, content);
        }

        for (int idx = 0; idx < 15; idx++) {
            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(List.of("a" + idx));
            String artifactId = "avro-" + idx + "-" + UUID.randomUUID().toString();
            String content = IoUtil.toString(avroSchema.generateSchemaStream());
            var amd = source.createArtifact("avro-schemas", artifactId, IoUtil.toStream(content));
            retry(() -> source.getContentByGlobalId(amd.getGlobalId()));
            artifacts.put("avro-schemas:" + artifactId, content);

            avroSchema = new AvroGenericRecordSchemaFactory(List.of("u" + idx));
            String content2 = IoUtil.toString(avroSchema.generateSchemaStream());
            var vmd = source.updateArtifact("avro-schemas", artifactId, IoUtil.toStream(content2));
            retry(() -> source.getContentByGlobalId(vmd.getGlobalId()));
            artifacts.put("avro-schemas:" + artifactId, content2);
        }

        // Fill the destination registry with data (Avro content is inserted first to ensure that the content IDs are different)
        for (int idx = 0; idx < 15; idx++) {
            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(List.of("a" + idx));
            String artifactId = "avro-" + idx + "-" + UUID.randomUUID().toString(); // Artifact ids need to be different we do not support identical artifact ids
            String content = IoUtil.toString(avroSchema.generateSchemaStream());
            var amd = dest.createArtifact("avro-schemas", artifactId, IoUtil.toStream(content));
            retry(() -> dest.getContentByGlobalId(amd.getGlobalId()));
            artifacts.put("avro-schemas:" + artifactId, content);
        }

        for (int idx = 0; idx < 50; idx++) {
            String artifactId = idx + "-" + UUID.randomUUID().toString(); // Artifact ids need to be different we do not support identical artifact ids
            String content = IoUtil.toString(jsonSchema.getSchemaStream());
            var amd = dest.createArtifact("default", artifactId, IoUtil.toStream(content));
            retry(() -> dest.getContentByGlobalId(amd.getGlobalId()));
            artifacts.put("default:" + artifactId, content);
        }

        // Import the data
        InputStream data = source.exportData();


        dest.importData(data, false, false);

        // Check that the import was successful
        retry(() -> {
            for (var entry : artifacts.entrySet()) {
                String groupId = entry.getKey().split(":")[0];
                String artifactId = entry.getKey().split(":")[1];
                String content = entry.getValue();
                var registryContent = dest.getLatestArtifact(groupId, artifactId);
                assertNotNull(registryContent);
                assertEquals(content, IoUtil.toString(registryContent));
            }
        });
    }

    @Test
    public void testGeneratingCanonicalHashOnImport() throws Exception {
        RegistryClient dest = RegistryClientFactory.create(registryFacade.getDestRegistryUrl());

        Map<String, String> artifacts = new HashMap<>();

        JsonSchemaMsgFactory jsonSchema = new JsonSchemaMsgFactory();
        for(int i = 0; i < 20; i++) {
            String artifactId = i + "-" + UUID.randomUUID();
            String content = IoUtil.toString(jsonSchema.getSchemaStream());
            artifacts.put(artifactId, content);
        }
        dest.importData(generateExportedZip(artifacts), false, false);

        retry(() -> {
            for(var entry : artifacts.entrySet()) {
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

    @AfterEach
    public void tearDownRegistries() throws IOException {
        registryFacade.stopAndCollectLogs(null);
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

    private List<ArtifactReference> getSingletonRefList(String groupId, String artifactId, String version, String name) {
        ArtifactReference artifactReference = new ArtifactReference();
        artifactReference.setGroupId(groupId);
        artifactReference.setArtifactId(artifactId);
        artifactReference.setVersion(version);
        artifactReference.setName(name);
        return Collections.singletonList(artifactReference);
    }

    private boolean matchesReferences(List<ArtifactReference> srcReferences, List<ArtifactReference> destReferences) {
        return destReferences.size() == srcReferences.size() && destReferences.stream().allMatch(
                srcRef -> srcReferences.stream().anyMatch(destRef ->
                        Objects.equals(srcRef.getGroupId(), destRef.getGroupId()) &&
                                Objects.equals(srcRef.getArtifactId(), destRef.getArtifactId()) &&
                                Objects.equals(srcRef.getVersion(), destRef.getVersion()) &&
                                Objects.equals(srcRef.getName(), destRef.getName()))
        );
    }

}
