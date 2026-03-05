package io.apicurio.registry.storage.impl.polling;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.gitops.ProcessingState;
import io.apicurio.registry.storage.impl.gitops.model.Type;
import io.apicurio.registry.storage.impl.gitops.model.v0.Artifact;
import io.apicurio.registry.storage.impl.gitops.model.v0.Content;
import io.apicurio.registry.storage.impl.gitops.model.v0.Group;
import io.apicurio.registry.storage.impl.gitops.model.v0.Registry;
import io.apicurio.registry.storage.impl.gitops.model.v0.Rule;
import io.apicurio.registry.storage.impl.gitops.model.v0.Setting;
import io.apicurio.registry.storage.impl.gitops.model.v0.Version;
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
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.io.FilenameUtils.concat;

/**
 * Abstract base class for {@link DataSourceManager} implementations that share the
 * same file processing logic (settings, global rules, artifacts, groups, content).
 * Subclasses only need to implement data-source-specific methods (start, poll,
 * commitChange, getPreviousMarker) and provide the registry ID and commit time.
 */
public abstract class AbstractDataSourceManager implements DataSourceManager {

    @Inject
    Logger log;

    @Inject
    RegistryStorageContentUtils utils;

    /**
     * Returns the registry ID used to filter data belonging to this instance.
     */
    protected abstract String getRegistryId();

    /**
     * Returns the commit/change time from the poll result marker.
     *
     * @param marker the marker from the PollResult
     * @return epoch seconds representing the change time
     */
    protected abstract long getCommitTime(Object marker);

    @Override
    public ProcessingResult process(RegistryStorage storage, PollResult pollResult) throws Exception {
        ProcessingState state = new ProcessingState(storage);

        state.setMarker(pollResult.getMarker());
        state.setCommitTime(getCommitTime(pollResult.getMarker()));

        for (DataFile file : pollResult.getFiles()) {
            state.index(file);
        }

        log.debug("Processing {} files", state.getPathIndex().size());
        processFiles(state);

        var unprocessed = state.getPathIndex().values().stream()
                .filter(f -> !f.isProcessed())
                .map(DataFile::getPath)
                .collect(Collectors.toList());

        log.debug("The following {} file(s) were not processed: {}", unprocessed.size(), unprocessed);

        if (state.isSuccessful()) {
            return ProcessingResult.success();
        } else {
            return ProcessingResult.failure(state.getErrors());
        }
    }

    private void processFiles(ProcessingState state) {
        for (DataFile file : state.fromTypeIndex(Type.REGISTRY)) {
            Registry registry = file.getEntityUnchecked();
            if (getRegistryId().equals(registry.getId())) {
                state.setCurrentRegistry(registry);
                file.setProcessed(true);
            }
        }

        if (state.getCurrentRegistry() != null) {
            processSettings(state);
            processGlobalRules(state);

            for (DataFile file : state.fromTypeIndex(Type.ARTIFACT)) {
                Artifact artifact = file.getEntityUnchecked();

                if (state.isCurrentRegistryId(artifact.getRegistryId())) {
                    processArtifact(state, file, artifact);
                } else {
                    log.debug("Ignoring {}", artifact);
                }
            }
        } else {
            log.warn("Data source does not contain data for this registry (ID = {})", getRegistryId());
        }
    }

