package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class GitOpsImportTest extends AbstractResourceTestBase {

    @Inject
    @Current
    RegistryStorage storage;

    private byte[] buildGitOpsZip(ZipBuilder builder) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            builder.build(zos);
        }
        return baos.toByteArray();
    }

    private void addZipEntry(ZipOutputStream zos, String path, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(path));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }


    private void importZip(byte[] zipBytes) {
        clientV3.admin().importEscaped().post(new ByteArrayInputStream(zipBytes), config -> {
            config.headers.putIfAbsent("Content-Type", Set.of("application/zip"));
        });
    }

    @Test
    public void testImportGitOpsFormat() throws Exception {
        storage.deleteAllUserData();

        final String avroSchema = "{\"type\":\"record\",\"name\":\"Test\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"}]}";

        final byte[] zipBytes = buildGitOpsZip(zos -> {
            addZipEntry(zos, "registry.registry.yaml", """
                    $type: registry-v0
                    registryId: main
                    globalRules:
                      - ruleType: VALIDITY
                        config: FULL
                    properties: []
                    """);

            addZipEntry(zos, "default/default.registry.yaml", """
                    $type: group-v0
                    groupId: default
                    description: Default group
                    """);

            addZipEntry(zos, "default/my-schema/my-schema.registry.yaml", """
                    $type: artifact-v0
                    groupId: default
                    artifactId: my-schema
                    artifactType: AVRO
                    name: My Schema
                    description: Test schema
                    versions:
                      - version: "1"
                        state: ENABLED
                        globalId: 1001
                        content: ../../content/1.avsc
                        contentMetadata: ../../content/1.content.registry.yaml
                    """);

            addZipEntry(zos, "content/1.avsc", avroSchema);

            addZipEntry(zos, "content/1.content.registry.yaml", """
                    $type: content-v0
                    contentId: 100
                    """);
        });

        importZip(zipBytes);

        // Verify artifact exists (default group = null groupId)
        final ArtifactMetaData artifact = clientV3.groups().byGroupId("default").artifacts()
                .byArtifactId("my-schema").get();
        Assertions.assertEquals("my-schema", artifact.getArtifactId());
        Assertions.assertEquals("My Schema", artifact.getName());
        Assertions.assertEquals("Test schema", artifact.getDescription());

        // Verify version
        final VersionMetaData version = clientV3.groups().byGroupId("default").artifacts()
                .byArtifactId("my-schema").versions().byVersionExpression("1").get();
        Assertions.assertEquals("1", version.getVersion());
        Assertions.assertEquals(1001L, version.getGlobalId());

        // Verify global rule
        final Rule rule = clientV3.admin().rules().byRuleType(RuleType.VALIDITY.getValue()).get();
        Assertions.assertEquals("FULL", rule.getConfig());
    }

    @Test
    public void testImportGitOpsWithReferences() throws Exception {
        storage.deleteAllUserData();

        final String addressSchema = "{\"type\":\"record\",\"name\":\"Address\",\"fields\":[{\"name\":\"city\",\"type\":\"string\"}]}";
        final String personSchema = "{\"type\":\"record\",\"name\":\"Person\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"}]}";

        final byte[] zipBytes = buildGitOpsZip(zos -> {
            addZipEntry(zos, "registry.registry.yaml", """
                    $type: registry-v0
                    registryId: main
                    globalRules: []
                    properties: []
                    """);

            addZipEntry(zos, "default/default.registry.yaml", """
                    $type: group-v0
                    groupId: default
                    """);

            addZipEntry(zos, "default/address/address.registry.yaml", """
                    $type: artifact-v0
                    groupId: default
                    artifactId: address
                    artifactType: AVRO
                    versions:
                      - version: "1"
                        state: ENABLED
                        globalId: 1
                        content: ../../content/address.avsc
                        contentMetadata: ../../content/address.metadata.registry.yaml
                    """);

            addZipEntry(zos, "default/person/person.registry.yaml", """
                    $type: artifact-v0
                    groupId: default
                    artifactId: person
                    artifactType: AVRO
                    versions:
                      - version: "1"
                        state: ENABLED
                        globalId: 2
                        content: ../../content/person.avsc
                        contentMetadata: ../../content/person.metadata.registry.yaml
                    """);

            addZipEntry(zos, "content/address.avsc", addressSchema);
            addZipEntry(zos, "content/address.metadata.registry.yaml", """
                    $type: content-v0
                    contentId: 10
                    """);

            addZipEntry(zos, "content/person.avsc", personSchema);
            addZipEntry(zos, "content/person.metadata.registry.yaml", """
                    $type: content-v0
                    contentId: 20
                    references:
                      - groupId: default
                        artifactId: address
                        version: "1"
                        name: com.example.Address
                    """);
        });

        importZip(zipBytes);

        // Verify both artifacts exist
        Assertions.assertNotNull(clientV3.groups().byGroupId("default").artifacts()
                .byArtifactId("address").get());
        Assertions.assertNotNull(clientV3.groups().byGroupId("default").artifacts()
                .byArtifactId("person").get());

        // Verify references on the person artifact's version
        final var refs = clientV3.groups().byGroupId("default").artifacts()
                .byArtifactId("person").versions().byVersionExpression("1").references().get();
        Assertions.assertEquals(1, refs.size());
        Assertions.assertEquals("address", refs.get(0).getArtifactId());
        Assertions.assertEquals("com.example.Address", refs.get(0).getName());
    }

    @Test
    public void testImportGitOpsEmptyGroup() throws Exception {
        storage.deleteAllUserData();

        final byte[] zipBytes = buildGitOpsZip(zos -> {
            addZipEntry(zos, "registry.registry.yaml", """
                    $type: registry-v0
                    registryId: main
                    globalRules: []
                    properties: []
                    """);

            addZipEntry(zos, "emptygroup/emptygroup.registry.yaml", """
                    $type: group-v0
                    groupId: emptygroup
                    description: Empty group
                    rules:
                      - ruleType: COMPATIBILITY
                        config: BACKWARD
                    """);
        });

        importZip(zipBytes);

        // Verify group exists
        final var group = clientV3.groups().byGroupId("emptygroup").get();
        Assertions.assertEquals("emptygroup", group.getGroupId());
        Assertions.assertEquals("Empty group", group.getDescription());

        // Verify group rule
        final List<RuleType> rules = clientV3.groups().byGroupId("emptygroup").rules().get();
        Assertions.assertTrue(rules.contains(RuleType.COMPATIBILITY));
    }

    @Test
    public void testImportGitOpsDefaultFormatUnchanged() throws Exception {
        storage.deleteAllUserData();

        // Build a standard (non-GitOps) ZIP with Manifest
        final byte[] zipBytes = buildGitOpsZip(zos -> {
            addZipEntry(zos, "0001.Manifest.json", """
                    {"systemVersion":"3.0.0","systemName":"Apicurio Registry",\
                    "systemDescription":"Test","exportVersion":"3.0",\
                    "exportedOn":"2024-01-01T00:00:00Z","exportedBy":"test"}""");

            addZipEntry(zos, "0002.GlobalRule.json", """
                    {"ruleType":"VALIDITY","configuration":"FULL"}""");
        });

        importZip(zipBytes);

        // Verify global rule imported via default format
        final Rule rule = clientV3.admin().rules().byRuleType(RuleType.VALIDITY.getValue()).get();
        Assertions.assertEquals("FULL", rule.getConfig());
    }

    @Test
    public void testImportGitOpsMissingContentFile() throws Exception {
        storage.deleteAllUserData();

        final byte[] zipBytes = buildGitOpsZip(zos -> {
            addZipEntry(zos, "registry.registry.yaml", """
                    $type: registry-v0
                    registryId: main
                    globalRules: []
                    properties: []
                    """);

            addZipEntry(zos, "grp/grp.registry.yaml", """
                    $type: group-v0
                    groupId: grp
                    """);

            addZipEntry(zos, "grp/art/art.registry.yaml", """
                    $type: artifact-v0
                    groupId: grp
                    artifactId: art
                    artifactType: JSON
                    versions:
                      - version: "1"
                        state: ENABLED
                        globalId: 1
                        content: ../../content/nonexistent.json
                    """);
        });

        Assertions.assertThrows(Exception.class, () -> importZip(zipBytes),
                "Should throw an exception for missing content file");
    }

    @Test
    public void testExportGitOpsAndImportRoundTrip() throws Exception {
        storage.deleteAllUserData();

        // 1. Populate the registry with data
        final String groupId = "RoundTripGroup";
        final CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        createGroup.setDescription("Group for export-import round-trip test");
        clientV3.groups().post(createGroup);

        final CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("FULL");
        clientV3.admin().rules().post(createRule);

        final String addressSchema =
                "{\"type\":\"record\",\"name\":\"Address\",\"fields\":[{\"name\":\"city\",\"type\":\"string\"}]}";
        final CreateArtifactResponse addressCreated = createArtifact(groupId, "address",
                ArtifactType.AVRO, addressSchema, ContentTypes.APPLICATION_JSON);
        final long addressGlobalId = addressCreated.getVersion().getGlobalId();

        final String personSchema =
                "{\"type\":\"record\",\"name\":\"Person\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"}]}";
        final ArtifactReference personRef = ArtifactReference.builder()
                .groupId(groupId).artifactId("address").version("1")
                .name("com.example.Address").build();
        final CreateArtifactResponse personCreated = createArtifactWithReferences(groupId, "person",
                ArtifactType.AVRO, personSchema, ContentTypes.APPLICATION_JSON,
                List.of(personRef));
        final long personGlobalId = personCreated.getVersion().getGlobalId();

        // 2. Export as GitOps format
        final byte[] exportedZipBytes = exportGitOpsZip();

        // 3. Delete all data
        storage.deleteAllUserData();

        // 4. Import the GitOps ZIP
        importZip(exportedZipBytes);

        // 5. Verify everything survived the round-trip
        final ArtifactMetaData addressArtifact = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId("address").get();
        Assertions.assertEquals("AVRO", addressArtifact.getArtifactType());

        final VersionMetaData addressVersion = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId("address").versions().byVersionExpression("1").get();
        Assertions.assertEquals(addressGlobalId, addressVersion.getGlobalId());

        final ArtifactMetaData personArtifact = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId("person").get();
        Assertions.assertEquals("AVRO", personArtifact.getArtifactType());

        final VersionMetaData personVersion = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId("person").versions().byVersionExpression("1").get();
        Assertions.assertEquals(personGlobalId, personVersion.getGlobalId());

        // Verify references survived
        final var refs = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId("person").versions().byVersionExpression("1").references().get();
        Assertions.assertEquals(1, refs.size());
        Assertions.assertEquals("address", refs.get(0).getArtifactId());
        Assertions.assertEquals("com.example.Address", refs.get(0).getName());

        // Verify content bytes survived
        final InputStream contentStream = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId("address").versions().byVersionExpression("1").content().get();
        final String importedContent = new String(contentStream.readAllBytes(), StandardCharsets.UTF_8);
        Assertions.assertTrue(importedContent.contains("Address"),
                "Content bytes should survive the round-trip");

        // Verify global rule
        final Rule rule = clientV3.admin().rules().byRuleType(RuleType.VALIDITY.getValue()).get();
        Assertions.assertEquals("FULL", rule.getConfig());
    }

    private byte[] exportGitOpsZip() throws Exception {
        final String href = given().when()
                .queryParam("format", "gitops-v0")
                .accept("application/json")
                .get("/registry/v3/admin/export")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("href");
        Assertions.assertNotNull(href, "Export should return a download href");

        final String downloadUrl = String.format("http://localhost:%s%s", testPort, href);
        final HttpURLConnection conn = (HttpURLConnection) URI.create(downloadUrl).toURL().openConnection();
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(30_000);
        try (InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
        }
    }

    @FunctionalInterface
    private interface ZipBuilder {
        void build(ZipOutputStream zos) throws Exception;
    }
}
