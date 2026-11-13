package io.apicurio.registry.rest.v3.impl.shared.gitops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
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

        final Map<String, byte[]> entries = writeAndExtract(collector, new HashMap<>());

        assertTrue(entries.containsKey("registry.registry.yaml"));
        final JsonNode yaml = parseYaml(entries.get("registry.registry.yaml"));
        assertEquals("registry-v0", yaml.get("$type").asText());
        assertEquals("main", yaml.get("registryId").asText());
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

        final Map<String, byte[]> entries = writeAndExtract(collector, new HashMap<>());

        assertTrue(entries.containsKey("orders/orders.registry.yaml"));
        final JsonNode yaml = parseYaml(entries.get("orders/orders.registry.yaml"));
        assertEquals("group-v0", yaml.get("$type").asText());
        assertEquals("orders", yaml.get("groupId").asText());
        assertEquals("Order schemas", yaml.get("description").asText());
        assertEquals(1, yaml.get("rules").size());
    }

    @Test
    void writesArtifactYamlWithVersions() throws Exception {
        final TestData data = createSingleArtifactData();
        final Map<String, byte[]> entries = writeAndExtract(data.collector, data.contentPaths, data.contentBytes);

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
        assertTrue(versions.get(0).get("content").asText().contains("content/"));
    }

    @Test
    void contentFilesInSharedDirectory() throws Exception {
        final TestData data = createSingleArtifactData();
        final Map<String, byte[]> entries = writeAndExtract(data.collector, data.contentPaths, data.contentBytes);

        assertTrue(entries.containsKey("content/1.avsc"));
        assertEquals("{\"type\":\"string\"}", new String(entries.get("content/1.avsc"), StandardCharsets.UTF_8));

        assertFalse(entries.keySet().stream()
                .anyMatch(k -> k.startsWith("mygroup/my-artifact/content/")));
    }

    @Test
    void versionReferencesSharedContent() throws Exception {
        final TestData data = createSingleArtifactData();
        final Map<String, byte[]> entries = writeAndExtract(data.collector, data.contentPaths, data.contentBytes);

        final JsonNode yaml = parseYaml(
                entries.get("mygroup/my-artifact/my-artifact.registry.yaml"));
        final String contentRef = yaml.get("versions").get(0).get("content").asText();
        assertEquals("../../content/1.avsc", contentRef);
    }

    @Test
    void writesContentMetadataWhenReferencesExist() throws Exception {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();
        final Map<Long, String> contentPaths = new HashMap<>();

        final ContentEntity content = new ContentEntity();
        content.contentId = 1L;
        content.contentBytes = "{\"type\":\"record\"}".getBytes(StandardCharsets.UTF_8);
        content.artifactType = "AVRO";
        content.serializedReferences = "[{\"groupId\":\"g1\",\"artifactId\":\"address\",\"version\":\"1\",\"name\":\"Address\"}]";
        collector.collect(content);
        contentPaths.put(1L, "content/1.avsc");

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

        final Map<String, byte[]> entries = writeAndExtract(collector, contentPaths);

        assertTrue(entries.containsKey("content/1.content.registry.yaml"));
        final JsonNode metadata = parseYaml(entries.get("content/1.content.registry.yaml"));
        assertEquals("content-v0", metadata.get("$type").asText());
        assertEquals(1, metadata.get("references").size());
        assertEquals("address", metadata.get("references").get(0).get("artifactId").asText());
    }

    @Test
    void contentMetadataWrittenWithoutReferences() throws Exception {
        final TestData data = createSingleArtifactData();
        final Map<String, byte[]> entries = writeAndExtract(data.collector, data.contentPaths, data.contentBytes);

        assertTrue(entries.keySet().stream().anyMatch(k -> k.endsWith(".content.registry.yaml")),
                "Content metadata should be written even without references to preserve contentId");
        final JsonNode metadataYaml = parseYaml(
                entries.get("content/1.content.registry.yaml"));
        assertEquals("content-v0", metadataYaml.get("$type").asText());
        assertEquals(1, metadataYaml.get("contentId").asLong());
        assertTrue(metadataYaml.get("references") == null || metadataYaml.get("references").isNull(),
                "References should be absent when content has none");
    }

    @Test
    void sharedContentDeduplicatesAcrossArtifacts() throws Exception {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();
        final Map<Long, String> contentPaths = new HashMap<>();

        final ContentEntity content = new ContentEntity();
        content.contentId = 1L;
        content.contentBytes = "shared content".getBytes(StandardCharsets.UTF_8);
        content.artifactType = "JSON";
        collector.collect(content);
        contentPaths.put(1L, "content/1.json");

        final ArtifactEntity a1 = new ArtifactEntity();
        a1.groupId = "g1";
        a1.artifactId = "a1";
        a1.artifactType = "JSON";
        collector.collect(a1);

        final ArtifactEntity a2 = new ArtifactEntity();
        a2.groupId = "g1";
        a2.artifactId = "a2";
        a2.artifactType = "JSON";
        collector.collect(a2);

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
        v2.artifactId = "a2";
        v2.version = "1.0";
        v2.versionOrder = 1;
        v2.globalId = 2L;
        v2.contentId = 1L;
        collector.collect(v2);

        final Map<String, byte[]> entries = writeAndExtract(collector, contentPaths);

        long contentFileCount = entries.keySet().stream()
                .filter(k -> k.startsWith("content/") && !k.endsWith(".registry.yaml"))
                .count();
        assertEquals(1, contentFileCount, "Shared content should appear once");

        final JsonNode a1Yaml = parseYaml(entries.get("g1/a1/a1.registry.yaml"));
        final JsonNode a2Yaml = parseYaml(entries.get("g1/a2/a2.registry.yaml"));
        assertEquals("../../content/1.json", a1Yaml.get("versions").get(0).get("content").asText());
        assertEquals("../../content/1.json", a2Yaml.get("versions").get(0).get("content").asText());
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

        final Map<String, byte[]> entries = writeAndExtract(collector, new HashMap<>());

        assertTrue(entries.containsKey("g1/empty/empty.registry.yaml"));
        final JsonNode yaml = parseYaml(entries.get("g1/empty/empty.registry.yaml"));
        assertEquals(1, yaml.get("versions").size());
        assertTrue(yaml.get("versions").get(0).get("content").asText().contains("content/999.bin"));
    }

    @Test
    void nullFieldsOmittedFromYaml() throws Exception {
        final TestData data = createSingleArtifactData();
        final Map<String, byte[]> entries = writeAndExtract(data.collector, data.contentPaths, data.contentBytes);

        final JsonNode yaml = parseYaml(
                entries.get("mygroup/my-artifact/my-artifact.registry.yaml"));
        assertNull(yaml.get("registryIds"));
        assertNull(yaml.get("validatedUpTo"));
    }

    @Test
    void defaultGroupUsesDefaultDirectory() throws Exception {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();
        final Map<Long, String> contentPaths = new HashMap<>();

        final ContentEntity content = new ContentEntity();
        content.contentId = 1L;
        content.contentBytes = "{}".getBytes(StandardCharsets.UTF_8);
        content.artifactType = "JSON";
        collector.collect(content);
        contentPaths.put(1L, "content/1.json");

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

        final Map<String, byte[]> entries = writeAndExtract(collector, contentPaths);

        assertTrue(entries.containsKey("default/a1/a1.registry.yaml"));
        assertTrue(entries.containsKey("content/1.json"));
    }

    @Test
    void emptyRegistryProducesOnlyRegistryYaml() throws Exception {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();
        final Map<String, byte[]> entries = writeAndExtract(collector, new HashMap<>());

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
    void resolveExtensionPrefersContentTypeForMultiFormat() {
        assertEquals(".json", GitOpsZipWriter.resolveExtension("OPENAPI", "application/json"));
        assertEquals(".json", GitOpsZipWriter.resolveExtension("ASYNCAPI", "application/json"));
        assertEquals(".yaml", GitOpsZipWriter.resolveExtension("OPENAPI", "application/yaml"));
        assertEquals(".yaml", GitOpsZipWriter.resolveExtension("ASYNCAPI", null));
    }

    @Test
    void resolveExtensionPreservesArtifactTypeSpecificExtension() {
        assertEquals(".avsc", GitOpsZipWriter.resolveExtension("AVRO", "application/json"));
        assertEquals(".proto", GitOpsZipWriter.resolveExtension("PROTOBUF", "application/protobuf"));
        assertEquals(".avsc", GitOpsZipWriter.resolveExtension("AVRO", null));
    }

    @Test
    void sanitizeRemovesPathSeparatorsAndTraversal() {
        assertEquals("a_b", GitOpsZipWriter.sanitize("a/b"));
        assertEquals("a_b", GitOpsZipWriter.sanitize("a\\b"));
        assertEquals("__", GitOpsZipWriter.sanitize(".."));
        assertEquals("a__b", GitOpsZipWriter.sanitize("a..b"));
        assertEquals("default", GitOpsZipWriter.sanitize(null));
        assertEquals("default", GitOpsZipWriter.sanitize(""));
        assertEquals("safe-name", GitOpsZipWriter.sanitize("safe-name"));
        assertEquals("______x", GitOpsZipWriter.sanitize("../../x"));
        assertEquals("___x", GitOpsZipWriter.sanitize("../x"));
        assertEquals("_", GitOpsZipWriter.sanitize("."));
    }

    @Test
    void groupIdsSanitizedInPaths() throws Exception {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();

        final GroupEntity group = new GroupEntity();
        group.groupId = "../../evil";
        group.description = "traversal attempt";
        collector.collect(group);

        final Map<String, byte[]> entries = writeAndExtract(collector, new HashMap<>());

        assertFalse(entries.keySet().stream().anyMatch(k -> k.contains("..")));
        assertTrue(entries.containsKey("______evil/______evil.registry.yaml"));
    }

    @Test
    void collisionProducesSuffixedPath() throws Exception {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();
        final Map<Long, String> contentPaths = new HashMap<>();

        final ContentEntity c1 = new ContentEntity();
        c1.contentId = 1L;
        c1.contentBytes = "content1".getBytes(StandardCharsets.UTF_8);
        collector.collect(c1);
        contentPaths.put(1L, "content/1.bin");

        final ContentEntity c2 = new ContentEntity();
        c2.contentId = 2L;
        c2.contentBytes = "content2".getBytes(StandardCharsets.UTF_8);
        collector.collect(c2);
        contentPaths.put(2L, "content/2.bin");

        final ArtifactEntity a1 = new ArtifactEntity();
        a1.groupId = "g";
        a1.artifactId = "a/b";
        a1.artifactType = "JSON";
        collector.collect(a1);

        final ArtifactEntity a2 = new ArtifactEntity();
        a2.groupId = "g";
        a2.artifactId = "a_b";
        a2.artifactType = "JSON";
        collector.collect(a2);

        final ArtifactVersionEntity v1 = new ArtifactVersionEntity();
        v1.groupId = "g";
        v1.artifactId = "a/b";
        v1.version = "1";
        v1.versionOrder = 1;
        v1.globalId = 1L;
        v1.contentId = 1L;
        collector.collect(v1);

        final ArtifactVersionEntity v2 = new ArtifactVersionEntity();
        v2.groupId = "g";
        v2.artifactId = "a_b";
        v2.version = "1";
        v2.versionOrder = 1;
        v2.globalId = 2L;
        v2.contentId = 2L;
        collector.collect(v2);

        final Map<String, byte[]> entries = writeAndExtract(collector, contentPaths);

        assertTrue(entries.containsKey("g/a_b/a_b.registry.yaml"));
        boolean hasSuffixed = entries.keySet().stream()
                .anyMatch(k -> k.contains("a_b") && k.contains("_2") && k.endsWith(".yaml"));
        assertTrue(hasSuffixed, "Colliding path should get a _2 suffix: " + entries.keySet());
    }

    @Test
    void relativePathFromArtifactCalculatedCorrectly() {
        assertEquals("../../content/1.avsc",
                GitOpsZipWriter.relativePathFromArtifact("mygroup/my-artifact", "content/1.avsc"));
        assertEquals("../../../content/1.json",
                GitOpsZipWriter.relativePathFromArtifact("a/b/c", "content/1.json"));
    }

    private TestData createSingleArtifactData() {
        final GitOpsEntityCollector collector = new GitOpsEntityCollector();
        final Map<Long, String> contentPaths = new HashMap<>();
        final Map<Long, byte[]> contentBytesMap = new HashMap<>();

        final ContentEntity content = new ContentEntity();
        content.contentId = 1L;
        content.contentBytes = "{\"type\":\"string\"}".getBytes(StandardCharsets.UTF_8);
        content.artifactType = "AVRO";
        collector.collect(content);
        contentPaths.put(1L, "content/1.avsc");
        contentBytesMap.put(1L, content.contentBytes);

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

        return new TestData(collector, contentPaths, contentBytesMap);
    }

    private Map<String, byte[]> writeAndExtract(GitOpsEntityCollector collector,
            Map<Long, String> contentPaths) throws IOException {
        return writeAndExtract(collector, contentPaths, new HashMap<>());
    }

    private Map<String, byte[]> writeAndExtract(GitOpsEntityCollector collector,
            Map<Long, String> contentPaths,
            Map<Long, byte[]> contentBytes) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (final Map.Entry<Long, String> entry : contentPaths.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getValue()));
                final byte[] bytes = contentBytes.get(entry.getKey());
                if (bytes != null) {
                    zos.write(bytes);
                }
                zos.closeEntry();
            }

            final GitOpsZipWriter writer = new GitOpsZipWriter(zos, collector, contentPaths);
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

    private record TestData(GitOpsEntityCollector collector, Map<Long, String> contentPaths,
            Map<Long, byte[]> contentBytes) {
    }
}
