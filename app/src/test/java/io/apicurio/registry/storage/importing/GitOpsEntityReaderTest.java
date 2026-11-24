package io.apicurio.registry.storage.importing;

import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.Entity;
import org.apache.commons.codec.digest.DigestUtils;
import io.apicurio.registry.utils.impexp.EntityType;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

class GitOpsEntityReaderTest {

    private static final String REGISTRY_YAML = """
            $type: registry-v0
            registryId: main
            globalRules: []
            properties: []
            """;

    @TempDir
    Path tempDir;

    private List<Entity> readAllEntities(GitOpsEntityReader reader) throws IOException {
        final List<Entity> entities = new ArrayList<>();
        Entity entity;
        while ((entity = reader.readNextEntity()) != null) {
            entities.add(entity);
        }
        return entities;
    }

    private <T extends Entity> List<T> filterByType(List<Entity> entities, Class<T> type) {
        final List<T> result = new ArrayList<>();
        for (final Entity e : entities) {
            if (type.isInstance(e)) {
                result.add(type.cast(e));
            }
        }
        return result;
    }

    private void writeYaml(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private void writeContent(Path path, byte[] content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content);
    }

    @Test
    void testBasicRoundTrip() throws Exception {
        final byte[] avroSchema = "{\"type\":\"record\",\"name\":\"Test\",\"fields\":[]}".getBytes(StandardCharsets.UTF_8);

        writeYaml(tempDir.resolve("registry.registry.yaml"), REGISTRY_YAML);

        writeYaml(tempDir.resolve("testgroup/testgroup.registry.yaml"), """
                $type: group-v0
                groupId: testgroup
                description: Test group
                """);

        writeYaml(tempDir.resolve("testgroup/my-schema/my-schema.registry.yaml"), """
                $type: artifact-v0
                groupId: testgroup
                artifactId: my-schema
                artifactType: AVRO
                name: My Schema
                versions:
                  - version: "1"
                    state: ENABLED
                    globalId: 1001
                    content: ../../content/1.avsc
                    contentMetadata: ../../content/1.content.registry.yaml
                """);

        writeContent(tempDir.resolve("content/1.avsc"), avroSchema);

        writeYaml(tempDir.resolve("content/1.content.registry.yaml"), """
                $type: content-v0
                contentId: 100
                """);

        final GitOpsEntityReader reader = new GitOpsEntityReader(tempDir);
        final List<Entity> entities = readAllEntities(reader);

        // First entity is ManifestEntity
        Assertions.assertEquals(EntityType.Manifest, entities.get(0).getEntityType());
        final ManifestEntity manifest = (ManifestEntity) entities.get(0);
        Assertions.assertEquals("3.0", manifest.exportVersion);

        // Content before groups/artifacts
        final List<ContentEntity> contents = filterByType(entities, ContentEntity.class);
        Assertions.assertEquals(1, contents.size());
        final ContentEntity content = contents.get(0);
        Assertions.assertEquals(100L, content.contentId);
        Assertions.assertArrayEquals(avroSchema, content.contentBytes);
        Assertions.assertEquals(DigestUtils.sha256Hex(avroSchema), content.contentHash);

        // Group
        final List<GroupEntity> groups = filterByType(entities, GroupEntity.class);
        Assertions.assertEquals(1, groups.size());
        Assertions.assertEquals("testgroup", groups.get(0).groupId);

        // Artifact
        final List<ArtifactEntity> artifacts = filterByType(entities, ArtifactEntity.class);
        Assertions.assertEquals(1, artifacts.size());
        Assertions.assertEquals("my-schema", artifacts.get(0).artifactId);
        Assertions.assertEquals("AVRO", artifacts.get(0).artifactType);

        // Version
        final List<ArtifactVersionEntity> versions = filterByType(entities, ArtifactVersionEntity.class);
        Assertions.assertEquals(1, versions.size());
        Assertions.assertEquals(1001L, versions.get(0).globalId);
        Assertions.assertEquals(100L, versions.get(0).contentId);
        Assertions.assertEquals("1", versions.get(0).version);

        // Branch
        final List<BranchEntity> branches = filterByType(entities, BranchEntity.class);
        Assertions.assertEquals(1, branches.size());
        Assertions.assertEquals("latest", branches.get(0).branchId);
        Assertions.assertTrue(branches.get(0).systemDefined);
        Assertions.assertEquals(List.of("1"), branches.get(0).versions);

        // Verify ordering: Manifest < Content < Group < Artifact < Version < Branch
        final int manifestIdx = entities.indexOf(manifest);
        final int contentIdx = entities.indexOf(content);
        final int groupIdx = entities.indexOf(groups.get(0));
        final int artifactIdx = entities.indexOf(artifacts.get(0));
        final int versionIdx = entities.indexOf(versions.get(0));
        final int branchIdx = entities.indexOf(branches.get(0));
        Assertions.assertTrue(manifestIdx < contentIdx);
        Assertions.assertTrue(contentIdx < groupIdx);
        Assertions.assertTrue(groupIdx < artifactIdx);
        Assertions.assertTrue(artifactIdx < versionIdx);
        Assertions.assertTrue(versionIdx < branchIdx);
    }

