package io.apicurio.registry.storage.impl.polling;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.polling.model.Type;
import io.apicurio.registry.storage.impl.polling.model.v0.Artifact;
import io.apicurio.registry.storage.impl.polling.model.v0.ConfigurationProperty;
import io.apicurio.registry.storage.impl.polling.model.v0.Content;
import io.apicurio.registry.storage.impl.polling.model.v0.Group;
import io.apicurio.registry.storage.impl.polling.model.v0.Registry;
import io.apicurio.registry.storage.impl.polling.model.v0.Rule;
import io.apicurio.registry.storage.impl.polling.model.v0.Version;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Abstract base class for {@link PollingDataSourceManager} implementations that share the
 * same file processing logic (settings, global rules, artifacts, groups, content).
 * Subclasses only need to implement data-source-specific methods (start, poll,
 * commitChange, getPreviousMarker) and provide the registry ID and commit time.
 */
public abstract class AbstractPollingDataSourceManager<MARKER extends SourceMarker> implements PollingDataSourceManager<MARKER> {

    @Inject
    Logger log;

    @Inject
    RegistryStorageContentUtils utils;

    @Inject
    RulesService rulesService;

    private PollingStorageConfig pollingConfig;

    /**
     * This method MUST be called from the subclass.
     */
    public void start(PollingStorageConfig config) {
        pollingConfig = config;
    }

    @Override
    public PollingProcessingResult process(RegistryStorage storage, PollingResult<MARKER> pollResult) throws Exception {
        ProcessingState state = new ProcessingState(pollingConfig, storage);

        state.setCommitTime(pollResult.getMarker().getCommitTime());

        for (PollingDataFile file : pollResult.getFiles()) {
            state.index(file);
        }

        log.debug("Processing {} files", state.getPathIndex().size());
        processFiles(state);

        // Validate loaded data against configured rules (before blue-green swap)
        if (state.isSuccessful()) {
            var validator = new PollingDataValidator(rulesService);
            validator.validate(state, storage, pollingConfig);
        }

        // Report unprocessed files - distinguish between expected (content/data files) and unexpected
        var unprocessedRegistryFiles = state.getPathIndex().values().stream()
                .filter(f -> !f.isProcessed() && pollingConfig.isMetadataFile(f.getPath()))
                .map(PollingDataFile::getPath)
                .collect(Collectors.toList());
        var unprocessedOtherFiles = state.getPathIndex().values().stream()
                .filter(f -> !f.isProcessed() && !pollingConfig.isMetadataFile(f.getPath()))
                .map(PollingDataFile::getPath)
                .collect(Collectors.toList());

        if (!unprocessedRegistryFiles.isEmpty()) {
            log.warn("{} registry metadata file(s) were not processed: {}",
                    unprocessedRegistryFiles.size(), unprocessedRegistryFiles);
        }
        if (!unprocessedOtherFiles.isEmpty()) {
            log.debug("{} non-registry file(s) were not processed (expected for content/data files): {}",
                    unprocessedOtherFiles.size(), unprocessedOtherFiles);
        }

        if (state.isSuccessful()) {
            log.info("Processing complete: {} groups, {} artifacts, {} versions loaded",
                    state.getGroupCount(), state.getArtifactCount(), state.getVersionCount());
        } else {
            log.error("Processing failed with {} error(s)", state.getErrors().size());
        }
        return state.getResult();
    }

    /**
     * Checks whether an entity with a registryIds list should be loaded by this registry instance.
     * If registryIds is null or empty, the entity is loaded by any registry (simple setups).
     */
    private boolean matchesRegistryId(List<String> registryIds) {
        return registryIds == null || registryIds.isEmpty() || registryIds.contains(pollingConfig.getRegistryId());
    }

