package io.apicurio.registry.noprofile.rest.v3;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.polling.model.v0.Artifact;
import io.apicurio.registry.storage.impl.polling.model.v0.Group;
import io.apicurio.registry.storage.impl.polling.model.v0.Registry;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class GitOpsExportRoundTripTest extends AbstractResourceTestBase {

    private static final String GROUP_ID = "GitOpsTestGroup";
    private static final String ARTIFACT_ID = "TestSchema";

    private static final ObjectMapper YAML_MAPPER;

    static {
        final YAMLFactory yamlFactory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build();
        YAML_MAPPER = new ObjectMapper(yamlFactory);
        YAML_MAPPER.findAndRegisterModules();
        YAML_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Inject
    @Current
    RegistryStorage storage;

    private Map<String, byte[]> exportedEntries;

    private Map<String, byte[]> getExportedEntries() throws Exception {
        if (exportedEntries != null) {
            return exportedEntries;
        }
        storage.deleteAllUserData();

        createGroup(GROUP_ID, "A test group for GitOps export", null, null);
        createGlobalRule(RuleType.VALIDITY, ValidityLevel.FULL.name());
        createGroupRule(GROUP_ID, RuleType.INTEGRITY, IntegrityLevel.ALL_REFS_MAPPED.name());

        createArtifact(GROUP_ID, ARTIFACT_ID, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"Test\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);

        final EditableArtifactMetaData artifactMeta = new EditableArtifactMetaData();
        artifactMeta.setName("Test Schema");
        artifactMeta.setDescription("An Avro test schema");
        artifactMeta.setLabels(new Labels());
        artifactMeta.getLabels().setAdditionalData(Map.of("env", "test"));
        clientV3.groups().byGroupId(GROUP_ID).artifacts().byArtifactId(ARTIFACT_ID)
                .put(artifactMeta);

        createArtifactVersion(GROUP_ID, ARTIFACT_ID,
                "{\"type\":\"record\",\"name\":\"Test\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"},{\"name\":\"name\",\"type\":\"string\"}]}",
                ContentTypes.APPLICATION_JSON);

        final EditableVersionMetaData versionMeta = new EditableVersionMetaData();
        versionMeta.setName("Version 2");
        versionMeta.setDescription("Added name field");
        clientV3.groups().byGroupId(GROUP_ID).artifacts().byArtifactId(ARTIFACT_ID).versions()
                .byVersionExpression("2").put(versionMeta);

        createArtifactRule(GROUP_ID, ARTIFACT_ID, RuleType.VALIDITY,
                ValidityLevel.SYNTAX_ONLY.name());

        exportedEntries = exportAndReadZip();
        return exportedEntries;
    }

    @Test
    public void testRegistryYaml() throws Exception {
        Assertions.assertTrue(getExportedEntries().containsKey("registry.registry.yaml"));

        final Registry registry = YAML_MAPPER.readValue(
                getExportedEntries().get("registry.registry.yaml"), Registry.class);
        Assertions.assertEquals("registry-v0", registry.getType());
        Assertions.assertEquals("main", registry.getRegistryId());
        Assertions.assertNotNull(registry.getGlobalRules());
        Assertions.assertEquals(1, registry.getGlobalRules().size());
        Assertions.assertEquals("VALIDITY", registry.getGlobalRules().get(0).getRuleType());
        Assertions.assertEquals(ValidityLevel.FULL.name(),
                registry.getGlobalRules().get(0).getConfig());
    }

    @Test
    public void testGroupYaml() throws Exception {
        final String groupYamlPath = GROUP_ID + "/" + GROUP_ID + ".registry.yaml";
        Assertions.assertTrue(getExportedEntries().containsKey(groupYamlPath));

        final Group group = YAML_MAPPER.readValue(getExportedEntries().get(groupYamlPath), Group.class);
        Assertions.assertEquals("group-v0", group.getType());
        Assertions.assertEquals(GROUP_ID, group.getGroupId());
        Assertions.assertEquals("A test group for GitOps export", group.getDescription());
        Assertions.assertNotNull(group.getRules());
        Assertions.assertEquals(1, group.getRules().size());
        Assertions.assertEquals("INTEGRITY", group.getRules().get(0).getRuleType());
        Assertions.assertEquals(IntegrityLevel.ALL_REFS_MAPPED.name(),
                group.getRules().get(0).getConfig());
    }

    @Test
    public void testArtifactMetadata() throws Exception {
        final Artifact artifact = readArtifactYaml();
        Assertions.assertEquals("artifact-v0", artifact.getType());
        Assertions.assertEquals(GROUP_ID, artifact.getGroupId());
        Assertions.assertEquals(ARTIFACT_ID, artifact.getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, artifact.getArtifactType());
        Assertions.assertEquals("Test Schema", artifact.getName());
        Assertions.assertEquals("An Avro test schema", artifact.getDescription());
        Assertions.assertEquals(Map.of("env", "test"), artifact.getLabels());
    }

    @Test
    public void testArtifactVersions() throws Exception {
        final Artifact artifact = readArtifactYaml();
        Assertions.assertNotNull(artifact.getVersions());
        Assertions.assertEquals(2, artifact.getVersions().size());
        Assertions.assertEquals("1", artifact.getVersions().get(0).getVersion());
        Assertions.assertEquals("2", artifact.getVersions().get(1).getVersion());
        Assertions.assertEquals("Version 2", artifact.getVersions().get(1).getName());
        Assertions.assertEquals("Added name field",
                artifact.getVersions().get(1).getDescription());
        Assertions.assertTrue(
                artifact.getVersions().get(0).getContent().contains("content/"));
        Assertions.assertNotNull(artifact.getVersions().get(0).getCreatedOn());
    }

    @Test
    public void testArtifactRules() throws Exception {
        final Artifact artifact = readArtifactYaml();

        Assertions.assertNotNull(artifact.getRules());
        Assertions.assertEquals(1, artifact.getRules().size());
        Assertions.assertEquals("VALIDITY", artifact.getRules().get(0).getRuleType());
        Assertions.assertEquals(ValidityLevel.SYNTAX_ONLY.name(),
                artifact.getRules().get(0).getConfig());
    }

    @Test
    public void testContentFilesInSharedDirectory() throws Exception {
        final Map<String, byte[]> entries = getExportedEntries();
        final long contentFiles = entries.keySet().stream()
                .filter(k -> k.startsWith("content/") && !k.endsWith(".registry.yaml"))
                .count();
        Assertions.assertTrue(contentFiles >= 2, "Expected at least 2 content files in shared directory");

        final boolean hasAvroContent = entries.keySet().stream()
                .filter(k -> k.startsWith("content/"))
                .anyMatch(k -> {
                    final String c = new String(entries.get(k), StandardCharsets.UTF_8);
                    return c.contains("\"name\":\"Test\"");
                });
        Assertions.assertTrue(hasAvroContent, "Shared content should contain the Avro schema");
    }

    @Test
    public void testVersionContentPathsResolve() throws Exception {
        final Artifact artifact = readArtifactYaml();
        final Map<String, byte[]> entries = getExportedEntries();
        final String artifactDir = GROUP_ID + "/" + ARTIFACT_ID;

        for (final var version : artifact.getVersions()) {
            final String contentRef = version.getContent();
            Assertions.assertNotNull(contentRef, "Version " + version.getVersion() + " missing content path");

            final String resolvedPath = resolveRelativePath(artifactDir, contentRef);
            Assertions.assertTrue(entries.containsKey(resolvedPath),
                    "Content path '" + resolvedPath + "' not found in ZIP for version "
                            + version.getVersion());

            final byte[] contentBytes = entries.get(resolvedPath);
            Assertions.assertTrue(contentBytes.length > 0,
                    "Content file should not be empty for version " + version.getVersion());
        }
    }

    @Test
    public void testRegistryIdMatchesDefault() throws Exception {
        final Registry registry = YAML_MAPPER.readValue(
                getExportedEntries().get("registry.registry.yaml"), Registry.class);
        Assertions.assertEquals("main", registry.getRegistryId(),
                "registryId must match default apicurio.polling-storage.id for loadability");
    }

    @Test
    public void testNoPathTraversalInEntries() throws Exception {
        for (final String path : getExportedEntries().keySet()) {
            Assertions.assertFalse(path.contains(".."),
                    "ZIP entry should not contain path traversal: " + path);
            Assertions.assertFalse(path.startsWith("/"),
                    "ZIP entry should not be an absolute path: " + path);
        }
    }

    @Test
    public void testDefaultExportFormatUnchanged() throws Exception {
        storage.deleteAllUserData();

        final String groupId = "DefaultFormatGroup";
        createGroup(groupId, "Test group", null, null);
        createArtifact(groupId, "SimpleArtifact", ArtifactType.JSON, "{}",
                ContentTypes.APPLICATION_JSON);

        final var downloadRef = clientV3.admin().export().get();
        Assertions.assertNotNull(downloadRef);
        Assertions.assertNotNull(downloadRef.getHref());
    }

    @Test
    public void testInvalidFormatReturnsBadRequest() {
        given().when()
                .queryParam("format", "invalid-format")
                .get("/registry/v3/admin/export")
                .then()
                .statusCode(400);
    }

    private Artifact readArtifactYaml() throws Exception {
        final String path = GROUP_ID + "/" + ARTIFACT_ID + "/" + ARTIFACT_ID + ".registry.yaml";
        Assertions.assertTrue(getExportedEntries().containsKey(path));
        return YAML_MAPPER.readValue(getExportedEntries().get(path), Artifact.class);
    }

    private String resolveRelativePath(String baseDir, String relativePath) {
        final String[] baseParts = baseDir.split("/");
        final String[] relParts = relativePath.split("/");
        final java.util.List<String> resolved = new java.util.ArrayList<>(java.util.Arrays.asList(baseParts));
        for (final String part : relParts) {
            if ("..".equals(part)) {
                if (!resolved.isEmpty()) {
                    resolved.remove(resolved.size() - 1);
                }
            } else if (!".".equals(part)) {
                resolved.add(part);
            }
        }
        return String.join("/", resolved);
    }

    private Map<String, byte[]> exportAndReadZip() throws IOException {
        final String href = given().when()
                .queryParam("format", "gitops-v0")
                .accept(CT_JSON)
                .get("/registry/v3/admin/export")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("href");

        Assertions.assertNotNull(href, "Export should return a download href");

        final String downloadUrl = String.format("http://localhost:%s%s", testPort, href);
        final byte[] zipBytes;
        try (final InputStream in = URI.create(downloadUrl).toURL().openStream()) {
            zipBytes = in.readAllBytes();
        }

        final Map<String, byte[]> entries = new HashMap<>();
        try (final ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), zis.readAllBytes());
                zis.closeEntry();
            }
        }
        return entries;
    }
}