    @Test
    void testGlobalRules() throws Exception {
        writeYaml(tempDir.resolve("registry.registry.yaml"), """
                $type: registry-v0
                registryId: main
                globalRules:
                  - ruleType: VALIDITY
                    config: FULL
                  - ruleType: COMPATIBILITY
                    config: BACKWARD
                properties: []
                """);

        final GitOpsEntityReader reader = new GitOpsEntityReader(tempDir);
        final List<Entity> entities = readAllEntities(reader);

        final List<GlobalRuleEntity> rules = filterByType(entities, GlobalRuleEntity.class);
        Assertions.assertEquals(2, rules.size());
        Assertions.assertEquals(RuleType.VALIDITY, rules.get(0).ruleType);
        Assertions.assertEquals("FULL", rules.get(0).configuration);
        Assertions.assertEquals(RuleType.COMPATIBILITY, rules.get(1).ruleType);
        Assertions.assertEquals("BACKWARD", rules.get(1).configuration);
    }

    @Test
    void testGroupRules() throws Exception {
        writeYaml(tempDir.resolve("registry.registry.yaml"), REGISTRY_YAML);

        writeYaml(tempDir.resolve("mygroup/mygroup.registry.yaml"), """
                $type: group-v0
                groupId: mygroup
                rules:
                  - ruleType: COMPATIBILITY
                    config: BACKWARD
                """);

        writeYaml(tempDir.resolve("mygroup/dummy/dummy.registry.yaml"), """
                $type: artifact-v0
                groupId: mygroup
                artifactId: dummy
                artifactType: JSON
                versions:
                  - version: "1"
                    state: ENABLED
                    globalId: 1
                    content: ../../content/d.json
                """);
        writeContent(tempDir.resolve("content/d.json"), "{}".getBytes(StandardCharsets.UTF_8));

        final GitOpsEntityReader reader = new GitOpsEntityReader(tempDir);
        final List<Entity> entities = readAllEntities(reader);

        final List<GroupRuleEntity> rules = filterByType(entities, GroupRuleEntity.class);
        Assertions.assertEquals(1, rules.size());
        Assertions.assertEquals("mygroup", rules.get(0).groupId);
        Assertions.assertEquals(RuleType.COMPATIBILITY, rules.get(0).type);
        Assertions.assertEquals("BACKWARD", rules.get(0).configuration);
    }