    private void processFiles(ProcessingState state) {
        // Find the Registry config matching our registry ID
        for (PollingDataFile file : state.fromTypeIndex(Type.REGISTRY)) {
            Registry registry = file.getEntityUnchecked();
            if (pollingConfig.getRegistryId().equals(registry.getRegistryId())) {
                state.setCurrentRegistry(registry);
                file.setProcessed(true);
            }
        }

        if (state.getCurrentRegistry() != null) {
            processConfigurationProperties(state);
            processGlobalRules(state);

            for (PollingDataFile file : state.fromTypeIndex(Type.ARTIFACT)) {
                Artifact artifact = file.getEntityUnchecked();

                if (!matchesRegistryId(artifact.getRegistryIds())) {
                    log.debug("Ignoring artifact {} (registryIds {} does not include {})",
                            artifact.getArtifactId(), artifact.getRegistryIds(), pollingConfig.getRegistryId());
                    continue;
                }

                if (state.checkArtifactConflict(artifact.getGroupId(), artifact.getArtifactId(), file.getSourceId())) {
                    state.recordError(file, "Artifact '%s:%s' is defined in multiple sources: '%s' and '%s'. "
                            + "Each artifact must be defined in exactly one source.",
                            artifact.getGroupId(), artifact.getArtifactId(),
                            state.getArtifactSource(artifact.getGroupId(), artifact.getArtifactId()),
                            file.getSourceId());
                    continue;
                }

                processArtifact(state, file, artifact);
            }
        } else {
            log.debug("No registry config found for ID '{}'. requireRegistryConfig={}",
                    pollingConfig.getRegistryId(), pollingConfig.isRequireRegistryConfig());
            if (pollingConfig.isRequireRegistryConfig()) {
                state.recordError("Data source does not contain a registry configuration matching ID '%s'. "
                        + "To allow loading without a registry config, set "
                        + "apicurio.polling-storage.require-registry-config=false.",
                        pollingConfig.getRegistryId());
            } else {
                log.warn("Data source does not contain data for this registry (ID = {})",
                        pollingConfig.getRegistryId());
            }
        }
    }

    private void processConfigurationProperties(ProcessingState state) {
        var properties = state.getCurrentRegistry().getProperties();
        if (properties != null) {
            for (ConfigurationProperty property : properties) {
                try {
                    var dto = new DynamicConfigPropertyDto();
                    dto.setName(property.getName());
                    dto.setValue(property.getValue());
                    log.trace("Importing {}",dto);
                    state.getStorage().setConfigProperty(dto);
                } catch (Exception ex) {
                    state.recordError("Could not import configuration property %s: %s", property.getName(),
                            ex.getMessage());
                }
            }
        }
    }

    private void processGlobalRules(ProcessingState state) {
        var globalRules = state.getCurrentRegistry().getGlobalRules();
        if (globalRules != null) {
            for (Rule globalRule : globalRules) {
                try {
                    var e = new GlobalRuleEntity();
                    e.ruleType = RuleType.fromValue(globalRule.getRuleType());
                    e.configuration = globalRule.getConfig();
                    log.trace("Importing {}",e);
                    state.getStorage().importGlobalRule(e);
                } catch (Exception ex) {
                    state.recordError("Could not import global rule %s: %s", globalRule.getRuleType(),
                            ex.getMessage());
                }
            }
        }
    }