    private void processSettings(ProcessingState state) {
        var settings = state.getCurrentRegistry().getSettings();
        if (settings != null) {
            for (Setting setting : settings) {
                try {
                    var dto = new DynamicConfigPropertyDto();
                    dto.setName(setting.getName());
                    dto.setValue(setting.getValue());
                    log.debug("Importing {}", dto);
                    state.getStorage().setConfigProperty(dto);
                } catch (Exception ex) {
                    state.recordError("Could not import configuration property %s: %s", setting.getName(),
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
                    e.ruleType = RuleType.fromValue(globalRule.getType());
                    e.configuration = globalRule.getConfig();
                    log.debug("Importing {}", e);
                    state.getStorage().importGlobalRule(e);
                } catch (Exception ex) {
                    state.recordError("Could not import global rule %s: %s", globalRule.getType(),
                            ex.getMessage());
                }
            }
        }
    }

    private void processArtifact(ProcessingState state, DataFile artifactFile, Artifact artifact) {
        boolean artifactImported = false;
        String artifactType;

        var group = processGroupRef(state, artifact.getGroupId());
        if (group != null) {
            List<Version> versions = artifact.getVersions();
            for (int i = 0; i < versions.size(); i++) {
                Version version = versions.get(i);
                try {
                    var e = new ArtifactVersionEntity();
                    e.groupId = artifact.getGroupId();
                    e.artifactId = artifact.getId();
                    e.version = version.getId();
                    e.globalId = version.getGlobalId();
                    e.state = VersionState.ENABLED;
                    e.createdOn = state.getCommitTime();
                    e.modifiedOn = state.getCommitTime();

                    var content = processContent(state, artifactFile, version.getContentFile());
                    if (content != null) {
                        artifactType = content.getArtifactType();
                        e.contentId = content.getId();

                        if (!artifactImported) {
                            ArtifactEntity artifactEntity = new ArtifactEntity();
                            artifactEntity.groupId = artifact.getGroupId();
                            artifactEntity.artifactId = artifact.getId();
                            artifactEntity.artifactType = artifactType;
                            artifactEntity.createdOn = state.getCommitTime();
                            artifactEntity.modifiedOn = state.getCommitTime();
                            state.getStorage().importArtifact(artifactEntity);
                            artifactImported = true;
                        }

                        log.debug("Importing {}", e);
                        state.getStorage().importArtifactVersion(e);
                    } else {
                        state.recordError("Could not import content for artifact version %s.",
                                artifact.getGroupId() + ":" + artifact.getId() + ":" + version.getId());
                    }
                } catch (Exception ex) {
                    state.recordError("Could not import artifact version '%s': %s",
                            artifact.getGroupId() + ":" + artifact.getId() + ":" + version.getId(),
                            ex.getMessage());
                }
            }
            processArtifactRules(state, artifact);
            artifactFile.setProcessed(true);
        } else {
            state.recordError("Could not find group %s", artifact.getGroupId());
        }
    }

    private void processArtifactRules(ProcessingState state, Artifact artifact) {
        var rules = artifact.getRules();
        if (rules != null) {
            for (Rule rule : rules) {
                try {
                    var e = new ArtifactRuleEntity();
                    e.groupId = artifact.getGroupId();
                    e.artifactId = artifact.getId();
                    e.type = RuleType.fromValue(rule.getType());
                    e.configuration = rule.getConfig();
                    log.debug("Importing {}", e);
                    state.getStorage().importArtifactRule(e);
                } catch (Exception ex) {
                    state.recordError("Could not import rule %s for artifact '%s': %s", rule.getType(),
                            artifact.getGroupId() + ":" + artifact.getId(), ex.getMessage());
                }
            }
        }
    }

    private Group processGroupRef(ProcessingState state, String groupName) {
        var groupFiles = state.fromTypeIndex(Type.GROUP).stream().filter(f -> {
            Group group = f.getEntityUnchecked();
            return state.isCurrentRegistryId(group.getRegistryId()) && groupName.equals(group.getId());
        }).collect(Collectors.toList());

        if (groupFiles.isEmpty()) {
            state.recordError("Could not find group with ID %s in registry %s", groupName,
                    state.getCurrentRegistry().getId());
            return null;
        } else if (groupFiles.size() > 1) {
            state.recordError("Multiple groups with ID %s found in registry %s: %s", groupName,
                    state.getCurrentRegistry().getId(), groupFiles);
            return null;
        } else {
            var groupFile = groupFiles.get(0);
            Group group = groupFile.getEntityUnchecked();
            if (groupFile.isProcessed()) {
                return group;
            }
            try {
                var e = new GroupEntity();
                e.groupId = group.getId();
                log.debug("Importing {}", e);
                state.getStorage().importGroup(e);
                groupFile.setProcessed(true);
                return group;
            } catch (Exception ex) {
                state.recordError("Could not import group %s: %s", group.getId(), ex.getMessage());
                return null;
            }
        }
    }

    private Content processContent(ProcessingState state, DataFile base, String contentRef) {
        var contentFile = findFileByPathRef(state, base, contentRef);
        if (contentFile != null) {
            if (contentFile.isType(Type.CONTENT)) {
                Content content = contentFile.getEntityUnchecked();
                if (state.isCurrentRegistryId(content.getRegistryId())) {
                    if (!contentFile.isProcessed()) {
                        var dataFile = findFileByPathRef(state, contentFile, content.getDataFile());
                        if (dataFile != null) {
                            var data = dataFile.getData();
                            if (data == null) {
                                state.recordError("Content data is null for file %s", dataFile.getPath());
                                return null;
                            }
                            if (ContentTypeUtil.isParsableYaml(data)) {
                                data = ContentTypeUtil.yamlToJson(data);
                            }
                            try {
                                String contentType = detectContentType(dataFile.getPath(),
                                        ContentTypes.APPLICATION_JSON);
                                var typedContent = TypedContent.create(data, contentType);

                                var e = new ContentEntity();
                                e.contentId = content.getId();
                                e.contentHash = content.getContentHash();
                                e.contentBytes = data.bytes();
                                content.setArtifactType(
                                        utils.determineArtifactType(typedContent, content.getArtifactType()));
                                e.canonicalHash = utils.getCanonicalContentHash(typedContent,
                                        content.getArtifactType(), null, null);
                                e.artifactType = content.getArtifactType();
                                e.contentType = contentType;
                                String contentFileType = detectContentType(contentFile.getPath(), null);
                                if (contentFileType != null) {
                                    e.contentType = contentFileType;
                                }
                                log.debug("Importing {}", e);
                                state.getStorage().importContent(e);
                                contentFile.setProcessed(true);
                                dataFile.setProcessed(true);
                                return content;
                            } catch (Exception ex) {
                                state.recordError("Could not import content %s: %s", contentFile.getPath(),
                                        ex.getMessage());
                                return null;
                            }
                        } else {
                            state.recordError("Could not find content data file at path %s referenced by %s",
                                    concat(concat(contentFile.getPath(), ".."), content.getDataFile()),
                                    contentFile.getPath());
                            return null;
                        }
                    } else {
                        return content;
                    }
                } else {
                    state.recordError("Content file %s does not belong to this registry",
                            contentFile.getPath());
                    return null;
                }
            } else {
                state.recordError("File %s is not a valid content definition", contentFile.getPath());
                return null;
            }
        } else {
            state.recordError("Could not find content file at path %s",
                    concat(concat(base.getPath(), ".."), contentRef));
            return null;
        }
    }

    private DataFile findFileByPathRef(ProcessingState state, DataFile base, String path) {
        path = concat(concat(base.getPath(), ".."), path);
        return state.getPathIndex().get(path);
    }

    /**
     * Detects content type based on file extension.
     *
     * @param path the file path
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
}
