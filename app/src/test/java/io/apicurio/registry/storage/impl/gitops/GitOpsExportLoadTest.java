package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.rest.v3.impl.shared.gitops.GitOpsEntityCollector;
import io.apicurio.registry.rest.v3.impl.shared.gitops.GitOpsZipWriter;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
public class GitOpsExportLoadTest {

    private static final String REGISTRY_ID = "test";
    private static final String GROUP_ID = "ExportLoadGroup";
    private static final String ARTIFACT_ID = "ExportLoadSchema";
    private static final String AVRO_V1 = "{\"type\":\"record\",\"name\":\"User\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"}]}";
    private static final String AVRO_V2 = "{\"type\":\"record\",\"name\":\"User\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"},{\"name\":\"name\",\"type\":\"string\",\"default\":\"\"}]}";

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    void exportedDataIsLoadableByGitOpsRegistry() throws Exception {
        final var testRepository = GitTestRepositoryManager.getTestRepository();

        final byte[] exportZip = buildExportZip();
        extractZipToGitRepo(testRepository, exportZip);

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertEquals(Set.of(ARTIFACT_ID), withContext(() -> storage.getArtifactIds(10)));
        });

        // Groups
        final var groups = storage.getGroupIds(10);
        assertEquals(1, groups.size());
        assertTrue(groups.contains(GROUP_ID));
        final var groupMeta = storage.getGroupMetaData(GROUP_ID);
        assertEquals("A test group for export-load verification", groupMeta.getDescription());

        // Global rules
        assertEquals(Set.of(RuleType.VALIDITY), Set.copyOf(storage.getGlobalRules()));
        assertEquals("FULL", storage.getGlobalRule(RuleType.VALIDITY).getConfiguration());

        // Group rules
        assertEquals(Set.of(RuleType.INTEGRITY), Set.copyOf(storage.getGroupRules(GROUP_ID)));

        // Artifact metadata
        final var artifactMeta = storage.getArtifactMetaData(GROUP_ID, ARTIFACT_ID);
        assertEquals(ARTIFACT_ID, artifactMeta.getArtifactId());
        assertEquals("AVRO", artifactMeta.getArtifactType());
        assertEquals("User Schema", artifactMeta.getName());
        assertEquals("An Avro schema for users", artifactMeta.getDescription());

        // Artifact rules
        assertEquals(Set.of(RuleType.COMPATIBILITY),
                Set.copyOf(storage.getArtifactRules(GROUP_ID, ARTIFACT_ID)));
        assertEquals("BACKWARD",
                storage.getArtifactRule(GROUP_ID, ARTIFACT_ID, RuleType.COMPATIBILITY).getConfiguration());

        // Version 1 content
        final var v1Content = storage.getArtifactVersionContent(GROUP_ID, ARTIFACT_ID, "1");
        assertNotNull(v1Content.getContent());
        final String v1Json = v1Content.getContent().content();
        assertTrue(v1Json.contains("User"), "V1 should contain schema name 'User': " + v1Json);

        // Version 2 content and metadata
        final var v2Content = storage.getArtifactVersionContent(GROUP_ID, ARTIFACT_ID, "2");
        assertNotNull(v2Content.getContent());
        final String v2Json = v2Content.getContent().content();
        assertTrue(v2Json.contains("User"), "V2 should contain schema name 'User': " + v2Json);
        assertTrue(v2Json.contains("name"), "V2 should contain the 'name' field: " + v2Json);
        final var v2Meta = storage.getArtifactVersionMetaData(GROUP_ID, ARTIFACT_ID, "2");
        assertEquals("Version 2", v2Meta.getName());
        assertEquals("Added name field", v2Meta.getDescription());

        // Latest branch resolves to version 2
        final var latest = storage.getBranchTip(
                new GA(GROUP_ID, ARTIFACT_ID), BranchId.LATEST,
                RegistryStorage.RetrievalBehavior.SKIP_DISABLED_LATEST);
        assertEquals("2", latest.getRawVersionId());
    }

    private byte[] buildExportZip() throws Exception {
        final var collector = new GitOpsEntityCollector();
        final Map<Long, String> contentIdToPath = new HashMap<>();

        final var baos = new ByteArrayOutputStream();
        try (final var zip = new ZipOutputStream(baos)) {
            // Write content files first (matching GitOpsExportStrategy streaming order)
            writeContentFile(zip, contentIdToPath, 1001L, AVRO_V1, "AVRO");
            writeContentFile(zip, contentIdToPath, 1002L, AVRO_V2, "AVRO");

            // Collect metadata entities (same as GitOpsExportStrategy.handleEntity for non-content)
            final var content1 = new ContentEntity();
            content1.contentId = 1001L;
            content1.contentType = "application/json";
            collector.collect(content1);

            final var content2 = new ContentEntity();
            content2.contentId = 1002L;
            content2.contentType = "application/json";
            collector.collect(content2);

            final var globalRule = new GlobalRuleEntity();
            globalRule.ruleType = RuleType.VALIDITY;
            globalRule.configuration = "FULL";
            collector.collect(globalRule);

            final var group = new GroupEntity();
            group.groupId = GROUP_ID;
            group.description = "A test group for export-load verification";
            group.createdOn = System.currentTimeMillis();
            group.modifiedOn = System.currentTimeMillis();
            collector.collect(group);

            final var groupRule = new GroupRuleEntity();
            groupRule.groupId = GROUP_ID;
            groupRule.type = RuleType.INTEGRITY;
            groupRule.configuration = "ALL_REFS_MAPPED";
            collector.collect(groupRule);

            final var artifact = new ArtifactEntity();
            artifact.groupId = GROUP_ID;
            artifact.artifactId = ARTIFACT_ID;
            artifact.artifactType = "AVRO";
            artifact.name = "User Schema";
            artifact.description = "An Avro schema for users";
            artifact.createdOn = System.currentTimeMillis();
            artifact.modifiedOn = System.currentTimeMillis();
            collector.collect(artifact);

            final var version1 = new ArtifactVersionEntity();
            version1.groupId = GROUP_ID;
            version1.artifactId = ARTIFACT_ID;
            version1.version = "1";
            version1.versionOrder = 1;
            version1.globalId = 5001L;
            version1.contentId = 1001L;
            version1.createdOn = System.currentTimeMillis();
            version1.modifiedOn = System.currentTimeMillis();
            collector.collect(version1);

            final var version2 = new ArtifactVersionEntity();
            version2.groupId = GROUP_ID;
            version2.artifactId = ARTIFACT_ID;
            version2.version = "2";
            version2.versionOrder = 2;
            version2.globalId = 5002L;
            version2.contentId = 1002L;
            version2.name = "Version 2";
            version2.description = "Added name field";
            version2.createdOn = System.currentTimeMillis();
            version2.modifiedOn = System.currentTimeMillis();
            collector.collect(version2);

            final var artifactRule = new ArtifactRuleEntity();
            artifactRule.groupId = GROUP_ID;
            artifactRule.artifactId = ARTIFACT_ID;
            artifactRule.type = RuleType.COMPATIBILITY;
            artifactRule.configuration = "BACKWARD";
            collector.collect(artifactRule);

            // Write YAML metadata with registryId matching the test profile
            final var writer = new GitOpsZipWriter(zip, collector, contentIdToPath, REGISTRY_ID);
            writer.write();
        }
        return baos.toByteArray();
    }

    private void writeContentFile(ZipOutputStream zip, Map<Long, String> contentIdToPath,
            long contentId, String content, String artifactType) throws Exception {
        final String ext = "AVRO".equals(artifactType) ? ".avsc" : ".json";
        final String path = "content/" + contentId + ext;
        contentIdToPath.put(contentId, path);
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void extractZipToGitRepo(GitTestRepository repo, byte[] zipBytes) throws Exception {
        final var repoDir = new File(repo.getGitRepoUrl());

        // Clear existing files (except .git)
        final var files = FileUtils.listFiles(repoDir, null, true);
        for (final File f : files) {
            final var gitDir = Path.of(repoDir.getPath(), ".git");
            if (!f.toPath().startsWith(gitDir)) {
                FileUtils.delete(f);
            }
        }

        // Extract ZIP entries
        try (final var zis = new ZipInputStream(
                new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                final var target = repoDir.toPath().resolve(entry.getName());
                Files.createDirectories(target.getParent());
                Files.write(target, zis.readAllBytes());
                zis.closeEntry();
            }
        }

        // Git add + commit to trigger poller
        try (final var git = org.eclipse.jgit.api.Git.open(repoDir)) {
            git.add().setUpdate(true).addFilepattern(".").call();
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Load exported data").call();
        }
    }

    @ActivateRequestContext
    public <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }
}
