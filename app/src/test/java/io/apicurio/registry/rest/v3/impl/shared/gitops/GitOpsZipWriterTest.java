package io.apicurio.registry.rest.v3.impl.shared.gitops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.utils.impexp.v3.CommentEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitOpsZipWriterTest {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Test
    void writesRegistryYaml() throws Exception {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final GlobalRuleEntity rule = new GlobalRuleEntity();
        rule.ruleType = RuleType.VALIDITY;
        rule.configuration = "FULL";
        collector.collect(rule);

        final Map<String, byte[]> entries = writeAndExtract(collector);

        assertTrue(entries.containsKey("registry.registry.yaml"));
        final JsonNode yaml = parseYaml(entries.get("registry.registry.yaml"));
        assertEquals("registry-v0", yaml.get("$type").asText());
        assertEquals("default", yaml.get("registryId").asText());
        assertEquals(1, yaml.get("globalRules").size());
        assertEquals("VALIDITY", yaml.get("globalRules").get(0).get("ruleType").asText());
    }

    @Test
    void writesGroupYaml() throws Exception {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final GroupEntity group = new GroupEntity();
        group.groupId = "orders";
        group.description = "Order schemas";
        group.owner = "team-orders";
        group.createdOn = 1700000000000L;
        group.modifiedOn = 1700000000000L;

        final GroupRuleEntity rule = new GroupRuleEntity();
        rule.groupId = "orders";
        rule.type = RuleType.COMPATIBILITY;
        rule.configuration = "BACKWARD";

        collector.collect(group);
        collector.collect(rule);

        final Map<String, byte[]> entries = writeAndExtract(collector);

        assertTrue(entries.containsKey("orders/orders.registry.yaml"));
        final JsonNode yaml = parseYaml(entries.get("orders/orders.registry.yaml"));
        assertEquals("group-v0", yaml.get("$type").asText());
        assertEquals("orders", yaml.get("groupId").asText());
        assertEquals("Order schemas", yaml.get("description").asText());
        assertEquals(1, yaml.get("rules").size());
    }

    @Test
    void writesArtifactYamlWithVersions() throws Exception {
        final GitOpsEntityCollector collector = createSingleArtifactCollector();
        final Map<String, byte[]> entries = writeAndExtract(collector);

        final String yamlPath = "mygroup/my-artifact/my-artifact.registry.yaml";
        assertTrue(entries.containsKey(yamlPath));

        final JsonNode yaml = parseYaml(entries.get(yamlPath));
        assertEquals("artifact-v0", yaml.get("$type").asText());
        assertEquals("mygroup", yaml.get("groupId").asText());
        assertEquals("my-artifact", yaml.get("artifactId").asText());
        assertEquals("AVRO", yaml.get("artifactType").asText());

        final JsonNode versions = yaml.get("versions");
        assertNotNull(versions);
        assertEquals(1, versions.size());
        assertEquals("1.0", versions.get(0).get("version").asText());
        assertEquals("./content/1.0.avsc", versions.get(0).get("content").asText());
    }

    @Test
    void writesContentFile() throws Exception {
        final GitOpsEntityCollector collector = createSingleArtifactCollector();
        final Map<String, byte[]> entries = writeAndExtract(collector);

        final String contentPath = "mygroup/my-artifact/content/1.0.avsc";
        assertTrue(entries.containsKey(contentPath));
        assertEquals("{\"type\":\"string\"}", new String(entries.get(contentPath), StandardCharsets.UTF_8));
    }

    @Test
    void writesContentMetadataWhenReferencesExist() throws Exception {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final ContentEntity content = new ContentEntity();
        content.contentId = 1L;
        content.contentBytes = "{\"type\":\"record\"}".getBytes(StandardCharsets.UTF_8);
        content.artifactType = "AVRO";
        content.serializedReferences = "[{\"groupId\":\"g1\",\"artifactId\":\"address\",\"version\":\"1\",\"name\":\"Address\"}]";
        collector.collect(content);

        final ArtifactEntity artifact = new ArtifactEntity();
        artifact.groupId = "g1";
        artifact.artifactId = "customer";
        artifact.artifactType = "AVRO";
        collector.collect(artifact);

        final ArtifactVersionEntity version = new ArtifactVersionEntity();
        version.groupId = "g1";
        version.artifactId = "customer";
        version.version = "1";
        version.versionOrder = 1;
        version.globalId = 1L;
        version.contentId = 1L;
        collector.collect(version);

        final Map<String, byte[]> entries = writeAndExtract(collector);

        assertTrue(entries.containsKey("g1/customer/content/1.content.registry.yaml"));
        final JsonNode metadata = parseYaml(
                entries.get("g1/customer/content/1.content.registry.yaml"));
        assertEquals("content-v0", metadata.get("$type").asText());
        assertEquals(1, metadata.get("references").size());
        assertEquals("address",
                metadata.get("references").get(0).get("artifactId").asText());

        final JsonNode artifactYaml = parseYaml(
                entries.get("g1/customer/customer.registry.yaml"));
        assertEquals("./content/1.content.registry.yaml",
                artifactYaml.get("versions").get(0).get("contentMetadata").asText());
    }

    @Test
    void noContentMetadataWhenNoReferences() throws Exception {
        final GitOpsEntityCollector collector = createSingleArtifactCollector();
        final Map<String, byte[]> entries = writeAndExtract(collector);

        assertFalse(entries.keySet().stream().anyMatch(k -> k.endsWith(".content.registry.yaml")));
        final JsonNode yaml = parseYaml(
                entries.get("mygroup/my-artifact/my-artifact.registry.yaml"));
        assertNull(yaml.get("versions").get(0).get("contentMetadata"));
    }

    @Test
    void deduplicatesSharedContent() throws Exception {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final ContentEntity content = new ContentEntity();
        content.contentId = 1L;
        content.contentBytes = "shared content".getBytes(StandardCharsets.UTF_8);
        content.artifactType = "JSON";
        collector.collect(content);

        final ArtifactEntity artifact = new ArtifactEntity();
        artifact.groupId = "g1";
        artifact.artifactId = "a1";
        artifact.artifactType = "JSON";
        collector.collect(artifact);

        final ArtifactVersionEntity v1 = new ArtifactVersionEntity();
        v1.groupId = "g1";
        v1.artifactId = "a1";
        v1.version = "1.0";
        v1.versionOrder = 1;
        v1.globalId = 1L;
        v1.contentId = 1L;
        collector.collect(v1);

        final ArtifactVersionEntity v2 = new ArtifactVersionEntity();
        v2.groupId = "g1";
        v2.artifactId = "a1";
        v2.version = "2.0";
        v2.versionOrder = 2;
        v2.globalId = 2L;
        v2.contentId = 1L;
        collector.collect(v2);

        final Map<String, byte[]> entries = writeAndExtract(collector);

        assertTrue(entries.containsKey("g1/a1/content/1.0.json"));
        assertFalse(entries.containsKey("g1/a1/content/2.0.json"));

        final JsonNode yaml = parseYaml(entries.get("g1/a1/a1.registry.yaml"));
        assertEquals("./content/1.0.json", yaml.get("versions").get(0).get("content").asText());
        assertEquals("./content/1.0.json", yaml.get("versions").get(1).get("content").asText());
    }

    @Test
    void deduplicatedContentMetadataReferencesCorrectFile() throws Exception {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final ContentEntity content = new ContentEntity();
        content.contentId = 1L;
        content.contentBytes = "{\"type\":\"record\"}".getBytes(StandardCharsets.UTF_8);
        content.artifactType = "AVRO";
        content.serializedReferences = "[{\"groupId\":\"g1\",\"artifactId\":\"ref\",\"version\":\"1\",\"name\":\"Ref\"}]";
        collector.collect(content);

        final ArtifactEntity artifact = new ArtifactEntity();
        artifact.groupId = "g1";
        artifact.artifactId = "a1";
        artifact.artifactType = "AVRO";
        collector.collect(artifact);

        final ArtifactVersionEntity v1 = new ArtifactVersionEntity();
        v1.groupId = "g1";
        v1.artifactId = "a1";
        v1.version = "1.0";
        v1.versionOrder = 1;
        v1.globalId = 1L;
        v1.contentId = 1L;
        collector.collect(v1);

        final ArtifactVersionEntity v2 = new ArtifactVersionEntity();
        v2.groupId = "g1";
        v2.artifactId = "a1";
        v2.version = "2.0";
        v2.versionOrder = 2;
        v2.globalId = 2L;
        v2.contentId = 1L;
        collector.collect(v2);

        final Map<String, byte[]> entries = writeAndExtract(collector);

        assertTrue(entries.containsKey("g1/a1/content/1.0.content.registry.yaml"));
        assertTrue(entries.containsKey("g1/a1/content/2.0.content.registry.yaml"));

        final JsonNode meta1 = parseYaml(entries.get("g1/a1/content/1.0.content.registry.yaml"));
        assertEquals("./1.0.avsc", meta1.get("content").asText());

        final JsonNode meta2 = parseYaml(entries.get("g1/a1/content/2.0.content.registry.yaml"));
        assertEquals("./1.0.avsc", meta2.get("content").asText());
    }

    @Test
    void writesBranchesInArtifactYaml() throws Exception {
        final GitOpsEntityCollector collector = createSingleArtifactCollector();

        final BranchEntity branch = new BranchEntity();
        branch.groupId = "mygroup";
        branch.artifactId = "my-artifact";
        branch.branchId = "release-1";
        branch.description = "Release 1";
        branch.owner = "user";
        branch.createdOn = 1700000000000L;
        branch.versions = List.of("1.0");
        collector.collect(branch);

        final Map<String, byte[]> entries = writeAndExtract(collector);
        final JsonNode yaml = parseYaml(
                entries.get("mygroup/my-artifact/my-artifact.registry.yaml"));

        final JsonNode branches = yaml.get("branches");
        assertNotNull(branches);
        assertEquals(1, branches.size());
        assertEquals("release-1", branches.get(0).get("branchId").asText());
        assertEquals("Release 1", branches.get(0).get("description").asText());
    }

    @Test
    void writesCommentsInVersionEntries() throws Exception {
        final GitOpsEntityCollector collector = createSingleArtifactCollector();

        final CommentEntity comment = new CommentEntity();
        comment.globalId = 1L;
        comment.commentId = "c1";
        comment.owner = "reviewer";
        comment.createdOn = 1700000000000L;
        comment.value = "Approved";
        collector.collect(comment);

        final Map<String, byte[]> entries = writeAndExtract(collector);
        final JsonNode yaml = parseYaml(
                entries.get("mygroup/my-artifact/my-artifact.registry.yaml"));

        final JsonNode comments = yaml.get("versions").get(0).get("comments");
        assertNotNull(comments);
        assertEquals(1, comments.size());
        assertEquals("Approved", comments.get(0).get("value").asText());
        assertEquals("reviewer", comments.get(0).get("owner").asText());
    }

    @Test
    void handlesVersionWithNoContentEntity() throws Exception {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final ArtifactEntity artifact = new ArtifactEntity();
        artifact.groupId = "g1";
        artifact.artifactId = "empty";
        artifact.artifactType = "JSON";
        collector.collect(artifact);

        final ArtifactVersionEntity version = new ArtifactVersionEntity();
        version.groupId = "g1";
        version.artifactId = "empty";
        version.version = "1";
        version.versionOrder = 1;
        version.globalId = 1L;
        version.contentId = 999L;
        collector.collect(version);

        final Map<String, byte[]> entries = writeAndExtract(collector);

        assertTrue(entries.containsKey("g1/empty/empty.registry.yaml"));
        final JsonNode yaml = parseYaml(entries.get("g1/empty/empty.registry.yaml"));
        assertEquals(1, yaml.get("versions").size());
        assertEquals("./content/1.json", yaml.get("versions").get(0).get("content").asText());
        assertFalse(entries.containsKey("g1/empty/content/1.json"));
    }

    @Test
    void nullFieldsOmittedFromYaml() throws Exception {
        final GitOpsEntityCollector collector = createSingleArtifactCollector();
        final Map<String, byte[]> entries = writeAndExtract(collector);

        final JsonNode yaml = parseYaml(
                entries.get("mygroup/my-artifact/my-artifact.registry.yaml"));
        assertNull(yaml.get("registryIds"));
        assertNull(yaml.get("validatedUpTo"));
    }

    @Test
    void defaultGroupUsesDefaultDirectory() throws Exception {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final ContentEntity content = new ContentEntity();
        content.contentId = 1L;
        content.contentBytes = "{}".getBytes(StandardCharsets.UTF_8);
        content.artifactType = "JSON";
        collector.collect(content);

        final ArtifactEntity artifact = new ArtifactEntity();
        artifact.groupId = null;
        artifact.artifactId = "a1";
        artifact.artifactType = "JSON";
        collector.collect(artifact);

        final ArtifactVersionEntity version = new ArtifactVersionEntity();
        version.groupId = null;
        version.artifactId = "a1";
        version.version = "1";
        version.versionOrder = 1;
        version.globalId = 1L;
        version.contentId = 1L;
        collector.collect(version);

        final Map<String, byte[]> entries = writeAndExtract(collector);

        assertTrue(entries.containsKey("default/a1/a1.registry.yaml"));
        assertTrue(entries.containsKey("default/a1/content/1.json"));
    }

    @Test
    void emptyRegistryProducesOnlyRegistryYaml() throws Exception {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();
        final Map<String, byte[]> entries = writeAndExtract(collector);

        assertEquals(1, entries.size());
        assertTrue(entries.containsKey("registry.registry.yaml"));
    }

    @Test
    void timestampsFormattedAsIso8601() {
        assertEquals("2023-11-14T22:13:20Z", GitOpsZipWriter.epochToIso(1700000000000L));
        assertNull(GitOpsZipWriter.epochToIso(0L));
        assertNull(GitOpsZipWriter.epochToIso(-1L));
    }

    @Test
    void resolveExtensionByArtifactType() {
        assertEquals(".avsc", GitOpsZipWriter.resolveExtension("AVRO", null));
        assertEquals(".proto", GitOpsZipWriter.resolveExtension("PROTOBUF", null));
        assertEquals(".json", GitOpsZipWriter.resolveExtension("JSON", null));
        assertEquals(".yaml", GitOpsZipWriter.resolveExtension("OPENAPI", null));
        assertEquals(".graphql", GitOpsZipWriter.resolveExtension("GRAPHQL", null));
        assertEquals(".wsdl", GitOpsZipWriter.resolveExtension("WSDL", null));
        assertEquals(".xsd", GitOpsZipWriter.resolveExtension("XSD", null));
        assertEquals(".bin", GitOpsZipWriter.resolveExtension(null, null));
    }

    @Test
    void resolveExtensionPrefersContentType() {
        assertEquals(".yaml", GitOpsZipWriter.resolveExtension("OPENAPI", "application/yaml"));
        assertEquals(".xml", GitOpsZipWriter.resolveExtension("OPENAPI", "application/xml"));
        assertEquals(".proto", GitOpsZipWriter.resolveExtension("PROTOBUF", "application/protobuf"));
    }

    @Test
    void sanitizeRemovesPathSeparatorsAndTraversal() {
        assertEquals("a_b", GitOpsZipWriter.sanitize("a/b"));
        assertEquals("a_b", GitOpsZipWriter.sanitize("a\\b"));
        assertEquals("__", GitOpsZipWriter.sanitize(".."));
        assertEquals("a__b", GitOpsZipWriter.sanitize("a..b"));
        assertEquals("default", GitOpsZipWriter.sanitize(null));
        assertEquals("safe-name", GitOpsZipWriter.sanitize("safe-name"));
    }

    private GitOpsEntityCollector createSingleArtifactCollector() {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final ContentEntity content = new ContentEntity();
        content.contentId = 1L;
        content.contentBytes = "{\"type\":\"string\"}".getBytes(StandardCharsets.UTF_8);
        content.artifactType = "AVRO";
        collector.collect(content);

        final ArtifactEntity artifact = new ArtifactEntity();
        artifact.groupId = "mygroup";
        artifact.artifactId = "my-artifact";
        artifact.artifactType = "AVRO";
        artifact.owner = "user";
        artifact.createdOn = 1700000000000L;
        artifact.modifiedOn = 1700000000000L;
        collector.collect(artifact);

        final ArtifactVersionEntity version = new ArtifactVersionEntity();
        version.groupId = "mygroup";
        version.artifactId = "my-artifact";
        version.version = "1.0";
        version.versionOrder = 1;
        version.state = VersionState.ENABLED;
        version.globalId = 1L;
        version.contentId = 1L;
        version.createdOn = 1700000000000L;
        collector.collect(version);

        return collector;
    }

    private Map<String, byte[]> writeAndExtract(GitOpsEntityCollector collector) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            final GitOpsZipWriter writer = new GitOpsZipWriter(zos, collector);
            writer.write();
        }

        final Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(baos.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), zis.readAllBytes());
            }
        }
        return entries;
    }

    private JsonNode parseYaml(byte[] yamlBytes) throws IOException {
        return YAML_MAPPER.readTree(yamlBytes);
    }
}
