package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.rest.v3.impl.shared.gitops.GitOpsExportStrategy;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
public class GitOpsExportLoadTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    Logger log;

    @Test
    void exportedDataIsLoadableByGitOpsRegistry() throws Exception {
        final var testRepository = GitTestRepositoryManager.getTestRepository();

        // Load fixture data: JSON OpenAPI + Avro with reference + Avro referenced schema
        testRepository.load("git/export-roundtrip");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertEquals(Set.of("petstore-json", "address", "person"),
                    withContext(() -> storage.getArtifactIds(10)));
        });

        // Capture original IDs for round-trip verification
        final var originalJsonOpenApi = storage.getArtifactVersionContent("testgroup", "petstore-json", "1");
        final long jsonContentId = originalJsonOpenApi.getContentId();
        final long jsonGlobalId = originalJsonOpenApi.getGlobalId();

        final var originalPerson = storage.getArtifactVersionContent("testgroup", "person", "1");
        final long personContentId = originalPerson.getContentId();
        final long personGlobalId = originalPerson.getGlobalId();

        final var originalAddress = storage.getArtifactVersionContent("testgroup", "address", "1");
        final long addressContentId = originalAddress.getContentId();

        // Export via GitOpsExportStrategy — the same code path the REST endpoint uses
        final byte[] exportZip = exportViaStrategy();

        // Clear the registry
        testRepository.load("git/empty");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(Set.of(), withContext(() -> storage.getArtifactIds(10)));
        });

        // Load the exported data into the git repo
        extractZipToGitRepo(testRepository, exportZip);
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertEquals(Set.of("petstore-json", "address", "person"),
                    withContext(() -> storage.getArtifactIds(10)));
        });

        verifyGroupAndRules();
        verifyArtifact("petstore-json", "OPENAPI", "Petstore JSON API", jsonContentId, jsonGlobalId);
        verifyArtifact("address", "AVRO", "Address Schema", addressContentId, 0);
        verifyArtifact("person", "AVRO", "Person Schema", personContentId, personGlobalId);
        verifyReference(personContentId, "testgroup", "address", "1", "com.example.Address");
        verifyLatestBranch("petstore-json", "address", "person");
    }

    private void verifyGroupAndRules() {
        assertTrue(storage.getGroupIds(10).contains("testgroup"));
        assertEquals("Test group for export round-trip",
                storage.getGroupMetaData("testgroup").getDescription());
        assertEquals(Set.of(RuleType.VALIDITY), Set.copyOf(storage.getGlobalRules()));
        assertEquals("FULL", storage.getGlobalRule(RuleType.VALIDITY).getConfiguration());
    }

    private void verifyArtifact(String artifactId, String expectedType, String expectedName,
            long expectedContentId, long expectedGlobalId) {
        final var meta = storage.getArtifactMetaData("testgroup", artifactId);
        assertEquals(expectedType, meta.getArtifactType());
        assertEquals(expectedName, meta.getName());
        final var content = storage.getArtifactVersionContent("testgroup", artifactId, "1");
        assertNotNull(content.getContent());
        assertEquals(expectedContentId, content.getContentId(), artifactId + " contentId must round-trip");
        if (expectedGlobalId > 0) {
            assertEquals(expectedGlobalId, content.getGlobalId(), artifactId + " globalId must round-trip");
        }
    }

    private void verifyReference(long contentId, String expectedGroupId, String expectedArtifactId,
            String expectedVersion, String expectedName) {
        final List<ArtifactReferenceDto> refs = storage.getContentById(contentId).getReferences();
        assertFalse(refs.isEmpty(), "Content should have references");
        final var ref = refs.get(0);
        assertEquals(expectedGroupId, ref.getGroupId());
        assertEquals(expectedArtifactId, ref.getArtifactId());
        assertEquals(expectedVersion, ref.getVersion());
        assertEquals(expectedName, ref.getName());
    }

    private void verifyLatestBranch(String... artifactIds) {
        for (final String artifactId : artifactIds) {
            assertEquals("1", storage.getBranchTip(new GA("testgroup", artifactId),
                    BranchId.LATEST, RegistryStorage.RetrievalBehavior.SKIP_DISABLED_LATEST)
                    .getRawVersionId(), artifactId + " latest branch should resolve");
        }
    }

    private byte[] exportViaStrategy() {
        final var baos = new ByteArrayOutputStream();
        try (final var zip = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            final var strategy = new GitOpsExportStrategy(log);
            strategy.init(zip);
            storage.exportData(null, entity -> {
                try {
                    strategy.handleEntity(entity);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            strategy.finish();
            zip.flush();
        } catch (Exception e) {
            throw new RuntimeException("Export failed", e);
        }
        return baos.toByteArray();
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

        // The export writes registryId="main" but the test profile uses "test".
        final var registryYaml = repoDir.toPath().resolve("registry.registry.yaml");
        if (Files.exists(registryYaml)) {
            final String content = Files.readString(registryYaml, StandardCharsets.UTF_8);
            Files.writeString(registryYaml,
                    content.replace("registryId: \"main\"", "registryId: \"test\""),
                    StandardCharsets.UTF_8);
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
