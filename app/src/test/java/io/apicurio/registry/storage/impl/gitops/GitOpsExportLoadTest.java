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
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GitOpsExportLoadTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    Logger log;

    @Test
    @Order(1)
    void exportedDataIsLoadableByGitOpsRegistry() throws Exception {
        final var testRepository = GitTestRepositoryManager.getTestRepository();

        // Load fixture data: JSON OpenAPI + Avro with reference + Avro referenced schema
        testRepository.load("git/export-roundtrip");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertEquals(Set.of("petstore-json", "address", "person", "empty-artifact"),
                    withContext(() -> storage.getArtifactIds(10)));
        });

        // Capture original IDs for round-trip verification
        final var originalJsonOpenApi = storage.getArtifactVersionContent("testgroup", "petstore-json", "1");
        final long jsonContentId = originalJsonOpenApi.getContentId();
        final long jsonGlobalId = originalJsonOpenApi.getGlobalId();

        final var originalPerson = storage.getArtifactVersionContent("testgroup", "person", "1");
        final long personContentId = originalPerson.getContentId();
        final long personGlobalId = originalPerson.getGlobalId();

        final var originalPersonV2 = storage.getArtifactVersionContent("testgroup", "person", "2");
        final long personV2ContentId = originalPersonV2.getContentId();
        final long personV2GlobalId = originalPersonV2.getGlobalId();

        final var originalAddress = storage.getArtifactVersionContent("testgroup", "address", "1");
        final long addressContentId = originalAddress.getContentId();
        final long addressGlobalId = originalAddress.getGlobalId();

        final var originalAddressV2 = storage.getArtifactVersionContent("testgroup", "address", "2");
        final long addressV2ContentId = originalAddressV2.getContentId();
        final long addressV2GlobalId = originalAddressV2.getGlobalId();

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
            assertEquals(Set.of("petstore-json", "address", "person", "empty-artifact"),
                    withContext(() -> storage.getArtifactIds(10)));
        });

        verifyGroupAndRules();
        verifyArtifact("petstore-json", "1", "OPENAPI", "Petstore JSON API", jsonContentId, jsonGlobalId);
        verifyArtifact("address", "1", "AVRO", "Address Schema", addressContentId, addressGlobalId);
        verifyArtifact("address", "2", "AVRO", "Address Schema", addressV2ContentId, addressV2GlobalId);
        verifyArtifact("person", "1", "AVRO", "Person Schema", personContentId, personGlobalId);
        verifyArtifact("person", "2", "AVRO", "Person Schema", personV2ContentId, personV2GlobalId);
        verifyReference(personContentId, "testgroup", "address", "1", "com.example.Address");
        verifyReference(personV2ContentId, "testgroup", "address", "2", "com.example.Address");
        // R13: person v1 and v2 have the same bytes but different references,
        // so they must get distinct contentIds after the round-trip.
        assertNotEquals(personContentId, personV2ContentId,
                "Same bytes with different references must produce distinct contentIds");
        verifyLatestBranch("1", "petstore-json");
        verifyLatestBranch("2", "address", "person");

        final var emptyMeta = storage.getArtifactMetaData("testgroup", "empty-artifact");
        assertEquals("JSON", emptyMeta.getArtifactType());
        assertEquals("Empty Artifact", emptyMeta.getName());

        assertTrue(storage.getGroupIds(10).contains("emptygroup"),
                "Empty group must survive export round-trip");
        assertEquals("Group with no artifacts",
                storage.getGroupMetaData("emptygroup").getDescription());
        assertEquals("BACKWARD",
                storage.getGroupRule("emptygroup", RuleType.COMPATIBILITY).getConfiguration(),
                "Empty group rules must survive export round-trip");
    }

    private void verifyGroupAndRules() {
        assertTrue(storage.getGroupIds(10).contains("testgroup"));
        assertEquals("Test group for export round-trip",
                storage.getGroupMetaData("testgroup").getDescription());
        assertEquals(Set.of(RuleType.VALIDITY), Set.copyOf(storage.getGlobalRules()));
        assertEquals("FULL", storage.getGlobalRule(RuleType.VALIDITY).getConfiguration());
    }

    private void verifyArtifact(String artifactId, String version, String expectedType, String expectedName,
            long expectedContentId, long expectedGlobalId) {
        final var meta = storage.getArtifactMetaData("testgroup", artifactId);
        assertEquals(expectedType, meta.getArtifactType());
        assertEquals(expectedName, meta.getName());
        final var content = storage.getArtifactVersionContent("testgroup", artifactId, version);
        assertNotNull(content.getContent());
        assertEquals(expectedContentId, content.getContentId(),
                artifactId + " v" + version + " contentId must round-trip");
        assertEquals(expectedGlobalId, content.getGlobalId(),
                artifactId + " v" + version + " globalId must round-trip");
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

    private void verifyLatestBranch(String expectedVersion, String... artifactIds) {
        for (final String artifactId : artifactIds) {
            assertEquals(expectedVersion, storage.getBranchTip(new GA("testgroup", artifactId),
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
        try (final var git = Git.open(repoDir)) {
            git.add().setUpdate(true).addFilepattern(".").call();
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Load exported data").call();
        }
    }

    @Test
    @Order(2)
    void versionlessArtifactWithoutTypeFails() throws Exception {
        final var testRepository = GitTestRepositoryManager.getTestRepository();

        // Load a valid fixture first so the registry is in a known good state
        testRepository.load("git/smoke01");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/status")
                    .then()
                    .statusCode(200)
                    .body("syncState", equalTo("IDLE"));
        });

        // Load a fixture with a versionless artifact that has no artifactType.
        // The load must fail with an error referencing the missing artifactType.
        testRepository.load("git/versionless-no-type");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/status")
                    .then()
                    .statusCode(200)
                    .body("syncState", equalTo("ERROR"))
                    .body("errors.detail", hasItem(containsString("must specify artifactType explicitly")));
        });
    }

    @ActivateRequestContext
    public <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }
}