    private void processArtifact(ProcessingState state, PollingDataFile artifactFile, Artifact artifact) {
        boolean artifactImported = false;

        var group = processGroupRef(state, artifact.getGroupId());
        if (group != null) {
            List<Version> versions = artifact.getVersions();
            if (versions == null || versions.isEmpty()) {
                log.debug("Artifact {} has no versions, skipping", artifact.getArtifactId());
                artifactFile.setProcessed(true);
                return;
            }

            for (int i = 0; i < versions.size(); i++) {
                Version version = versions.get(i);
                try {
                    // Load content: supports direct path (content) and optional metadata (contentMetadata)
                    Long contentId = processVersionContent(state, artifactFile, version,
                            artifact.getArtifactType());
                    if (contentId == null) {
                        state.recordError(artifactFile, "Could not import content for artifact version %s.",
                                artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + version.getVersion());
                        continue;
                    }

                    if (!artifactImported) {
                        ArtifactEntity artifactEntity = new ArtifactEntity();
                        artifactEntity.groupId = artifact.getGroupId();
                        artifactEntity.artifactId = artifact.getArtifactId();
                        artifactEntity.artifactType = artifact.getArtifactType();
                        artifactEntity.name = artifact.getName();
                        artifactEntity.description = artifact.getDescription();
                        artifactEntity.labels = withSourceLabel(artifact.getLabels(), artifactFile.getSourceId());
                        artifactEntity.owner = artifact.getOwner();
                        artifactEntity.createdOn = TimestampParser.parse(artifact.getCreatedOn(), state.getCommitTime());
                        artifactEntity.modifiedOn = TimestampParser.parse(artifact.getModifiedOn(), state.getCommitTime());
                        state.getStorage().importArtifact(artifactEntity);
                        state.incrementArtifactCount();
                        artifactImported = true;
                    }

                    var e = new ArtifactVersionEntity();
                    e.groupId = artifact.getGroupId();
                    e.artifactId = artifact.getArtifactId();
                    e.version = version.getVersion();
                    e.versionOrder = i + 1;
                    if (version.getGlobalId() != null) {
                        e.globalId = version.getGlobalId();
                    } else if (pollingConfig.isDeterministicIdGenerationEnabled()) {
                        e.globalId = DeterministicIdGenerator.globalId(
                                artifact.getGroupId(), artifact.getArtifactId(), version.getVersion());
                    } else {
                        state.recordError(artifactFile, "globalId is required for version %s (deterministic ID generation is disabled)",
                                artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + version.getVersion());
                        continue;
                    }
                    e.state = version.getState() != null
                            ? VersionState.fromValue(version.getState())
                            : VersionState.ENABLED;
                    e.name = version.getName();
                    e.description = version.getDescription();
                    e.labels = version.getLabels();
                    e.owner = version.getOwner();
                    e.contentId = contentId;
                    e.createdOn = TimestampParser.parse(version.getCreatedOn(), state.getCommitTime());
                    e.modifiedOn = TimestampParser.parse(version.getModifiedOn(), state.getCommitTime());

                    log.trace("Importing {}",e);
                    state.getStorage().importArtifactVersion(e);
                    state.incrementVersionCount();
                } catch (Exception ex) {
                    state.recordError(artifactFile, "Could not import artifact version '%s': %s",
                            artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + version.getVersion(),
                            ex.getMessage());
                }
            }
            processArtifactRules(state, artifact);
            artifactFile.setProcessed(true);
        }
        // Note: if group is null, processGroupRef() already recorded the error
    }

    private void processArtifactRules(ProcessingState state, Artifact artifact) {
        var rules = artifact.getRules();
        if (rules != null) {
            for (Rule rule : rules) {
                try {
                    var e = new ArtifactRuleEntity();
                    e.groupId = artifact.getGroupId();
                    e.artifactId = artifact.getArtifactId();
                    e.type = RuleType.fromValue(rule.getRuleType());
                    e.configuration = rule.getConfig();
                    log.trace("Importing {}",e);
                    state.getStorage().importArtifactRule(e);
                } catch (Exception ex) {
                    state.recordError("Could not import rule %s for artifact '%s': %s", rule.getRuleType(),
                            artifact.getGroupId() + ":" + artifact.getArtifactId(), ex.getMessage());
                }
            }
        }
    }

    private void processGroupRules(ProcessingState state, Group group) {
        var rules = group.getRules();
        if (rules != null) {
            for (Rule rule : rules) {
                try {
                    var e = new GroupRuleEntity();
                    e.groupId = group.getGroupId();
                    e.type = RuleType.fromValue(rule.getRuleType());
                    e.configuration = rule.getConfig();
                    log.trace("Importing {}",e);
                    state.getStorage().importGroupRule(e);
                } catch (Exception ex) {
                    state.recordError("Could not import rule %s for group '%s': %s", rule.getRuleType(),
                            group.getGroupId(), ex.getMessage());
                }
            }
        }
    }

