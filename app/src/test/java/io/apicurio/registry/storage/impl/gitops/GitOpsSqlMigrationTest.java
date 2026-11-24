package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
public class GitOpsSqlMigrationTest {

    private static final Set<String> ALL_ARTIFACT_IDS =
            Set.of("simple-json", "address", "person", "openapi-spec");

    @Inject
    @Current
    RegistryStorage storage;

    @ConfigProperty(name = "quarkus.http.test-port")
    int testPort;

    @Test
    void sqlToGitOpsMigrationRoundTrip() throws Exception {
        final var testRepository = GitTestRepositoryManager.getTestRepository();

        testRepository.load("git/sql-migration");
        awaitArtifacts(ALL_ARTIFACT_IDS);

        // Capture original sequential IDs before the round-trip
        final Map<String, long[]> originalIds = captureIds();

        // Export via the REST endpoint, clear, and reload
        final byte[] exportZip = exportViaRest();
        assertTrue(exportZip.length > 0, "Export ZIP should not be empty");

        testRepository.load("git/empty");
        awaitArtifacts(Set.of());

        extractZipToGitRepo(testRepository, exportZip);
        awaitArtifacts(ALL_ARTIFACT_IDS);

        // Verify all IDs survived the round-trip
        for (final var entry : originalIds.entrySet()) {
            final String[] parts = entry.getKey().split("/");
            final long[] ids = entry.getValue();
            verifyArtifactIds(parts[0], parts[1], parts[2], ids[0], ids[1]);
        }

        // Same bytes with different refs must keep distinct contentIds after round-trip
        final long personV1ContentId =
                storage.getArtifactVersionContent("sqlgroup", "person", "1").getContentId();
        final long personV2ContentId =
                storage.getArtifactVersionContent("sqlgroup", "person", "2").getContentId();
        assertNotEquals(personV1ContentId, personV2ContentId,
                "Same bytes with different references must produce distinct contentIds after round-trip");

        verifyArtifactMetadata();
        verifyContentBytes();
        verifyReferences(originalIds);
        verifyLatestBranches();
        verifyRules();

        assertNotNull(storage.getArtifactMetaData("default", "simple-json"),
                "Default group artifact must survive migration round-trip");
    }

    private Map<String, long[]> captureIds() {
        return Map.of(
                "default/simple-json/1", idsOf("default", "simple-json", "1"),
                "sqlgroup/address/1", idsOf("sqlgroup", "address", "1"),
                "sqlgroup/address/2", idsOf("sqlgroup", "address", "2"),
                "sqlgroup/person/1", idsOf("sqlgroup", "person", "1"),
                "sqlgroup/person/2", idsOf("sqlgroup", "person", "2"),
                "sqlgroup/openapi-spec/1", idsOf("sqlgroup", "openapi-spec", "1"));
    }

    private long[] idsOf(String groupId, String artifactId, String version) {
        final var content = storage.getArtifactVersionContent(groupId, artifactId, version);
        return new long[]{content.getContentId(), content.getGlobalId()};
    }