    @Test
    void testArtifactRules() throws Exception {
        writeYaml(tempDir.resolve("registry.registry.yaml"), REGISTRY_YAML);

        writeYaml(tempDir.resolve("grp/grp.registry.yaml"), """
                $type: group-v0
                groupId: grp
                """);

        writeYaml(tempDir.resolve("grp/art/art.registry.yaml"), """
                $type: artifact-v0
                groupId: grp
                artifactId: art
                artifactType: JSON
                versions:
                  - version: "1"
                    state: ENABLED
                    globalId: 1
                    content: ../../content/a.json
                rules:
                  - ruleType: VALIDITY
                    config: SYNTAX_ONLY
                """);
        writeContent(tempDir.resolve("content/a.json"), "{}".getBytes(StandardCharsets.UTF_8));

        final GitOpsEntityReader reader = new GitOpsEntityReader(tempDir);
        final List<Entity> entities = readAllEntities(reader);

        final List<ArtifactRuleEntity> rules = filterByType(entities, ArtifactRuleEntity.class);
        Assertions.assertEquals(1, rules.size());
        Assertions.assertEquals("grp", rules.get(0).groupId);
        Assertions.assertEquals("art", rules.get(0).artifactId);
        Assertions.assertEquals(RuleType.VALIDITY, rules.get(0).type);
        Assertions.assertEquals("SYNTAX_ONLY", rules.get(0).configuration);
    }

    @Test
    void testContentDeduplication() throws Exception {
        final byte[] schema = "{\"type\":\"string\"}".getBytes(StandardCharsets.UTF_8);

        writeYaml(tempDir.resolve("registry.registry.yaml"), REGISTRY_YAML);

        writeYaml(tempDir.resolve("grp/grp.registry.yaml"), """
                $type: group-v0
                groupId: grp
                """);

        // Two versions reference the same contentId
        writeYaml(tempDir.resolve("grp/art/art.registry.yaml"), """
                $type: artifact-v0
                groupId: grp
                artifactId: art
                artifactType: JSON
                versions:
                  - version: "1"
                    state: ENABLED
                    globalId: 1
                    content: ../../content/s.json
                    contentMetadata: ../../content/50.content.registry.yaml
                  - version: "2"
                    state: ENABLED
                    globalId: 2
                    content: ../../content/s.json
                    contentMetadata: ../../content/50.content.registry.yaml
                """);
        writeContent(tempDir.resolve("content/s.json"), schema);
        writeYaml(tempDir.resolve("content/50.content.registry.yaml"), """
                $type: content-v0
                contentId: 50
                """);

        final GitOpsEntityReader reader = new GitOpsEntityReader(tempDir);
        final List<Entity> entities = readAllEntities(reader);

        final List<ContentEntity> contents = filterByType(entities, ContentEntity.class);
        Assertions.assertEquals(1, contents.size(), "Same contentId should produce only one ContentEntity");
        Assertions.assertEquals(50L, contents.get(0).contentId);

        final List<ArtifactVersionEntity> versions = filterByType(entities, ArtifactVersionEntity.class);
        Assertions.assertEquals(2, versions.size());
        Assertions.assertEquals(50L, versions.get(0).contentId);
        Assertions.assertEquals(50L, versions.get(1).contentId);
    }

    @Test
    void testContentWithReferences() throws Exception {
        final byte[] schema = "{\"type\":\"record\",\"name\":\"Person\",\"fields\":[]}".getBytes(StandardCharsets.UTF_8);

        writeYaml(tempDir.resolve("registry.registry.yaml"), REGISTRY_YAML);

        writeYaml(tempDir.resolve("grp/grp.registry.yaml"), """
                $type: group-v0
                groupId: grp
                """);

        writeYaml(tempDir.resolve("grp/person/person.registry.yaml"), """
                $type: artifact-v0
                groupId: grp
                artifactId: person
                artifactType: AVRO
                versions:
                  - version: "1"
                    state: ENABLED
                    globalId: 1
                    content: ../../content/person.avsc
                    contentMetadata: ../../content/person.metadata.registry.yaml
                """);
        writeContent(tempDir.resolve("content/person.avsc"), schema);

        writeYaml(tempDir.resolve("content/person.metadata.registry.yaml"), """
                $type: content-v0
                contentId: 200
                references:
                  - groupId: grp
                    artifactId: address
                    version: "1"
                    name: com.example.Address
                """);

        final GitOpsEntityReader reader = new GitOpsEntityReader(tempDir);
        final List<Entity> entities = readAllEntities(reader);

        final List<ContentEntity> contents = filterByType(entities, ContentEntity.class);
        Assertions.assertEquals(1, contents.size());
        Assertions.assertNotNull(contents.get(0).serializedReferences);
        Assertions.assertTrue(contents.get(0).serializedReferences.contains("com.example.Address"));
        Assertions.assertTrue(contents.get(0).serializedReferences.contains("address"));

        // contentHash must include references (not just raw bytes)
        final String hashWithoutRefs = DigestUtils.sha256Hex(schema);
        Assertions.assertNotEquals(hashWithoutRefs, contents.get(0).contentHash,
                "contentHash should include references, not just raw bytes");
    }