    private Group processGroupRef(ProcessingState state, String groupName) {
        var groupFiles = state.fromTypeIndex(Type.GROUP).stream().filter(f -> {
            Group group = f.getEntityUnchecked();
            return matchesRegistryId(group.getRegistryIds()) && groupName.equals(group.getGroupId());
        }).collect(Collectors.toList());

        if (groupFiles.isEmpty()) {
            state.recordError("Could not find group with ID %s for registry %s", groupName,
                    pollingConfig.getRegistryId());
            return null;
        } else if (groupFiles.size() > 1) {
            state.recordError("Multiple groups with ID %s found for registry %s: %s", groupName,
                    pollingConfig.getRegistryId(), groupFiles);
            return null;
        } else {
            var groupFile = groupFiles.get(0);
            Group group = groupFile.getEntityUnchecked();
            if (groupFile.isProcessed()) {
                return group;
            }
            try {
                var e = new GroupEntity();
                e.groupId = group.getGroupId();
                e.description = group.getDescription();
                e.labels = withSourceLabel(group.getLabels(), groupFile.getSourceId());
                e.owner = group.getOwner();
                e.artifactsType = group.getArtifactsType();
                e.createdOn = TimestampParser.parse(group.getCreatedOn(), state.getCommitTime());
                e.modifiedOn = TimestampParser.parse(group.getModifiedOn(), state.getCommitTime());
                log.trace("Importing {}",e);
                state.getStorage().importGroup(e);
                processGroupRules(state, group);
                state.incrementGroupCount();
                groupFile.setProcessed(true);
                return group;
            } catch (Exception ex) {
                state.recordError(groupFile, "Could not import group '%s': %s", group.getGroupId(), ex.getMessage());
                return null;
            }
        }
    }

