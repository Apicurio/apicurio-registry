package io.apicurio.registry.storage.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.impl.polling.model.Type;
import io.apicurio.registry.storage.impl.polling.model.v0.Artifact;
import io.apicurio.registry.storage.impl.polling.model.v0.Content;
import io.apicurio.registry.storage.impl.polling.model.v0.Group;
import io.apicurio.registry.storage.impl.polling.model.v0.Registry;
import io.apicurio.registry.storage.impl.polling.model.v0.Rule;
import io.apicurio.registry.storage.impl.polling.model.v0.Version;
import io.apicurio.registry.storage.impl.polling.TimestampParser;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GitOpsEntityReader {

    private static final ObjectMapper YAML_MAPPER;
    private static final String DEFAULT_GROUP_ID = "default";

    static {
        final YAMLFactory yamlFactory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build();
        YAML_MAPPER = new ObjectMapper(yamlFactory);
        YAML_MAPPER.findAndRegisterModules();
    }

    private final Path importDirectory;
    private List<Entity> entities;
    private int currentIndex;

    public GitOpsEntityReader(Path importDirectory) {
        this.importDirectory = importDirectory;
    }

    public Entity readNextEntity() throws IOException {
        if (entities == null) {
            buildEntityList();
        }
        if (currentIndex >= entities.size()) {
            return null;
        }
        return entities.get(currentIndex++);
    }

    private void buildEntityList() throws IOException {
        final Map<Path, ParsedFile> parsedFiles = scanAndParseYamlFiles();

        Registry registry = null;
        final Map<String, GroupWithPath> groupsByGroupId = new LinkedHashMap<>();
        final List<ArtifactWithPath> artifactList = new ArrayList<>();

        for (final Map.Entry<Path, ParsedFile> entry : parsedFiles.entrySet()) {
            final ParsedFile parsed = entry.getValue();
            switch (parsed.type) {
                case REGISTRY:
                    registry = (Registry) parsed.entity;
                    break;
                case GROUP:
                    final Group group = (Group) parsed.entity;
                    groupsByGroupId.put(group.getGroupId(), new GroupWithPath(group, entry.getKey()));
                    break;
                case ARTIFACT:
                    final Artifact artifact = (Artifact) parsed.entity;
                    artifactList.add(new ArtifactWithPath(artifact, entry.getKey()));
                    break;
                case CONTENT:
                    break;
            }
        }

        entities = new ArrayList<>();

        final ManifestEntity manifest = new ManifestEntity();
        manifest.exportVersion = "3.0";
        manifest.systemName = "Apicurio Registry (GitOps Import)";
        manifest.systemDescription = "Imported from GitOps format";
        manifest.systemVersion = "3.0.0";
        entities.add(manifest);

        if (registry != null) {
            addGlobalRules(registry);
        }

        final Set<Long> emittedContentIds = new HashSet<>();
        final Map<String, Long> contentPathToId = new LinkedHashMap<>();

        for (final ArtifactWithPath awp : artifactList) {
            addContentEntities(awp, parsedFiles, emittedContentIds, contentPathToId);
        }

        final Set<String> emittedGroupIds = new HashSet<>();
        for (final ArtifactWithPath awp : artifactList) {
            final String groupId = awp.artifact.getGroupId();
            if (emittedGroupIds.add(groupId)) {
                final GroupWithPath gwp = groupsByGroupId.get(groupId);
                if (gwp != null) {
                    addGroupEntity(gwp.group);
                    addGroupRules(gwp.group);
                }
            }
        }

        // Also emit groups that have no artifacts (empty groups with rules)
        for (final Map.Entry<String, GroupWithPath> entry : groupsByGroupId.entrySet()) {
            if (emittedGroupIds.add(entry.getKey())) {
                addGroupEntity(entry.getValue().group);
                addGroupRules(entry.getValue().group);
            }
        }

        for (final ArtifactWithPath awp : artifactList) {
            addArtifactEntities(awp, contentPathToId);
        }
    }

    private void addGlobalRules(Registry registry) throws IOException {
        final List<Rule> rules = registry.getGlobalRules();
        if (rules == null) {
            return;
        }
        for (final Rule rule : rules) {
            final GlobalRuleEntity entity = new GlobalRuleEntity();
            entity.ruleType = parseRuleType(rule.getRuleType());
            entity.configuration = rule.getConfig();
            entities.add(entity);
        }
    }

    private void addGroupEntity(Group group) {
        final GroupEntity entity = new GroupEntity();
        entity.groupId = normalizeGroupId(group.getGroupId());
        entity.description = group.getDescription();
        entity.labels = group.getLabels();
        entity.owner = group.getOwner();
        entity.artifactsType = group.getArtifactsType();
        entity.createdOn = parseTimestamp(group.getCreatedOn());
        entity.modifiedOn = parseTimestamp(group.getModifiedOn());
        entities.add(entity);
    }

    private void addGroupRules(Group group) throws IOException {
        final List<Rule> rules = group.getRules();
        if (rules == null) {
            return;
        }
        for (final Rule rule : rules) {
            final GroupRuleEntity entity = new GroupRuleEntity();
            entity.groupId = normalizeGroupId(group.getGroupId());
            entity.type = parseRuleType(rule.getRuleType());
            entity.configuration = rule.getConfig();
            entities.add(entity);
        }
    }

    private void addContentEntities(ArtifactWithPath awp, Map<Path, ParsedFile> parsedFiles,
                                    Set<Long> emittedContentIds, Map<String, Long> contentPathToId) throws IOException {
        final List<Version> versions = awp.artifact.getVersions();
        if (versions == null) {
            return;
        }

        final Path artifactDir = awp.yamlPath.getParent();
        for (final Version version : versions) {
            final String contentRef = version.getContent();
            if (contentRef != null && !contentRef.isBlank()) {
                final Path contentPath = validatePath(
                        artifactDir.resolve(contentRef).normalize(), contentRef);
                if (!contentPathToId.containsKey(contentPath.toString())) {
                    final Content contentMetadata = resolveContentMetadata(
                            artifactDir, version.getContentMetadata(), parsedFiles);
                    addSingleContentEntity(contentPath, contentMetadata, awp.artifact.getArtifactType(),
                            emittedContentIds, contentPathToId);
                }
            }
        }
    }

    private Content resolveContentMetadata(Path artifactDir, String metadataRef,
                                           Map<Path, ParsedFile> parsedFiles) throws IOException {
        if (metadataRef == null || metadataRef.isBlank()) {
            return null;
        }
        final Path metadataPath = validatePath(
                artifactDir.resolve(metadataRef).normalize(), metadataRef);
        final ParsedFile metaParsed = parsedFiles.get(metadataPath);
        if (metaParsed != null && metaParsed.type == Type.CONTENT) {
            return (Content) metaParsed.entity;
        }
        return null;
    }

    private void addSingleContentEntity(Path contentPath, Content contentMetadata, String artifactType,
                                        Set<Long> emittedContentIds,
                                        Map<String, Long> contentPathToId) throws IOException {
        if (!Files.exists(contentPath)) {
            throw new IOException("Content file not found: " + importDirectory.relativize(contentPath));
        }

        final byte[] contentBytes = Files.readAllBytes(contentPath);
        final String serializedRefs = serializeReferences(contentMetadata);
        final String contentHash = computeContentHash(contentBytes, serializedRefs);
        final long contentId = resolveContentId(contentMetadata, contentHash);

        contentPathToId.put(contentPath.toString(), contentId);

        if (!emittedContentIds.add(contentId)) {
            return;
        }

        final ContentEntity entity = new ContentEntity();
        entity.contentId = contentId;
        entity.contentHash = contentHash;
        entity.contentBytes = contentBytes;
        entity.contentType = detectContentType(contentPath.toString());
        entity.artifactType = artifactType;
        entity.serializedReferences = serializedRefs;
        entities.add(entity);
    }

    private static long resolveContentId(Content contentMetadata, String contentHash) {
        if (contentMetadata != null && contentMetadata.getContentId() != null) {
            return contentMetadata.getContentId();
        }
        return Long.parseUnsignedLong(contentHash.substring(0, 15), 16);
    }

    private void addArtifactEntities(ArtifactWithPath awp,
                                     Map<String, Long> contentPathToId) throws IOException {
        final Artifact artifact = awp.artifact;
        final List<Version> versions = artifact.getVersions();
        if (versions == null || versions.isEmpty()) {
            return;
        }

        final String normalizedGroupId = normalizeGroupId(artifact.getGroupId());
        final Path artifactDir = awp.yamlPath.getParent();
        final String artifactCoord = artifact.getGroupId() + ":" + artifact.getArtifactId();

        validateGlobalIds(versions, artifactCoord);

        final ArtifactEntity artifactEntity = buildArtifactEntity(artifact, normalizedGroupId);

        boolean artifactEmitted = false;
        final List<String> publishedVersions = new ArrayList<>();

        for (int i = 0; i < versions.size(); i++) {
            final Version version = versions.get(i);
            final long contentId = resolveVersionContentId(
                    version, artifactDir, contentPathToId, artifactCoord);
            if (contentId < 0) {
                continue;
            }

            if (!artifactEmitted) {
                entities.add(artifactEntity);
                artifactEmitted = true;
            }

            final ArtifactVersionEntity versionEntity = buildVersionEntity(
                    version, normalizedGroupId, artifact.getArtifactId(), i + 1, contentId);
            entities.add(versionEntity);

            if (versionEntity.state != VersionState.DRAFT) {
                publishedVersions.add(versionEntity.version);
            }
        }

        addLatestBranch(publishedVersions, normalizedGroupId, artifact);
        addArtifactRules(artifact, normalizedGroupId);
    }

    private void validateGlobalIds(List<Version> versions, String artifactCoord) throws IOException {
        for (final Version version : versions) {
            final String contentRef = version.getContent();
            if (contentRef == null || contentRef.isBlank()) {
                continue;
            }
            if (version.getGlobalId() == null) {
                throw new IOException("globalId is required for version "
                        + artifactCoord + ":" + version.getVersion());
            }
        }
    }

    private ArtifactEntity buildArtifactEntity(Artifact artifact, String normalizedGroupId) {
        final ArtifactEntity entity = new ArtifactEntity();
        entity.groupId = normalizedGroupId;
        entity.artifactId = artifact.getArtifactId();
        entity.artifactType = artifact.getArtifactType();
        entity.name = artifact.getName();
        entity.description = artifact.getDescription();
        entity.labels = artifact.getLabels();
        entity.owner = artifact.getOwner();
        entity.createdOn = parseTimestamp(artifact.getCreatedOn());
        entity.modifiedOn = parseTimestamp(artifact.getModifiedOn());
        return entity;
    }

    private long resolveVersionContentId(Version version, Path artifactDir,
                                         Map<String, Long> contentPathToId,
                                         String artifactCoord) throws IOException {
        final String contentRef = version.getContent();
        if (contentRef == null || contentRef.isBlank()) {
            return -1;
        }
        final Path contentPath = validatePath(
                artifactDir.resolve(contentRef).normalize(), contentRef);
        final Long resolved = contentPathToId.get(contentPath.toString());
        if (resolved == null) {
            throw new IOException("Could not resolve content for version "
                    + artifactCoord + ":" + version.getVersion());
        }
        return resolved;
    }

    private ArtifactVersionEntity buildVersionEntity(Version version, String normalizedGroupId,
                                                     String artifactId, int versionOrder,
                                                     long contentId) throws IOException {
        final ArtifactVersionEntity entity = new ArtifactVersionEntity();
        entity.groupId = normalizedGroupId;
        entity.artifactId = artifactId;
        entity.version = version.getVersion();
        entity.versionOrder = versionOrder;
        entity.state = parseVersionState(version.getState());
        entity.name = version.getName();
        entity.description = version.getDescription();
        entity.labels = version.getLabels();
        entity.owner = version.getOwner();
        entity.createdOn = parseTimestamp(version.getCreatedOn());
        entity.modifiedOn = parseTimestamp(version.getModifiedOn());
        entity.globalId = version.getGlobalId();
        entity.contentId = contentId;
        return entity;
    }

    private void addLatestBranch(List<String> publishedVersions, String normalizedGroupId,
                                 Artifact artifact) {
        if (publishedVersions.isEmpty()) {
            return;
        }
        final long time = parseTimestamp(artifact.getModifiedOn());
        entities.add(BranchEntity.builder()
                .groupId(normalizedGroupId)
                .artifactId(artifact.getArtifactId())
                .branchId(BranchId.LATEST.getRawBranchId())
                .systemDefined(true)
                .owner(artifact.getOwner())
                .createdOn(time)
                .modifiedOn(time)
                .versions(publishedVersions)
                .build());
    }

    private void addArtifactRules(Artifact artifact, String normalizedGroupId) throws IOException {
        final List<Rule> rules = artifact.getRules();
        if (rules == null) {
            return;
        }
        for (final Rule rule : rules) {
            final ArtifactRuleEntity entity = new ArtifactRuleEntity();
            entity.groupId = normalizedGroupId;
            entity.artifactId = artifact.getArtifactId();
            entity.type = parseRuleType(rule.getRuleType());
            entity.configuration = rule.getConfig();
            entities.add(entity);
        }
    }

    private Map<Path, ParsedFile> scanAndParseYamlFiles() throws IOException {
        final Map<Path, ParsedFile> result = new LinkedHashMap<>();
        Files.walkFileTree(importDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                final String fileName = file.getFileName().toString();
                if (fileName.endsWith(".registry.yaml") || fileName.endsWith(".registry.yml")) {
                    final byte[] bytes = Files.readAllBytes(file);
                    final var tree = YAML_MAPPER.readTree(bytes);
                    final var typeNode = tree.get("$type");
                    if (typeNode != null && typeNode.isTextual()) {
                        final var type = Type.from(typeNode.textValue());
                        if (type.isPresent()) {
                            final Object entity = YAML_MAPPER.treeToValue(tree, type.get().getKlass());
                            result.put(file.normalize(), new ParsedFile(type.get(), entity));
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }

    private static String normalizeGroupId(String groupId) {
        if (DEFAULT_GROUP_ID.equals(groupId)) {
            return null;
        }
        return groupId;
    }

    private static long parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return TimestampParser.parse(value, Instant.EPOCH);
    }

    private static RuleType parseRuleType(String value) throws IOException {
        try {
            return RuleType.fromValue(value);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid rule type: " + value, e);
        }
    }

    private static VersionState parseVersionState(String value) throws IOException {
        if (value == null) {
            return VersionState.ENABLED;
        }
        try {
            return VersionState.fromValue(value);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid version state: " + value, e);
        }
    }

    private static String computeContentHash(byte[] contentBytes, String serializedRefs) {
        if (serializedRefs != null && !serializedRefs.isEmpty()) {
            final byte[] refsBytes = serializedRefs.getBytes(StandardCharsets.UTF_8);
            final byte[] combined = new byte[contentBytes.length + refsBytes.length];
            System.arraycopy(contentBytes, 0, combined, 0, contentBytes.length);
            System.arraycopy(refsBytes, 0, combined, contentBytes.length, refsBytes.length);
            return DigestUtils.sha256Hex(combined);
        }
        return DigestUtils.sha256Hex(contentBytes);
    }

    private Path validatePath(Path resolved, String ref) throws IOException {
        if (!resolved.startsWith(importDirectory)) {
            throw new IOException("Path escapes import directory: " + ref);
        }
        return resolved;
    }

    private static String detectContentType(String path) {
        final String lowerPath = path.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")) {
            return ContentTypes.APPLICATION_YAML;
        } else if (lowerPath.endsWith(".xml") || lowerPath.endsWith(".wsdl")
                || lowerPath.endsWith(".xsd")) {
            return ContentTypes.APPLICATION_XML;
        } else if (lowerPath.endsWith(".proto")) {
            return ContentTypes.APPLICATION_PROTOBUF;
        } else if (lowerPath.endsWith(".graphql") || lowerPath.endsWith(".gql")) {
            return ContentTypes.APPLICATION_GRAPHQL;
        } else if (lowerPath.endsWith(".thrift")) {
            return ContentTypes.APPLICATION_THRIFT;
        }
        return ContentTypes.APPLICATION_JSON;
    }

    private static String serializeReferences(Content contentMetadata) {
        if (contentMetadata == null || contentMetadata.getReferences() == null
                || contentMetadata.getReferences().isEmpty()) {
            return null;
        }
        final List<ArtifactReferenceDto> refs = contentMetadata.getReferences().stream()
                .map(ref -> ArtifactReferenceDto.builder()
                        .groupId(ref.getGroupId())
                        .artifactId(ref.getArtifactId())
                        .version(ref.getVersion())
                        .name(ref.getName())
                        .build())
                .collect(Collectors.toList());
        return RegistryContentUtils.serializeReferences(refs);
    }

    private record ParsedFile(Type type, Object entity) {}
    private record GroupWithPath(Group group, Path yamlPath) {}
    private record ArtifactWithPath(Artifact artifact, Path yamlPath) {}
}
