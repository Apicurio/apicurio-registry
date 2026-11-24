package io.apicurio.tests.migration;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.vertx.core.Vertx;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.BindMode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.apicurio.deployment.Constants.MIGRATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end migration test across two registry instances.
 * Starts a SQL-backed (in-memory) registry, populates it, exports
 * with format=gitops-v0, then starts a GitOps-configured registry
 * pointing at the exported Git repo and verifies all data round-trips.
 *
 * Requires a local registry image. Build with:
 *   mvn clean install -pl distro/docker -am -DskipTests
 *   podman build -f distro/docker/target/docker/Dockerfile.jvm \
 *     -t apicurio-registry:local distro/docker/target/docker
 *
 * Override the image name with -Dregistry.test.image=...
 */
@Tag(MIGRATION)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GitOpsMigrationIT {

    private static final Logger log = LoggerFactory.getLogger(GitOpsMigrationIT.class);

    private static final String REGISTRY_IMAGE = System.getProperty(
            "registry.test.image", "apicurio-registry:local");

    private static final String ADDRESS_V1 =
            "{\"type\":\"record\",\"name\":\"Address\",\"namespace\":\"com.example\","
                    + "\"fields\":[{\"name\":\"street\",\"type\":\"string\"},{\"name\":\"city\",\"type\":\"string\"}]}";
    private static final String ADDRESS_V2 =
            "{\"type\":\"record\",\"name\":\"Address\",\"namespace\":\"com.example\","
                    + "\"fields\":[{\"name\":\"street\",\"type\":\"string\"},{\"name\":\"city\",\"type\":\"string\"},"
                    + "{\"name\":\"zip\",\"type\":\"string\"}]}";
    private static final String PERSON =
            "{\"type\":\"record\",\"name\":\"Person\",\"namespace\":\"com.example\","
                    + "\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"address\",\"type\":\"com.example.Address\"}]}";
    private static final String PETSTORE_JSON =
            "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Petstore\",\"version\":\"1.0.0\"},"
                    + "\"paths\":{\"/pets\":{\"get\":{\"operationId\":\"listPets\",\"summary\":\"List all pets\","
                    + "\"responses\":{\"200\":{\"description\":\"A list of pets\"}}}}}}";

    private GenericContainer<?> sqlRegistry;
    private GenericContainer<?> gitopsRegistry;
    private Vertx vertx;
    private Path tempDir;

    @AfterAll
    void tearDown() {
        if (gitopsRegistry != null && gitopsRegistry.isRunning()) {
            gitopsRegistry.stop();
        }
        if (sqlRegistry != null && sqlRegistry.isRunning()) {
            sqlRegistry.stop();
        }
        if (vertx != null) {
            vertx.close();
        }
        if (tempDir != null) {
            try {
                org.apache.commons.io.FileUtils.deleteDirectory(tempDir.toFile());
            } catch (Exception e) {
                log.warn("Failed to clean up temp dir: {}", tempDir, e);
            }
        }
    }

    @Test
    void sqlToGitOpsMigration() throws Exception {
        vertx = Vertx.vertx();
        tempDir = Files.createTempDirectory(
                Path.of(System.getProperty("user.home")), "gitops-migration-test-");

        // 1. Start SQL-backed (in-memory) registry
        sqlRegistry = new GenericContainer<>(REGISTRY_IMAGE)
                .withEnv("QUARKUS_HTTP_PORT", "8080")
                .withEnv("APICURIO_REST_DELETION_ARTIFACT_ENABLED", "true")
                .withExposedPorts(8080, 9000)
                .waitingFor(Wait.forHttp("/health/live").forPort(9000)
                        .withStartupTimeout(Duration.ofSeconds(60)));
        try {
            sqlRegistry.start();
        } catch (Exception e) {
            Assumptions.abort("Registry image '" + REGISTRY_IMAGE + "' not available: " + e.getMessage());
        }
        sqlRegistry.followOutput(new Slf4jLogConsumer(log).withPrefix("sql-registry"));

        final int sqlPort = sqlRegistry.getMappedPort(8080);
        final String sqlApiUrl = TestUtils.getRegistryV3ApiUrl(sqlPort);
        final RegistryClient sqlClient = RegistryClientFactory.create(
                RegistryClientOptions.create(sqlApiUrl, vertx).requestTimeout(10_000, 60_000).retry());

        TestUtils.retry(() -> sqlClient.groups().byGroupId("default").artifacts().get());

        // 2. Populate the SQL registry
        final var populatedData = populateSqlRegistry(sqlClient);

        // 3. Export via REST endpoint with format=gitops-v0
        final String exportUrl = String.format(
                "http://localhost:%d/apis/registry/v3/admin/export?format=gitops-v0", sqlPort);
        final byte[] exportZip = downloadZip(exportUrl);
        assertNotNull(exportZip);
        assertTrue(exportZip.length > 0, "Export ZIP must not be empty");
        log.info("Exported {} bytes from SQL registry", exportZip.length);

        // 4. Extract ZIP into a Git repository
        final Path gitRepoDir = tempDir.resolve("gitops-repo");
        Files.createDirectories(gitRepoDir);
        extractZipToDir(exportZip, gitRepoDir);
        patchRegistryId(gitRepoDir, "main");
        initGitRepo(gitRepoDir);

        // 5. Start GitOps-configured registry pointing at the Git repo.
        gitopsRegistry = new GenericContainer<>(REGISTRY_IMAGE)
                .withEnv("APICURIO_STORAGE_KIND", "gitops")
                .withEnv("APICURIO_FEATURES_EXPERIMENTAL_ENABLED", "true")
                .withEnv("APICURIO_POLLING_STORAGE_ID", "main")
                .withEnv("APICURIO_GITOPS_WORKSPACE", "/repos")
                .withEnv("APICURIO_GITOPS_REPO_DIR", "data")
                .withEnv("APICURIO_GITOPS_REPO_BRANCH", "main")
                .withEnv("APICURIO_POLLING_STORAGE_POLL_PERIOD", "PT1S")
                .withEnv("APICURIO_POLLING_STORAGE_DEBOUNCE_QUIET_PERIOD", "PT0S")
                .withEnv("APICURIO_POLLING_STORAGE_DEBOUNCE_MAX_WAIT_PERIOD", "PT0S")
                .withFileSystemBind(gitRepoDir.toString(), "/repos/data", BindMode.READ_ONLY)
                .withExposedPorts(8080, 9000)
                .waitingFor(Wait.forHttp("/health/live").forPort(9000)
                        .withStartupTimeout(Duration.ofSeconds(90)));
        gitopsRegistry.start();
        gitopsRegistry.followOutput(new Slf4jLogConsumer(log).withPrefix("gitops-registry"));

        final int gitopsPort = gitopsRegistry.getMappedPort(8080);
        final String gitopsApiUrl = TestUtils.getRegistryV3ApiUrl(gitopsPort);
        final RegistryClient gitopsClient = RegistryClientFactory.create(
                RegistryClientOptions.create(gitopsApiUrl, vertx).requestTimeout(10_000, 60_000).retry());

        // 6. Wait for GitOps registry to sync and verify all data
        log.info("Waiting for GitOps registry to load exported data...");
        TestUtils.retry(() -> {
            final var results = gitopsClient.search().artifacts().get(q -> q.queryParameters.limit = 100);
            assertEquals(3, results.getCount(), "GitOps registry should have 3 artifacts");
        });

        verifyMigratedData(gitopsClient, populatedData);
        log.info("SQL-to-GitOps migration verified successfully");
    }

    private PopulatedData populateSqlRegistry(RegistryClient client) throws Exception {
        // Create groups
        final var createGroup = new CreateGroup();
        createGroup.setGroupId("migrationtest");
        createGroup.setDescription("Group for migration testing");
        client.groups().post(createGroup);

        // Create address v1
        final var addressV1Response = createArtifactWithContent(client, "migrationtest", "address",
                ArtifactType.AVRO, ADDRESS_V1, ContentTypes.APPLICATION_JSON, Collections.emptyList());
        final long addressV1GlobalId = addressV1Response.getVersion().getGlobalId();
        final long addressV1ContentId = addressV1Response.getVersion().getContentId();

        // Create address v2
        final var addressV2Version = createVersionWithContent(client, "migrationtest", "address",
                ADDRESS_V2, ContentTypes.APPLICATION_JSON, Collections.emptyList());
        final long addressV2GlobalId = addressV2Version.getGlobalId();
        final long addressV2ContentId = addressV2Version.getContentId();

        // Create person v1 (references address v1)
        final var personRef1 = new ArtifactReference();
        personRef1.setGroupId("migrationtest");
        personRef1.setArtifactId("address");
        personRef1.setVersion("1");
        personRef1.setName("com.example.Address");

        final var personV1Response = createArtifactWithContent(client, "migrationtest", "person",
                ArtifactType.AVRO, PERSON, ContentTypes.APPLICATION_JSON, List.of(personRef1));
        final long personV1GlobalId = personV1Response.getVersion().getGlobalId();
        final long personV1ContentId = personV1Response.getVersion().getContentId();

        // Create person v2 — same bytes, different reference
        final var personRef2 = new ArtifactReference();
        personRef2.setGroupId("migrationtest");
        personRef2.setArtifactId("address");
        personRef2.setVersion("2");
        personRef2.setName("com.example.Address");

        final var personV2Version = createVersionWithContent(client, "migrationtest", "person",
                PERSON, ContentTypes.APPLICATION_JSON, List.of(personRef2));
        final long personV2GlobalId = personV2Version.getGlobalId();
        final long personV2ContentId = personV2Version.getContentId();

        // Same bytes with different references must produce distinct contentIds in SQL
        assertNotEquals(personV1ContentId, personV2ContentId,
                "SQL must assign distinct contentIds for same bytes with different references");

        // Create OpenAPI artifact in a second named group.
        // Note: artifacts in the implicit default group (groupId=null in SQL) are
        // exported with groupId="default" but without a group definition YAML, so
        // the GitOps loader rejects them (#11425). Use a named group here.
        final var apiGroup = new CreateGroup();
        apiGroup.setGroupId("apigroup");
        apiGroup.setDescription("Group for API definitions");
        client.groups().post(apiGroup);

        final var openapiResponse = createArtifactWithContent(client, "apigroup", "petstore",
                ArtifactType.OPENAPI, PETSTORE_JSON, ContentTypes.APPLICATION_JSON, Collections.emptyList());
        final long openapiGlobalId = openapiResponse.getVersion().getGlobalId();
        final long openapiContentId = openapiResponse.getVersion().getContentId();

        // Set rules AFTER all artifacts are created so compatibility checks don't
        // block version creation (the migration test verifies rules survive, not that
        // they were enforced during population)
        final var globalRule = new CreateRule();
        globalRule.setRuleType(RuleType.VALIDITY);
        globalRule.setConfig("SYNTAX_ONLY");
        client.admin().rules().post(globalRule);

        final var groupRule = new CreateRule();
        groupRule.setRuleType(RuleType.COMPATIBILITY);
        groupRule.setConfig("BACKWARD");
        client.groups().byGroupId("migrationtest").rules().post(groupRule);

        final var artifactRule = new CreateRule();
        artifactRule.setRuleType(RuleType.VALIDITY);
        artifactRule.setConfig("FULL");
        client.groups().byGroupId("apigroup").artifacts().byArtifactId("petstore")
                .rules().post(artifactRule);

        log.info("Populated SQL registry: addressV1(g={},c={}), addressV2(g={},c={}), "
                        + "personV1(g={},c={}), personV2(g={},c={}), petstore(g={},c={})",
                addressV1GlobalId, addressV1ContentId,
                addressV2GlobalId, addressV2ContentId,
                personV1GlobalId, personV1ContentId,
                personV2GlobalId, personV2ContentId,
                openapiGlobalId, openapiContentId);

        return new PopulatedData(
                addressV1GlobalId, addressV1ContentId,
                addressV2GlobalId, addressV2ContentId,
                personV1GlobalId, personV1ContentId,
                personV2GlobalId, personV2ContentId,
                openapiGlobalId, openapiContentId);
    }

    private void verifyMigratedData(RegistryClient client, PopulatedData data) throws Exception {
        // Verify artifacts exist with correct types
        final var addressMeta = client.groups().byGroupId("migrationtest")
                .artifacts().byArtifactId("address").get();
        assertEquals("AVRO", addressMeta.getArtifactType());

        final var personMeta = client.groups().byGroupId("migrationtest")
                .artifacts().byArtifactId("person").get();
        assertEquals("AVRO", personMeta.getArtifactType());

        final var petstoreMeta = client.groups().byGroupId("apigroup")
                .artifacts().byArtifactId("petstore").get();
        assertEquals("OPENAPI", petstoreMeta.getArtifactType());

        // Verify version content IDs round-tripped
        verifyVersionId(client, "migrationtest", "address", "1",
                data.addressV1GlobalId, data.addressV1ContentId);
        verifyVersionId(client, "migrationtest", "address", "2",
                data.addressV2GlobalId, data.addressV2ContentId);
        verifyVersionId(client, "migrationtest", "person", "1",
                data.personV1GlobalId, data.personV1ContentId);
        verifyVersionId(client, "migrationtest", "person", "2",
                data.personV2GlobalId, data.personV2ContentId);
        verifyVersionId(client, "apigroup", "petstore", "1",
                data.openapiGlobalId, data.openapiContentId);

        // Same bytes with different references must preserve distinct contentIds
        assertNotEquals(data.personV1ContentId, data.personV2ContentId,
                "Person v1 and v2 contentIds must remain distinct after migration");

        // Verify references
        final var personV1Refs = client.ids().contentIds().byContentId(data.personV1ContentId)
                .references().get();
        assertEquals(1, personV1Refs.size(), "Person v1 must have 1 reference");
        assertEquals("1", personV1Refs.get(0).getVersion(), "Person v1 must reference address v1");

        final var personV2Refs = client.ids().contentIds().byContentId(data.personV2ContentId)
                .references().get();
        assertEquals(1, personV2Refs.size(), "Person v2 must have 1 reference");
        assertEquals("2", personV2Refs.get(0).getVersion(), "Person v2 must reference address v2");

        // Verify content bytes survived the migration
        try (final var addressContent = client.ids().contentIds()
                .byContentId(data.addressV1ContentId).get()) {
            final byte[] addressBytes = addressContent.readAllBytes();
            assertTrue(addressBytes.length > 0, "Address v1 content bytes must survive migration");
            assertTrue(new String(addressBytes, StandardCharsets.UTF_8).contains("\"name\":\"Address\""),
                    "Address v1 content must contain schema name");
        }
        try (final var petstoreContent = client.ids().contentIds()
                .byContentId(data.openapiContentId).get()) {
            final byte[] petstoreBytes = petstoreContent.readAllBytes();
            assertTrue(petstoreBytes.length > 0, "Petstore content bytes must survive migration");
            assertTrue(new String(petstoreBytes, StandardCharsets.UTF_8).contains("\"title\":\"Petstore\""),
                    "Petstore content must contain API title");
        }

        // Verify global rules
        final var globalValidity = client.admin().rules().byRuleType("VALIDITY").get();
        assertEquals("SYNTAX_ONLY", globalValidity.getConfig());

        // Verify group metadata and rules
        final var groupMeta = client.groups().byGroupId("migrationtest").get();
        assertEquals("Group for migration testing", groupMeta.getDescription());

        final var groupCompat = client.groups().byGroupId("migrationtest")
                .rules().byRuleType("COMPATIBILITY").get();
        assertEquals("BACKWARD", groupCompat.getConfig());

        // Verify artifact rules
        final var artifactValidity = client.groups().byGroupId("apigroup")
                .artifacts().byArtifactId("petstore").rules().byRuleType("VALIDITY").get();
        assertEquals("FULL", artifactValidity.getConfig());
    }

    private void verifyVersionId(RegistryClient client, String groupId, String artifactId,
            String version, long expectedGlobalId, long expectedContentId) {
        final var versionMeta = client.groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(version).get();
        assertEquals(expectedGlobalId, versionMeta.getGlobalId(),
                groupId + "/" + artifactId + " v" + version + " globalId must round-trip");
        assertEquals(expectedContentId, versionMeta.getContentId(),
                groupId + "/" + artifactId + " v" + version + " contentId must round-trip");
    }

    private CreateArtifactResponse createArtifactWithContent(RegistryClient client, String groupId,
            String artifactId, String artifactType, String content, String contentType,
            List<ArtifactReference> references) {
        final var createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(artifactType);
        final var firstVersion = new CreateVersion();
        final var versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(contentType);
        versionContent.setReferences(references);
        firstVersion.setContent(versionContent);
        createArtifact.setFirstVersion(firstVersion);
        return client.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }

    private io.apicurio.registry.rest.client.models.VersionMetaData createVersionWithContent(
            RegistryClient client, String groupId, String artifactId, String content,
            String contentType, List<ArtifactReference> references) {
        final var createVersion = new CreateVersion();
        final var versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(contentType);
        versionContent.setReferences(references);
        createVersion.setContent(versionContent);
        return client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().post(createVersion);
    }

    private byte[] downloadZip(String url) throws Exception {
        final HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
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

    private void extractZipToDir(byte[] zipBytes, Path targetDir) throws Exception {
        try (final var zis = new ZipInputStream(
                new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                final Path target = targetDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(targetDir.normalize())) {
                    throw new IOException("ZipEntry escapes target directory: " + entry.getName());
                }
                Files.createDirectories(target.getParent());
                Files.write(target, zis.readAllBytes());
                zis.closeEntry();
            }
        }
    }

    private void patchRegistryId(Path repoDir, String registryId) throws Exception {
        final Path registryYaml = repoDir.resolve("registry.registry.yaml");
        if (Files.exists(registryYaml)) {
            final String content = Files.readString(registryYaml, StandardCharsets.UTF_8);
            Files.writeString(registryYaml,
                    content.replaceAll("registryId: \"[^\"]*\"", "registryId: \"" + registryId + "\""),
                    StandardCharsets.UTF_8);
        }
    }

    private void initGitRepo(Path repoDir) throws Exception {
        try (final var git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call()) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Initial export").call();
        }
    }

    private record PopulatedData(
            long addressV1GlobalId, long addressV1ContentId,
            long addressV2GlobalId, long addressV2ContentId,
            long personV1GlobalId, long personV1ContentId,
            long personV2GlobalId, long personV2ContentId,
            long openapiGlobalId, long openapiContentId) {
    }
}