    /**
     * Processes content for a version. Supports two modes:
     * <ul>
     * <li>Mode A (simple): Version has only {@code content} field pointing to a raw content file.
     *     contentId is generated from a hash-based sequence.</li>
     * <li>Mode B (with metadata): Version has {@code contentMetadata} field pointing to a Content entity
     *     (*.registry.yaml with $type: content-v0) which specifies the content file path, optional explicit
     *     contentId, and optional references.</li>
     * </ul>
     *
     * @return the contentId, or null on failure
     */
    private Long processVersionContent(ProcessingState state, PollingDataFile artifactFile, Version version,
                                       String artifactType) {
        String contentPath = version.getContent();
        String contentMetadataPath = version.getContentMetadata();

        if (contentPath == null || contentPath.isBlank()) {
            state.recordError(artifactFile, "Version has no content path specified");
            return null;
        }

        // If contentMetadata is specified, load the Content entity for references and explicit contentId
        Content contentMetadata = null;
        if (contentMetadataPath != null && !contentMetadataPath.isBlank()) {
            var metadataFile = findFileByPathRef(state, artifactFile, contentMetadataPath);
            if (metadataFile == null) {
                state.recordError(artifactFile, "Could not find content metadata file at path %s",
                        Path.of(artifactFile.getPath()).resolveSibling(contentMetadataPath).normalize());
                return null;
            }
            if (!metadataFile.isType(Type.CONTENT)) {
                state.recordError(metadataFile, "Not a valid content metadata definition (expected $type: content-v0)");
                return null;
            }
            contentMetadata = metadataFile.getEntityUnchecked();
            metadataFile.setProcessed(true);
        }

        // Load the actual content file
        var dataFile = findFileByPathRef(state, artifactFile, contentPath);
        if (dataFile == null) {
            state.recordError(artifactFile, "Could not find content file at path %s",
                    Path.of(artifactFile.getPath()).resolveSibling(contentPath).normalize());
            return null;
        }

        var data = dataFile.getData();
        if (data == null) {
            state.recordError(dataFile, "Content data is null");
            return null;
        }

        try {
            String contentType = detectContentType(dataFile.getPath(), ContentTypes.APPLICATION_JSON);

            // Only convert YAML to JSON for file types that should be stored as JSON
            // (e.g., .avsc, .json, or files with no YAML extension).
            // Preserve YAML content as-is for .yaml/.yml files to avoid lossy conversion.
            if (!ContentTypes.APPLICATION_YAML.equals(contentType)
                    && ContentTypeUtil.isParsableYaml(data)) {
                data = ContentTypeUtil.yamlToJson(data);
            }
            var typedContent = TypedContent.create(data, contentType);

            // Determine artifact type from content if not specified
            String resolvedArtifactType = utils.determineArtifactType(typedContent, artifactType);

            // Calculate content hash for deduplication
            String contentHash = utils.getContentHash(typedContent, null);

            // Check if this content was already imported (deduplication)
            Long existingContentId = state.getContentHashToId().get(contentHash);
            if (existingContentId != null) {
                dataFile.setProcessed(true);
                return existingContentId;
            }

            // Determine contentId: explicit from metadata > deterministic from hash > error
            long contentId;
            if (contentMetadata != null && contentMetadata.getContentId() != null) {
                contentId = contentMetadata.getContentId();
            } else if (pollingConfig.isDeterministicIdGenerationEnabled()) {
                contentId = DeterministicIdGenerator.contentId(data);
            } else {
                state.recordError(dataFile, "contentId is required (deterministic ID generation is disabled)");
                return null;
            }

            var e = new ContentEntity();
            e.contentId = contentId;
            e.contentHash = contentHash;
            e.contentBytes = data.bytes();
            e.canonicalHash = utils.getCanonicalContentHash(typedContent, resolvedArtifactType, null, null);
            e.artifactType = resolvedArtifactType;
            e.contentType = contentType;

            if (contentMetadata != null && contentMetadata.getReferences() != null
                    && !contentMetadata.getReferences().isEmpty()) {
                List<ArtifactReferenceDto> refs = contentMetadata.getReferences().stream()
                        .map(ref -> ArtifactReferenceDto.builder()
                                .groupId(ref.getGroupId())
                                .artifactId(ref.getArtifactId())
                                .version(ref.getVersion())
                                .name(ref.getName())
                                .build())
                        .collect(Collectors.toList());
                e.serializedReferences = RegistryContentUtils.serializeReferences(refs);
            }

            log.trace("Importing content from {}",dataFile.getPath());
            state.getStorage().importContent(e);
            state.getContentHashToId().put(contentHash, contentId);
            dataFile.setProcessed(true);
            return contentId;
        } catch (Exception ex) {
            state.recordError(dataFile, "Could not import content: %s", ex.getMessage());
            return null;
        }
    }

    private PollingDataFile findFileByPathRef(ProcessingState state, PollingDataFile base, String ref) {
        String resolved = Path.of(base.getPath()).resolveSibling(ref).normalize().toString();
        return state.getPathIndex().get(base.getSourceId() + ":" + resolved);
    }

    /**
     * Detects content type based on file extension.
     *
     * @param path        the file path
     * @param defaultType the default content type if no extension matches, or null
     * @return the detected content type, or defaultType if no match
     */
    protected static String detectContentType(String path, String defaultType) {
        String lowerPath = path.toLowerCase();
        if (lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")) {
            return ContentTypes.APPLICATION_YAML;
        } else if (lowerPath.endsWith(".xml") || lowerPath.endsWith(".wsdl")
                || lowerPath.endsWith(".xsd")) {
            return ContentTypes.APPLICATION_XML;
        } else if (lowerPath.endsWith(".proto")) {
            return ContentTypes.APPLICATION_PROTOBUF;
        } else if (lowerPath.endsWith(".graphql")) {
            return ContentTypes.APPLICATION_GRAPHQL;
        }
        return defaultType;
    }

    /**
     * Adds the source label to a labels map if the source-label-key is configured.
     * Returns the original map (or a new one) with the label added.
     */
    private Map<String, String> withSourceLabel(Map<String, String> labels, String sourceId) {
        String key = pollingConfig.getSourceLabelKey();
        if (key == null || key.isEmpty()) {
            return labels;
        }
        var result = labels != null ? new LinkedHashMap<>(labels) : new LinkedHashMap<String, String>();
        result.put(key, sourceId);
        return result;
    }
}
