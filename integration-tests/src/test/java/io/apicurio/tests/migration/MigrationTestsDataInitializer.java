package io.apicurio.tests.migration;

import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.v2.models.ArtifactContent;
import io.apicurio.registry.rest.client.v2.models.Rule;
import io.apicurio.registry.rest.client.v2.models.RuleType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.impexp.EntityWriter;
import io.apicurio.registry.utils.impexp.v2.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
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

    public static void initializeMigrateTest(io.apicurio.registry.rest.client.v2.RegistryClient source,
            String registryBaseUrl) throws Exception {
        migrateGlobalIds = new ArrayList<>();
        migrateReferencesMap = new HashMap<>();

        JsonSchemaMsgFactory jsonSchema = new JsonSchemaMsgFactory();
        for (int idx = 0; idx < 50; idx++) {
            String artifactId = idx + "-" + UUID.randomUUID().toString();

            io.apicurio.registry.rest.client.v2.models.ArtifactContent createArtifact = TestUtils
                    .clientCreateArtifactV2(artifactId, ArtifactType.JSON,
                            new String(jsonSchema.getSchemaStream().readAllBytes(), StandardCharsets.UTF_8),
                            ContentTypes.APPLICATION_JSON);
            var response = source.groups().byGroupId("default").artifacts().post(createArtifact);
            TestUtils.retry(() -> source.ids().globalIds().byGlobalId(response.getGlobalId()));
            migrateGlobalIds.add(response.getGlobalId());
        }

        for (int idx = 0; idx < 15; idx++) {
            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(
                    List.of("a" + idx));
            String artifactId = "avro-" + idx;
            List<io.apicurio.registry.rest.client.v2.models.ArtifactReference> references = idx > 0
                ? getSingletonRefListV2("migrateTest", "avro-" + (idx - 1), "1", "myRef" + idx)
                : Collections.emptyList();

            io.apicurio.registry.rest.client.v2.models.ArtifactContent createArtifact = TestUtils
                    .clientCreateArtifactV2(artifactId, ArtifactType.AVRO,
                            new String(avroSchema.generateSchemaStream().readAllBytes(),
                                    StandardCharsets.UTF_8),
                            ContentTypes.APPLICATION_JSON);
            createArtifact.setReferences(references);
            var response = source.groups().byGroupId("migrateTest").artifacts().post(createArtifact,
                    configuration -> {
                        configuration.headers.add("X-Registry-ArtifactType", ArtifactType.AVRO);
                        configuration.headers.add("X-Registry-ArtifactId", artifactId);
                    });

            TestUtils.retry(() -> source.ids().globalIds().byGlobalId(response.getGlobalId()).get());
            assertTrue(matchesReferencesV2V2(references,
                    source.ids().globalIds().byGlobalId(response.getGlobalId()).references().get()));
            migrateReferencesMap.put(response.getGlobalId(), references);
            migrateGlobalIds.add(response.getGlobalId());

            avroSchema = new AvroGenericRecordSchemaFactory(List.of("u" + idx));
            List<io.apicurio.registry.rest.client.v2.models.ArtifactReference> updatedReferences = idx > 0
                ? getSingletonRefListV2("migrateTest", "avro-" + (idx - 1), "2", "myRef" + idx)
                : Collections.emptyList();
            io.apicurio.registry.rest.client.v2.models.ArtifactContent createVersion = TestUtils
                    .clientCreateVersionV2(new String(avroSchema.generateSchemaStream().readAllBytes(),
                            StandardCharsets.UTF_8), ContentTypes.APPLICATION_JSON);
            createVersion.setReferences(updatedReferences);
            var vmd = source.groups().byGroupId("migrateTest").artifacts().byArtifactId(artifactId).versions()
                    .post(createVersion);
            TestUtils.retry(() -> source.ids().globalIds().byGlobalId(vmd.getGlobalId()));
            assertTrue(matchesReferencesV2V2(updatedReferences,
                    source.ids().globalIds().byGlobalId(vmd.getGlobalId()).references().get()));
            migrateReferencesMap.put(vmd.getGlobalId(), updatedReferences);
            migrateGlobalIds.add(vmd.getGlobalId());
        }

        io.apicurio.registry.rest.client.v2.models.Rule createRule = new Rule();
        createRule.setType(io.apicurio.registry.rest.client.v2.models.RuleType.VALIDITY);
        createRule.setConfig("SYNTAX_ONLY");
        source.groups().byGroupId("migrateTest").artifacts().byArtifactId("avro-0").rules().post(createRule);

        createRule.setType(RuleType.COMPATIBILITY);
        createRule.setConfig("BACKWARD");
        source.admin().rules().post(createRule);

        var downloadHref = source.admin().export().get().getHref();
        OkHttpClient client = new OkHttpClient();
        DataMigrationIT.migrateDataToImport = client
                .newCall(new Request.Builder().url(registryBaseUrl + downloadHref).build()).execute().body()
                .byteStream();
    }

    public static void initializeDoNotPreserveIdsImport(
            io.apicurio.registry.rest.client.v2.RegistryClient source, String registryBaseUrl)
            throws Exception {
        // Fill the source registry with data
        JsonSchemaMsgFactory jsonSchema = new JsonSchemaMsgFactory();
        for (int idx = 0; idx < 50; idx++) {
            String artifactId = idx + "-" + UUID.randomUUID().toString();
            String content = IoUtil.toString(jsonSchema.getSchemaStream());

            ArtifactContent createArtifact = TestUtils.clientCreateArtifactV2(artifactId, ArtifactType.JSON,
                    content, ContentTypes.APPLICATION_JSON);
            var response = source.groups().byGroupId("testDoNotPreserveIdsImport").artifacts()
                    .post(createArtifact);
            TestUtils.retry(() -> source.ids().globalIds().byGlobalId(response.getGlobalId()).get());
            doNotPreserveIdsImportArtifacts.put("testDoNotPreserveIdsImport:" + artifactId, content);
        }

        for (int idx = 0; idx < 15; idx++) {
            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(
                    List.of("a" + idx));
            String artifactId = "avro-" + idx + "-" + UUID.randomUUID().toString();
            String content = IoUtil.toString(avroSchema.generateSchemaStream());
            io.apicurio.registry.rest.client.v2.models.ArtifactContent createArtifact = TestUtils
                    .clientCreateArtifactV2(artifactId, ArtifactType.AVRO, content,
                            ContentTypes.APPLICATION_JSON);
            var response = source.groups().byGroupId("testDoNotPreserveIdsImport").artifacts()
                    .post(createArtifact);
            TestUtils.retry(() -> source.ids().globalIds().byGlobalId(response.getGlobalId()).get());
            doNotPreserveIdsImportArtifacts.put("testDoNotPreserveIdsImport:" + artifactId, content);

            avroSchema = new AvroGenericRecordSchemaFactory(List.of("u" + idx));
            String content2 = IoUtil.toString(avroSchema.generateSchemaStream());
            ArtifactContent createVersion = TestUtils.clientCreateVersionV2(content2,
                    ContentTypes.APPLICATION_JSON);
            var vmd = source.groups().byGroupId("testDoNotPreserveIdsImport").artifacts()
                    .byArtifactId(artifactId).versions().post(createVersion);
            TestUtils.retry(() -> source.ids().globalIds().byGlobalId(vmd.getGlobalId()).get());
            doNotPreserveIdsImportArtifacts.put("testDoNotPreserveIdsImport:" + artifactId, content2);
        }

        var downloadHref = source.admin().export().get().getHref();
        OkHttpClient client = new OkHttpClient();
        DoNotPreserveIdsImportIT.doNotPreserveIdsImportDataToImport = client
                .newCall(new Request.Builder().url(registryBaseUrl + downloadHref).build()).execute().body()
                .byteStream();
        DoNotPreserveIdsImportIT.jsonSchema = jsonSchema;
    }

    protected static List<ArtifactReference> getSingletonRefList(String groupId, String artifactId,
            String version, String name) {
        ArtifactReference artifactReference = new ArtifactReference();
        artifactReference.setGroupId(groupId);
        artifactReference.setArtifactId(artifactId);
        artifactReference.setVersion(version);
        artifactReference.setName(name);
        return Collections.singletonList(artifactReference);
    }

    protected static List<io.apicurio.registry.rest.client.v2.models.ArtifactReference> getSingletonRefListV2(
            String groupId, String artifactId, String version, String name) {
        io.apicurio.registry.rest.client.v2.models.ArtifactReference artifactReference = new io.apicurio.registry.rest.client.v2.models.ArtifactReference();
        artifactReference.setGroupId(groupId);
        artifactReference.setArtifactId(artifactId);
        artifactReference.setVersion(version);
        artifactReference.setName(name);
        return Collections.singletonList(artifactReference);
    }

    public static boolean matchesReferences(List<ArtifactReference> srcReferences,
            List<ArtifactReference> destReferences) {
        return destReferences.size() == srcReferences.size() && destReferences.stream()
                .allMatch(srcRef -> srcReferences.stream()
                        .anyMatch(destRef -> Objects.equals(srcRef.getGroupId(), destRef.getGroupId())
                                && Objects.equals(srcRef.getArtifactId(), destRef.getArtifactId())
                                && Objects.equals(srcRef.getVersion(), destRef.getVersion())
                                && Objects.equals(srcRef.getName(), destRef.getName())));
    }

    public static boolean matchesReferencesV2V3(
            List<io.apicurio.registry.rest.client.v2.models.ArtifactReference> srcReferences,
            List<ArtifactReference> destReferences) {
        return destReferences.size() == srcReferences.size() && destReferences.stream()
                .allMatch(srcRef -> srcReferences.stream()
                        .anyMatch(destRef -> Objects.equals(srcRef.getGroupId(), destRef.getGroupId())
                                && Objects.equals(srcRef.getArtifactId(), destRef.getArtifactId())
                                && Objects.equals(srcRef.getVersion(), destRef.getVersion())
                                && Objects.equals(srcRef.getName(), destRef.getName())));
    }

    public static boolean matchesReferencesV2V2(
            List<io.apicurio.registry.rest.client.v2.models.ArtifactReference> srcReferences,
            List<io.apicurio.registry.rest.client.v2.models.ArtifactReference> destReferences) {
        return destReferences.size() == srcReferences.size() && destReferences.stream()
                .allMatch(srcRef -> srcReferences.stream()
                        .anyMatch(destRef -> Objects.equals(srcRef.getGroupId(), destRef.getGroupId())
                                && Objects.equals(srcRef.getArtifactId(), destRef.getArtifactId())
                                && Objects.equals(srcRef.getVersion(), destRef.getVersion())
                                && Objects.equals(srcRef.getName(), destRef.getName())));
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

                ArtifactEntity artifactEntity = new ArtifactEntity();
                artifactEntity.artifactId = artifactId;
                artifactEntity.artifactType = artifactType;
                artifactEntity.owner = "integration-tests";
                artifactEntity.createdOn = System.currentTimeMillis();
                artifactEntity.modifiedBy = "integration-tests";
                artifactEntity.modifiedOn = System.currentTimeMillis();
                artifactEntity.description = null;
                artifactEntity.groupId = null;
                artifactEntity.labels = null;
                artifactEntity.name = null;

                writer.writeEntity(artifactEntity);

                io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity versionEntity = new io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity();
                versionEntity.artifactId = artifactId;
                versionEntity.contentId = contentId;
                versionEntity.owner = "integration-tests";
                versionEntity.createdOn = System.currentTimeMillis();
                versionEntity.description = null;
                versionEntity.globalId = migrateGlobalIdseq.getAndIncrement();
                versionEntity.groupId = null;
                versionEntity.labels = null;
                versionEntity.name = null;
                versionEntity.state = VersionState.ENABLED;
                versionEntity.version = "1";
                versionEntity.versionOrder = 1;

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
