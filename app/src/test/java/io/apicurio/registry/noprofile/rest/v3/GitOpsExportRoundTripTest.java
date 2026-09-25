package io.apicurio.registry.noprofile.rest.v3;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rest.client.models.CreateBranch;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.v3.beans.NewComment;
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
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class GitOpsExportRoundTripTest extends AbstractResourceTestBase {

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

    @Test
    public void testGitOpsExportRoundTrip() throws Exception {
        storage.deleteAllUserData();

        final String groupId = "GitOpsTestGroup";
        final String artifactId = "TestSchema";

        createGroup(groupId, "A test group for GitOps export", null, null);

        createGlobalRule(RuleType.VALIDITY, ValidityLevel.FULL.name());

        createGroupRule(groupId, RuleType.INTEGRITY, IntegrityLevel.ALL_REFS_MAPPED.name());

        createArtifact(groupId, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"Test\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);

        final EditableArtifactMetaData artifactMeta = new EditableArtifactMetaData();
        artifactMeta.setName("Test Schema");
        artifactMeta.setDescription("An Avro test schema");
        artifactMeta.setLabels(new Labels());
        artifactMeta.getLabels().setAdditionalData(Map.of("env", "test"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(artifactMeta);

        createArtifactVersion(groupId, artifactId,
                "{\"type\":\"record\",\"name\":\"Test\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"},{\"name\":\"name\",\"type\":\"string\"}]}",
                ContentTypes.APPLICATION_JSON);

        final EditableVersionMetaData versionMeta = new EditableVersionMetaData();
        versionMeta.setName("Version 2");
        versionMeta.setDescription("Added name field");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("2").put(versionMeta);

        createArtifactRule(groupId, artifactId, RuleType.VALIDITY, ValidityLevel.SYNTAX_ONLY.name());

        final CreateBranch createBranch = new CreateBranch();
        createBranch.setBranchId("stable");
        createBranch.setDescription("Stable versions");
        createBranch.setVersions(List.of("1"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).branches()
                .post(createBranch);

        final NewComment nc = NewComment.builder().value("Review comment on v1").build();
        given().when().contentType(CT_JSON).pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId).body(nc)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/1/comments")
                .then().statusCode(200);

        final byte[] zipBytes = exportGitOpsZip();

        final Map<String, byte[]> entries = readZipEntries(zipBytes);

        Assertions.assertTrue(entries.containsKey("registry.registry.yaml"),
                "Missing registry.registry.yaml");

        final Registry registry = YAML_MAPPER.readValue(entries.get("registry.registry.yaml"),
                Registry.class);
        Assertions.assertEquals("registry-v0", registry.getType());
        Assertions.assertEquals("default", registry.getRegistryId());
        Assertions.assertNotNull(registry.getGlobalRules());
        Assertions.assertEquals(1, registry.getGlobalRules().size());
        Assertions.assertEquals("VALIDITY", registry.getGlobalRules().get(0).getRuleType());
        Assertions.assertEquals(ValidityLevel.FULL.name(), registry.getGlobalRules().get(0).getConfig());

        final String groupYamlPath = groupId + "/" + groupId + ".registry.yaml";
        Assertions.assertTrue(entries.containsKey(groupYamlPath),
                "Missing group YAML: " + groupYamlPath);

        final Group group = YAML_MAPPER.readValue(entries.get(groupYamlPath), Group.class);
        Assertions.assertEquals("group-v0", group.getType());
        Assertions.assertEquals(groupId, group.getGroupId());
        Assertions.assertEquals("A test group for GitOps export", group.getDescription());
        Assertions.assertNotNull(group.getRules());
        Assertions.assertEquals(1, group.getRules().size());
        Assertions.assertEquals("INTEGRITY", group.getRules().get(0).getRuleType());
        Assertions.assertEquals(IntegrityLevel.ALL_REFS_MAPPED.name(),
                group.getRules().get(0).getConfig());

        final String artifactYamlPath = groupId + "/" + artifactId + "/" + artifactId
                + ".registry.yaml";
        Assertions.assertTrue(entries.containsKey(artifactYamlPath),
                "Missing artifact YAML: " + artifactYamlPath);

        final Artifact artifact = YAML_MAPPER.readValue(entries.get(artifactYamlPath),
                Artifact.class);
        Assertions.assertEquals("artifact-v0", artifact.getType());
        Assertions.assertEquals(groupId, artifact.getGroupId());
        Assertions.assertEquals(artifactId, artifact.getArtifactId());
        Assertions.assertEquals(ArtifactType.AVRO, artifact.getArtifactType());
        Assertions.assertEquals("Test Schema", artifact.getName());
        Assertions.assertEquals("An Avro test schema", artifact.getDescription());
        Assertions.assertEquals(Map.of("env", "test"), artifact.getLabels());

        Assertions.assertNotNull(artifact.getVersions());
        Assertions.assertEquals(2, artifact.getVersions().size());
        Assertions.assertEquals("1", artifact.getVersions().get(0).getVersion());
        Assertions.assertEquals("2", artifact.getVersions().get(1).getVersion());
        Assertions.assertEquals("Version 2", artifact.getVersions().get(1).getName());
        Assertions.assertEquals("Added name field", artifact.getVersions().get(1).getDescription());
        Assertions.assertNotNull(artifact.getVersions().get(0).getContent());
        Assertions.assertTrue(artifact.getVersions().get(0).getContent().startsWith("./content/"));
        Assertions.assertNotNull(artifact.getVersions().get(0).getCreatedOn());

        Assertions.assertNotNull(artifact.getVersions().get(0).getComments());
        Assertions.assertEquals(1, artifact.getVersions().get(0).getComments().size());
        Assertions.assertEquals("Review comment on v1",
                artifact.getVersions().get(0).getComments().get(0).getValue());
        Assertions.assertNotNull(artifact.getVersions().get(0).getComments().get(0).getCommentId());

        Assertions.assertNull(artifact.getVersions().get(1).getComments());

        Assertions.assertNotNull(artifact.getRules());
        Assertions.assertEquals(1, artifact.getRules().size());
        Assertions.assertEquals("VALIDITY", artifact.getRules().get(0).getRuleType());
        Assertions.assertEquals(ValidityLevel.SYNTAX_ONLY.name(),
                artifact.getRules().get(0).getConfig());

        Assertions.assertNotNull(artifact.getBranches());
        final boolean hasStableBranch = artifact.getBranches().stream()
                .anyMatch(b -> "stable".equals(b.getBranchId()));
        Assertions.assertTrue(hasStableBranch, "Missing 'stable' branch in export");
        final io.apicurio.registry.storage.impl.polling.model.v0.Branch stableBranch = artifact
                .getBranches().stream().filter(b -> "stable".equals(b.getBranchId())).findFirst()
                .orElseThrow();
        Assertions.assertEquals("Stable versions", stableBranch.getDescription());
        Assertions.assertFalse(stableBranch.isSystemDefined());

        final boolean hasLatestBranch = artifact.getBranches().stream()
                .anyMatch(b -> "latest".equals(b.getBranchId()));
        Assertions.assertTrue(hasLatestBranch, "Missing 'latest' branch in export");

        final String contentPath1 = groupId + "/" + artifactId + "/content/1.avsc";
        final String contentPath2 = groupId + "/" + artifactId + "/content/2.avsc";
        Assertions.assertTrue(entries.containsKey(contentPath1),
                "Missing content file: " + contentPath1);
        Assertions.assertTrue(entries.containsKey(contentPath2),
                "Missing content file: " + contentPath2);

        final String content1 = new String(entries.get(contentPath1), StandardCharsets.UTF_8);
        Assertions.assertTrue(content1.contains("\"name\":\"Test\""),
                "Content file 1 should contain schema name");

        final String content2 = new String(entries.get(contentPath2), StandardCharsets.UTF_8);
        Assertions.assertTrue(content2.contains("\"type\":\"string\""),
                "Content file 2 should contain the added string-typed field");
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

    private byte[] exportGitOpsZip() throws IOException {
        final String href = given().when()
                .queryParam("format", "gitops-v1")
                .accept(CT_JSON)
                .get("/registry/v3/admin/export")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("href");

        Assertions.assertNotNull(href, "Export should return a download href");

        final String downloadUrl = String.format("http://localhost:%s%s", testPort, href);
        try (final InputStream in = URI.create(downloadUrl).toURL().openStream()) {
            return in.readAllBytes();
        }
    }

    private static Map<String, byte[]> readZipEntries(byte[] zipBytes) throws IOException {
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