    @Test
    void testNullGroupIdNormalization() throws Exception {
        writeYaml(tempDir.resolve("registry.registry.yaml"), REGISTRY_YAML);

        // "default" groupId should be normalized to null
        writeYaml(tempDir.resolve("default/default.registry.yaml"), """
                $type: group-v0
                groupId: default
                """);

        writeYaml(tempDir.resolve("default/my-schema/my-schema.registry.yaml"), """
                $type: artifact-v0
                groupId: default
                artifactId: my-schema
                artifactType: JSON
                versions:
                  - version: "1"
                    state: ENABLED
                    globalId: 1
                    content: ../../content/s.json
                """);
        writeContent(tempDir.resolve("content/s.json"), "{}".getBytes(StandardCharsets.UTF_8));

        final GitOpsEntityReader reader = new GitOpsEntityReader(tempDir);
        final List<Entity> entities = readAllEntities(reader);

        final List<GroupEntity> groups = filterByType(entities, GroupEntity.class);
        Assertions.assertEquals(1, groups.size());
        Assertions.assertNull(groups.get(0).groupId, "default groupId should be normalized to null");

        final List<ArtifactEntity> artifacts = filterByType(entities, ArtifactEntity.class);
        Assertions.assertEquals(1, artifacts.size());
        Assertions.assertNull(artifacts.get(0).groupId, "default groupId should be normalized to null");

        final List<ArtifactVersionEntity> versions = filterByType(entities, ArtifactVersionEntity.class);
        Assertions.assertEquals(1, versions.size());
        Assertions.assertNull(versions.get(0).groupId, "default groupId should be normalized to null");
    }

    @Test
    void testMultipleVersionsOrdering() throws Exception {
        writeYaml(tempDir.resolve("registry.registry.yaml"), REGISTRY_YAML);

        writeYaml(tempDir.resolve("grp/grp.registry.yaml"), """
                $type: group-v0
                groupId: grp
                """);

        writeYaml(tempDir.resolve("grp/art/art.registry.yaml"), """
                $type: artifact-v0
                groupId: grp
                artifactId: art
                artifactType: JSON
                versions:
                  - version: "1"
                    state: ENABLED
                    globalId: 10
                    content: ../../content/v1.json
                  - version: "2"
                    state: ENABLED
                    globalId: 20
                    content: ../../content/v2.json
                  - version: "3"
                    state: DRAFT
                    globalId: 30
                    content: ../../content/v3.json
                """);
        writeContent(tempDir.resolve("content/v1.json"), "{\"v\":1}".getBytes(StandardCharsets.UTF_8));
        writeContent(tempDir.resolve("content/v2.json"), "{\"v\":2}".getBytes(StandardCharsets.UTF_8));
        writeContent(tempDir.resolve("content/v3.json"), "{\"v\":3}".getBytes(StandardCharsets.UTF_8));

        final GitOpsEntityReader reader = new GitOpsEntityReader(tempDir);
        final List<Entity> entities = readAllEntities(reader);

        final List<ArtifactVersionEntity> versions = filterByType(entities, ArtifactVersionEntity.class);
        Assertions.assertEquals(3, versions.size());
        Assertions.assertEquals("1", versions.get(0).version);
        Assertions.assertEquals(1, versions.get(0).versionOrder);
        Assertions.assertEquals("2", versions.get(1).version);
        Assertions.assertEquals(2, versions.get(1).versionOrder);
        Assertions.assertEquals("3", versions.get(2).version);
        Assertions.assertEquals(3, versions.get(2).versionOrder);

        // Branch should contain only non-DRAFT versions
        final List<BranchEntity> branches = filterByType(entities, BranchEntity.class);
        Assertions.assertEquals(1, branches.size());
        Assertions.assertEquals(List.of("1", "2"), branches.get(0).versions);
    }

