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
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.EntityWriter;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.serdes.apicurio.AvroGenericRecordSchemaFactory;
import io.apicurio.tests.serdes.apicurio.JsonSchemaMsgFactory;
import org.apache.commons.codec.digest.DigestUtils;

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

import static io.apicurio.tests.migration.DataMigrationIT.doNotPreserveIdsImportArtifacts;
import static io.apicurio.tests.migration.DataMigrationIT.migrateGlobalIds;
import static io.apicurio.tests.migration.DataMigrationIT.migrateReferencesMap;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MigrationTestsDataInitializer {

    public static void initializeMigrateTest(RegistryClient source) throws Exception {
        migrateGlobalIds = new ArrayList<>();
        migrateReferencesMap = new HashMap<>();

        JsonSchemaMsgFactory jsonSchema = new JsonSchemaMsgFactory();
        for (int idx = 0; idx < 50; idx++) {
            String artifactId = idx + "-" + UUID.randomUUID().toString();
            var amd = source.createArtifact("default", artifactId, jsonSchema.getSchemaStream());
            TestUtils.retry(() -> source.getContentByGlobalId(amd.getGlobalId()));
            migrateGlobalIds.add(amd.getGlobalId());
        }

        for (int idx = 0; idx < 15; idx++) {
            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(List.of("a" + idx));
            String artifactId = "avro-" + idx;
            List<ArtifactReference> references = idx > 0 ? getSingletonRefList("migrateTest", "avro-" + (idx - 1), "1", "myRef" + idx) : Collections.emptyList();
            var amd = source.createArtifact("migrateTest", artifactId, avroSchema.generateSchemaStream(), references);
            TestUtils.retry(() -> source.getContentByGlobalId(amd.getGlobalId()));
            assertTrue(matchesReferences(references, source.getArtifactReferencesByGlobalId(amd.getGlobalId())));
            migrateReferencesMap.put(amd.getGlobalId(), references);
            migrateGlobalIds.add(amd.getGlobalId());

            avroSchema = new AvroGenericRecordSchemaFactory(List.of("u" + idx));
            List<ArtifactReference> updatedReferences = idx > 0 ? getSingletonRefList("migrateTest", "avro-" + (idx - 1), "2", "myRef" + idx) : Collections.emptyList();
            var vmd = source.updateArtifact("migrateTest", artifactId, null, null, null, avroSchema.generateSchemaStream(), updatedReferences);
            TestUtils.retry(() -> source.getContentByGlobalId(vmd.getGlobalId()));
            assertTrue(matchesReferences(updatedReferences, source.getArtifactReferencesByGlobalId(vmd.getGlobalId())));
            migrateReferencesMap.put(vmd.getGlobalId(), updatedReferences);
            migrateGlobalIds.add(vmd.getGlobalId());
        }

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");
        source.createArtifactRule("migrateTest", "avro-0", rule);

        rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        source.createGlobalRule(rule);

        DataMigrationIT.migrateDataToImport = source.exportData();
    }

    public static void initializeDoNotPreserveIdsImport(RegistryClient source) throws Exception {
        // Fill the source registry with data
        JsonSchemaMsgFactory jsonSchema = new JsonSchemaMsgFactory();
        for (int idx = 0; idx < 50; idx++) {
            String artifactId = idx + "-" + UUID.randomUUID().toString();
            String content = IoUtil.toString(jsonSchema.getSchemaStream());
            var amd = source.createArtifact("testDoNotPreserveIdsImport", artifactId, IoUtil.toStream(content));
            TestUtils.retry(() -> source.getContentByGlobalId(amd.getGlobalId()));
            doNotPreserveIdsImportArtifacts.put("testDoNotPreserveIdsImport:" + artifactId, content);
        }

        for (int idx = 0; idx < 15; idx++) {
            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(List.of("a" + idx));
            String artifactId = "avro-" + idx + "-" + UUID.randomUUID().toString();
            String content = IoUtil.toString(avroSchema.generateSchemaStream());
            var amd = source.createArtifact("testDoNotPreserveIdsImport", artifactId, IoUtil.toStream(content));
            TestUtils.retry(() -> source.getContentByGlobalId(amd.getGlobalId()));
            doNotPreserveIdsImportArtifacts.put("testDoNotPreserveIdsImport:" + artifactId, content);

            avroSchema = new AvroGenericRecordSchemaFactory(List.of("u" + idx));
            String content2 = IoUtil.toString(avroSchema.generateSchemaStream());
            var vmd = source.updateArtifact("testDoNotPreserveIdsImport", artifactId, IoUtil.toStream(content2));
            TestUtils.retry(() -> source.getContentByGlobalId(vmd.getGlobalId()));
            doNotPreserveIdsImportArtifacts.put("testDoNotPreserveIdsImport:" + artifactId, content2);
        }

        DoNotPreserveIdsImportIT.doNotPreserveIdsImportDataToImport = source.exportData();
        DoNotPreserveIdsImportIT.jsonSchema = jsonSchema;
    }

    protected static List<ArtifactReference> getSingletonRefList(String groupId, String artifactId, String version, String name) {
        ArtifactReference artifactReference = new ArtifactReference();
        artifactReference.setGroupId(groupId);
        artifactReference.setArtifactId(artifactId);
        artifactReference.setVersion(version);
        artifactReference.setName(name);
        return Collections.singletonList(artifactReference);
    }

    public static boolean matchesReferences(List<ArtifactReference> srcReferences, List<ArtifactReference> destReferences) {
        return destReferences.size() == srcReferences.size() && destReferences.stream().allMatch(
                srcRef -> srcReferences.stream().anyMatch(destRef ->
                        Objects.equals(srcRef.getGroupId(), destRef.getGroupId()) &&
                                Objects.equals(srcRef.getArtifactId(), destRef.getArtifactId()) &&
                                Objects.equals(srcRef.getVersion(), destRef.getVersion()) &&
                                Objects.equals(srcRef.getName(), destRef.getName()))
        );
    }

    public InputStream generateExportedZip(Map<String, String> artifacts) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8);
            EntityWriter writer = new EntityWriter(zip);

            Map<String, Long> contentIndex = new HashMap<>();

            AtomicInteger migrateGlobalIdseq = new AtomicInteger(1);
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
                versionEntity.globalId = migrateGlobalIdseq.getAndIncrement();
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
