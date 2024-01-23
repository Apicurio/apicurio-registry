package io.apicurio.tests.migration;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.EntityWriter;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.serdes.apicurio.AvroGenericRecordSchemaFactory;
import io.apicurio.tests.serdes.apicurio.JsonSchemaMsgFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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

    public static void initializeMigrateTest(RegistryClient source, String registryBaseUrl) throws Exception {
        migrateGlobalIds = new ArrayList<>();
        migrateReferencesMap = new HashMap<>();

        JsonSchemaMsgFactory jsonSchema = new JsonSchemaMsgFactory();
        for (int idx = 0; idx < 50; idx++) {
            String artifactId = idx + "-" + UUID.randomUUID().toString();
            ArtifactContent content = new ArtifactContent();
            content.setContent(new String(jsonSchema.getSchemaStream().readAllBytes(), StandardCharsets.UTF_8));
            var amd = source.groups().byGroupId("default").artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                });
            TestUtils.retry(() -> source.ids().globalIds().byGlobalId(amd.getGlobalId()));
            migrateGlobalIds.add(amd.getGlobalId());
        }

        for (int idx = 0; idx < 15; idx++) {
            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(List.of("a" + idx));
            String artifactId = "avro-" + idx;
            List<ArtifactReference> references = idx > 0 ? getSingletonRefList("migrateTest", "avro-" + (idx - 1), "1", "myRef" + idx) : Collections.emptyList();

            ArtifactContent content = new ArtifactContent();
            content.setContent(new String(avroSchema.generateSchemaStream().readAllBytes(), StandardCharsets.UTF_8));
            content.setReferences(references);
            var amd = source.groups().byGroupId("migrateTest").artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
            });

            TestUtils.retry(() -> source.ids().globalIds().byGlobalId(amd.getGlobalId()).get());
            assertTrue(matchesReferences(references, source.ids().globalIds().byGlobalId(amd.getGlobalId()).references().get()));
            migrateReferencesMap.put(amd.getGlobalId(), references);
            migrateGlobalIds.add(amd.getGlobalId());

            avroSchema = new AvroGenericRecordSchemaFactory(List.of("u" + idx));
            List<ArtifactReference> updatedReferences = idx > 0 ? getSingletonRefList("migrateTest", "avro-" + (idx - 1), "2", "myRef" + idx) : Collections.emptyList();
            content.setContent(new String(avroSchema.generateSchemaStream().readAllBytes(), StandardCharsets.UTF_8));
            content.setReferences(updatedReferences);
            var vmd = source.groups().byGroupId("migrateTest").artifacts().byArtifactId(artifactId).put(content);
            TestUtils.retry(() -> source.ids().globalIds().byGlobalId(vmd.getGlobalId()));
            assertTrue(matchesReferences(updatedReferences, source.ids().globalIds().byGlobalId(vmd.getGlobalId()).references().get()));
            migrateReferencesMap.put(vmd.getGlobalId(), updatedReferences);
            migrateGlobalIds.add(vmd.getGlobalId());
        }

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");
        source.groups().byGroupId("migrateTest").artifacts().byArtifactId("avro-0").rules().post(rule);

        rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        source.admin().rules().post(rule);

        var downloadHref = source.admin().export().get().getHref();
        OkHttpClient client = new OkHttpClient();
        DataMigrationIT.migrateDataToImport = client.newCall(new Request.Builder().url(registryBaseUrl + downloadHref).build()).execute().body().byteStream();
    }

    public static void initializeDoNotPreserveIdsImport(RegistryClient source, String registryBaseUrl) throws Exception {
        // Fill the source registry with data
        JsonSchemaMsgFactory jsonSchema = new JsonSchemaMsgFactory();
        for (int idx = 0; idx < 50; idx++) {
            String artifactId = idx + "-" + UUID.randomUUID().toString();
            String content = IoUtil.toString(jsonSchema.getSchemaStream());
            ArtifactContent artifactContent = new ArtifactContent();
            artifactContent.setContent(content);
            var amd = source.groups().byGroupId("testDoNotPreserveIdsImport").artifacts().post(artifactContent, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
            });
            TestUtils.retry(() -> source.ids().globalIds().byGlobalId(amd.getGlobalId()).get());
            doNotPreserveIdsImportArtifacts.put("testDoNotPreserveIdsImport:" + artifactId, content);
        }

        for (int idx = 0; idx < 15; idx++) {
            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(List.of("a" + idx));
            String artifactId = "avro-" + idx + "-" + UUID.randomUUID().toString();
            String content = IoUtil.toString(avroSchema.generateSchemaStream());
            ArtifactContent artifactContent = new ArtifactContent();
            artifactContent.setContent(content);
            var amd = source.groups().byGroupId("testDoNotPreserveIdsImport").artifacts().post(artifactContent, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
            });
            TestUtils.retry(() -> source.ids().globalIds().byGlobalId(amd.getGlobalId()).get());
            doNotPreserveIdsImportArtifacts.put("testDoNotPreserveIdsImport:" + artifactId, content);

            avroSchema = new AvroGenericRecordSchemaFactory(List.of("u" + idx));
            String content2 = IoUtil.toString(avroSchema.generateSchemaStream());
            artifactContent.setContent(content2);
            var vmd = source.groups().byGroupId("testDoNotPreserveIdsImport").artifacts().byArtifactId(artifactId).put(artifactContent);
            TestUtils.retry(() -> source.ids().globalIds().byGlobalId(amd.getGlobalId()).get());
            doNotPreserveIdsImportArtifacts.put("testDoNotPreserveIdsImport:" + artifactId, content2);
        }

        var downloadHref = source.admin().export().get().getHref();
        OkHttpClient client = new OkHttpClient();
        DoNotPreserveIdsImportIT.doNotPreserveIdsImportDataToImport = client.newCall(new Request.Builder().url(registryBaseUrl + downloadHref).build()).execute().body().byteStream();
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