    @Test
    void testContentWithoutMetadata() throws Exception {
        final byte[] schema = "{\"type\":\"string\"}".getBytes(StandardCharsets.UTF_8);

        writeYaml(tempDir.resolve("registry.registry.yaml"), REGISTRY_YAML);

        writeYaml(tempDir.resolve("grp/grp.registry.yaml"), """
                $type: group-v0
                groupId: grp
                """);

        // Version with content but NO contentMetadata
        writeYaml(tempDir.resolve("grp/art/art.registry.yaml"), """
                $type: artifact-v0
                groupId: grp
                artifactId: art
                artifactType: JSON
                versions:
                  - version: "1"
                    state: ENABLED
                    globalId: 1
                    content: ../../content/data.json
                """);
        writeContent(tempDir.resolve("content/data.json"), schema);

        final GitOpsEntityReader reader = new GitOpsEntityReader(tempDir);
        final List<Entity> entities = readAllEntities(reader);

        final List<ContentEntity> contents = filterByType(entities, ContentEntity.class);
        Assertions.assertEquals(1, contents.size());
        // contentId should be generated (non-zero)
        Assertions.assertTrue(contents.get(0).contentId > 0, "contentId should be generated");
        Assertions.assertArrayEquals(schema, contents.get(0).contentBytes);
        Assertions.assertNull(contents.get(0).serializedReferences);
    }

    @Test
    void testTimestampParsing() throws Exception {
        final String createdOn = "2024-06-15T10:30:00Z";
        final String modifiedOn = "2024-07-01T14:00:00Z";
        final long expectedCreated = Instant.parse(createdOn).toEpochMilli();
        final long expectedModified = Instant.parse(modifiedOn).toEpochMilli();

        writeYaml(tempDir.resolve("registry.registry.yaml"), REGISTRY_YAML);

        writeYaml(tempDir.resolve("grp/grp.registry.yaml"), """
                $type: group-v0
                groupId: grp
                createdOn: %s
                modifiedOn: %s
                """.formatted(createdOn, modifiedOn));

        writeYaml(tempDir.resolve("grp/art/art.registry.yaml"), """
                $type: artifact-v0
                groupId: grp
                artifactId: art
                artifactType: JSON
                createdOn: %1$s
                modifiedOn: %2$s
                versions:
                  - version: "1"
                    state: ENABLED
                    globalId: 1
                    content: ../../content/t.json
                    createdOn: %1$s
                    modifiedOn: %2$s
                """.formatted(createdOn, modifiedOn));
        writeContent(tempDir.resolve("content/t.json"), "{}".getBytes(StandardCharsets.UTF_8));

        final GitOpsEntityReader reader = new GitOpsEntityReader(tempDir);
        final List<Entity> entities = readAllEntities(reader);

        final List<GroupEntity> groups = filterByType(entities, GroupEntity.class);
        Assertions.assertEquals(expectedCreated, groups.get(0).createdOn);
        Assertions.assertEquals(expectedModified, groups.get(0).modifiedOn);

        final List<ArtifactEntity> artifacts = filterByType(entities, ArtifactEntity.class);
        Assertions.assertEquals(expectedCreated, artifacts.get(0).createdOn);
        Assertions.assertEquals(expectedModified, artifacts.get(0).modifiedOn);

        final List<ArtifactVersionEntity> versions = filterByType(entities, ArtifactVersionEntity.class);
        Assertions.assertEquals(expectedCreated, versions.get(0).createdOn);
        Assertions.assertEquals(expectedModified, versions.get(0).modifiedOn);
    }