    private void awaitArtifacts(Set<String> expected) {
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertEquals(expected, withContext(() -> storage.getArtifactIds(10)));
        });
    }

    private void verifyArtifactIds(String groupId, String artifactId, String version,
            long expectedContentId, long expectedGlobalId) {
        final var content = storage.getArtifactVersionContent(groupId, artifactId, version);
        assertNotNull(content.getContent());
        assertEquals(expectedContentId, content.getContentId(),
                groupId + "/" + artifactId + " v" + version + " contentId must round-trip");
        assertEquals(expectedGlobalId, content.getGlobalId(),
                groupId + "/" + artifactId + " v" + version + " globalId must round-trip");
    }

    private void verifyArtifactMetadata() {
        for (final var entry : Map.of(
                "default/simple-json", new String[]{"JSON", "Simple JSON Schema"},
                "sqlgroup/address", new String[]{"AVRO", "Address Schema"},
                "sqlgroup/person", new String[]{"AVRO", "Person Schema"},
                "sqlgroup/openapi-spec", new String[]{"OPENAPI", "Petstore API"}).entrySet()) {
            final String[] parts = entry.getKey().split("/");
            final var meta = storage.getArtifactMetaData(parts[0], parts[1]);
            assertEquals(entry.getValue()[0], meta.getArtifactType(),
                    entry.getKey() + " artifactType must round-trip");
            assertEquals(entry.getValue()[1], meta.getName(),
                    entry.getKey() + " name must round-trip");
        }
    }

    private void verifyContentBytes() {
        assertTrue(storage.getArtifactVersionContent("sqlgroup", "address", "1")
                        .getContent().content().contains("\"name\":\"Address\""),
                "Address v1 content bytes must survive round-trip");
        assertTrue(storage.getArtifactVersionContent("sqlgroup", "openapi-spec", "1")
                        .getContent().content().contains("\"title\":\"Petstore\""),
                "Petstore content bytes must survive round-trip");
    }

    private void verifyReferences(Map<String, long[]> ids) {
        final long personV1ContentId = ids.get("sqlgroup/person/1")[0];
        final long personV2ContentId = ids.get("sqlgroup/person/2")[0];
        verifyReference(personV1ContentId, "sqlgroup", "address", "1", "com.example.Address");
        verifyReference(personV2ContentId, "sqlgroup", "address", "2", "com.example.Address");
    }

    private void verifyReference(long contentId, String expectedGroupId, String expectedArtifactId,
            String expectedVersion, String expectedName) {
        final List<ArtifactReferenceDto> refs = storage.getContentById(contentId).getReferences();
        assertEquals(1, refs.size(), "Content " + contentId + " should have exactly 1 reference");
        final var ref = refs.get(0);
        assertEquals(expectedGroupId, ref.getGroupId());
        assertEquals(expectedArtifactId, ref.getArtifactId());
        assertEquals(expectedVersion, ref.getVersion());
        assertEquals(expectedName, ref.getName());
    }

    private void verifyLatestBranches() {
        for (final var entry : Map.of(
                "default/simple-json", "1",
                "sqlgroup/address", "2",
                "sqlgroup/person", "2",
                "sqlgroup/openapi-spec", "1").entrySet()) {
            final String[] parts = entry.getKey().split("/");
            assertEquals(entry.getValue(),
                    storage.getBranchTip(new GA(parts[0], parts[1]),
                            BranchId.LATEST, RegistryStorage.RetrievalBehavior.SKIP_DISABLED_LATEST)
                            .getRawVersionId(),
                    entry.getKey() + " latest branch should resolve");
        }
    }

    private void verifyRules() {
        assertEquals(Set.of(RuleType.VALIDITY), Set.copyOf(storage.getGlobalRules()));
        assertEquals("SYNTAX_ONLY", storage.getGlobalRule(RuleType.VALIDITY).getConfiguration());
        assertTrue(storage.getGroupIds(10).contains("sqlgroup"));
        assertEquals("Group for SQL migration test",
                storage.getGroupMetaData("sqlgroup").getDescription());
        assertEquals("BACKWARD",
                storage.getGroupRule("sqlgroup", RuleType.COMPATIBILITY).getConfiguration());
        assertEquals("FULL",
                storage.getArtifactRule("sqlgroup", "openapi-spec", RuleType.VALIDITY)
                        .getConfiguration());
    }

    private byte[] exportViaRest() throws Exception {
        final String exportUrl = String.format(
                "http://localhost:%d/apis/registry/v3/admin/export?format=gitops-v0", testPort);
        final HttpURLConnection conn = (HttpURLConnection) URI.create(exportUrl).toURL()
                .openConnection();
        try {
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(60_000);
            conn.setRequestProperty("Accept", "application/zip");
            final int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException(
                        "Export failed with HTTP " + responseCode + ": " + conn.getResponseMessage());
            }
            try (final InputStream in = conn.getInputStream()) {
                return in.readAllBytes();
            }
        } finally {
            conn.disconnect();
        }
    }

    private void extractZipToGitRepo(GitTestRepository repo, byte[] zipBytes) throws Exception {
        final var repoDir = new File(repo.getGitRepoUrl());

        final var files = FileUtils.listFiles(repoDir, null, true);
        for (final File f : files) {
            final var gitDir = Path.of(repoDir.getPath(), ".git");
            if (!f.toPath().startsWith(gitDir)) {
                FileUtils.delete(f);
            }
        }

        try (final var zis = new ZipInputStream(
                new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                final var target = repoDir.toPath().resolve(entry.getName()).normalize();
                if (!target.startsWith(repoDir.toPath().normalize())) {
                    throw new IOException("ZipEntry escapes target directory: " + entry.getName());
                }
                Files.createDirectories(target.getParent());
                Files.write(target, zis.readAllBytes());
                zis.closeEntry();
            }
        }

        final var registryYaml = repoDir.toPath().resolve("registry.registry.yaml");
        if (Files.exists(registryYaml)) {
            final String content = Files.readString(registryYaml, StandardCharsets.UTF_8);
            Files.writeString(registryYaml,
                    content.replace("registryId: \"main\"", "registryId: \"test\""),
                    StandardCharsets.UTF_8);
        }

        try (final var git = Git.open(repoDir)) {
            git.add().setUpdate(true).addFilepattern(".").call();
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Load exported migration data").call();
        }
    }

    @ActivateRequestContext
    public <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }
}