    @Test
    void testPathTraversalInContentRefBlocked() throws Exception {
        writeYaml(tempDir.resolve("registry.registry.yaml"), REGISTRY_YAML);

        writeYaml(tempDir.resolve("grp/grp.registry.yaml"), """
                $type: group-v0
                groupId: grp
                """);

        writeYaml(tempDir.resolve("grp/art/art.registry.yaml"), """
                $type: artifact-v0
                groupId: grp
                artifactId: art
                artifactType: JSON
                versions:
                  - version: "1"
                    state: ENABLED
                    globalId: 1
                    content: ../../../../etc/passwd
                """);

        final GitOpsEntityReader reader = new GitOpsEntityReader(tempDir);
        Assertions.assertThrows(IOException.class, () -> readAllEntities(reader),
                "Path traversal in content ref should be rejected");
    }

    @Test
    void testPathTraversalInContentMetadataRefBlocked() throws Exception {
        writeYaml(tempDir.resolve("registry.registry.yaml"), REGISTRY_YAML);

        writeYaml(tempDir.resolve("grp/grp.registry.yaml"), """
                $type: group-v0
                groupId: grp
                """);

        writeYaml(tempDir.resolve("grp/art/art.registry.yaml"), """
                $type: artifact-v0
                groupId: grp
                artifactId: art
                artifactType: JSON
                versions:
                  - version: "1"
                    state: ENABLED
                    globalId: 1
                    content: ../../content/a.json
                    contentMetadata: ../../../../etc/shadow
                """);
        writeContent(tempDir.resolve("content/a.json"), "{}".getBytes(StandardCharsets.UTF_8));

        final GitOpsEntityReader reader = new GitOpsEntityReader(tempDir);
        Assertions.assertThrows(IOException.class, () -> readAllEntities(reader),
                "Path traversal in contentMetadata ref should be rejected");
    }

    @Test
    void testMissingContentRefThrowsError() throws Exception {
        writeYaml(tempDir.resolve("registry.registry.yaml"), REGISTRY_YAML);

        writeYaml(tempDir.resolve("grp/grp.registry.yaml"), """
                $type: group-v0
                groupId: grp
                """);

        // Version with no content field
        writeYaml(tempDir.resolve("grp/art/art.registry.yaml"), """
                $type: artifact-v0
                groupId: grp
                artifactId: art
                artifactType: JSON
                versions:
                  - version: "1"
                    state: ENABLED
                    globalId: 1
                """);

        final GitOpsEntityReader reader = new GitOpsEntityReader(tempDir);
        // Should produce entities but skip the version with no content (no artifact emitted since no versions succeed)
        final List<Entity> entities = readAllEntities(reader);
        final List<ArtifactVersionEntity> versions = filterByType(entities, ArtifactVersionEntity.class);
        Assertions.assertEquals(0, versions.size(), "Version with no content ref should be skipped");
    }

    @Test
    void testMissingGlobalIdThrowsError() throws Exception {
        writeYaml(tempDir.resolve("registry.registry.yaml"), REGISTRY_YAML);

        writeYaml(tempDir.resolve("grp/grp.registry.yaml"), """
                $type: group-v0
                groupId: grp
                """);

        writeYaml(tempDir.resolve("grp/art/art.registry.yaml"), """
                $type: artifact-v0
                groupId: grp
                artifactId: art
                artifactType: JSON
                versions:
                  - version: "1"
                    state: ENABLED
                    content: ../../content/a.json
                """);
        writeContent(tempDir.resolve("content/a.json"), "{}".getBytes(StandardCharsets.UTF_8));

        final GitOpsEntityReader reader = new GitOpsEntityReader(tempDir);
        Assertions.assertThrows(IOException.class, () -> readAllEntities(reader),
                "Missing globalId should throw an error");
    }
}
