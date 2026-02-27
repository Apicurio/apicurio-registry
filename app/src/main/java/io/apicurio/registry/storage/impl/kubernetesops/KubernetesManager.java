package io.apicurio.registry.storage.impl.kubernetesops;

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
import io.apicurio.registry.storage.impl.polling.DataFile;
import io.apicurio.registry.storage.impl.polling.DataSourceManager;
import io.apicurio.registry.storage.impl.polling.PollResult;
import io.apicurio.registry.storage.impl.polling.ProcessingResult;
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
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.apache.commons.io.FilenameUtils.concat;

@ApplicationScoped
public class KubernetesManager implements DataSourceManager {

    @Inject
    Logger log;

    @Inject
    KubernetesOpsConfigProperties config;

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    RegistryStorageContentUtils utils;

    private String previousResourceVersion = "";

    private Watch configMapWatch;
    private volatile boolean watchActive = false;
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private Runnable refreshCallback;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kubernetesops-watch-reconnect");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void start() throws Exception {
        log.info("Initializing KubernetesOps manager with registry ID: {}", config.getRegistryId());
        log.info("Watching namespace: {} for ConfigMaps with label {}={}",
                config.getEffectiveNamespace(), config.getRegistryIdLabel(), config.getRegistryId());
    }

    @Override
    public PollResult poll() throws Exception {
        String namespace = config.getEffectiveNamespace();
        String labelSelector = config.getRegistryIdLabel() + "=" + config.getRegistryId();

        var configMapList = kubernetesClient.configMaps()
                .inNamespace(namespace)
                .withLabelSelector(labelSelector)
                .list();

        String currentResourceVersion = configMapList.getMetadata().getResourceVersion();

        if (currentResourceVersion.equals(previousResourceVersion)) {
            return PollResult.noChanges(currentResourceVersion);
        }

        log.debug("Detected change in ConfigMaps: resourceVersion {} -> {}",
                previousResourceVersion, currentResourceVersion);

        List<DataFile> files = new ArrayList<>();
        ProcessingState tempState = new ProcessingState(null);

        for (ConfigMap configMap : configMapList.getItems()) {
            Map<String, String> data = configMap.getData();

            if (data != null) {
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    String dataKey = entry.getKey();
                    String content = entry.getValue();

                    // dataKey should be a relative path (e.g., "test/artifact.yaml")
                    ConfigMapDataFile file = ConfigMapDataFile.create(tempState, dataKey, content);
                    files.add(file);
                }
            }
        }

        log.debug("Found {} data files across {} ConfigMaps",
                files.size(), configMapList.getItems().size());

        return PollResult.withChanges(currentResourceVersion, files);
    }

    @Override
    public void commitChange(Object marker) {
        if (marker instanceof String) {
            previousResourceVersion = (String) marker;
        }
    }

    @Override
    public Object getPreviousMarker() {
        return previousResourceVersion;
    }

    @Override
    public ProcessingResult process(RegistryStorage storage, PollResult pollResult) throws Exception {
        ProcessingState state = new ProcessingState(storage);

        state.setMarker(pollResult.getMarker());
        state.setCommitTime(Instant.now().getEpochSecond());

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
            if (config.getRegistryId().equals(registry.getId())) {
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
            log.warn("ConfigMaps do not contain data for this registry (ID = {})", config.getRegistryId());
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
                            if (ContentTypeUtil.isParsableYaml(data)) {
                                data = ContentTypeUtil.yamlToJson(data);
                            }
                            try {
                                String contentType = ContentTypes.APPLICATION_JSON;
                                if (dataFile.getPath().toLowerCase().endsWith(".yaml")
                                        || dataFile.getPath().toLowerCase().endsWith(".yml")) {
                                    contentType = ContentTypes.APPLICATION_YAML;
                                } else if (dataFile.getPath().toLowerCase().endsWith(".xml")
                                        || dataFile.getPath().toLowerCase().endsWith(".wsdl")
                                        || dataFile.getPath().toLowerCase().endsWith(".xsd")) {
                                    contentType = ContentTypes.APPLICATION_XML;
                                } else if (dataFile.getPath().toLowerCase().endsWith(".proto")) {
                                    contentType = ContentTypes.APPLICATION_PROTOBUF;
                                } else if (dataFile.getPath().toLowerCase().endsWith(".graphql")) {
                                    contentType = ContentTypes.APPLICATION_GRAPHQL;
                                }
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
                                if (contentFile.getPath().toLowerCase().endsWith(".yaml")
                                        || contentFile.getPath().toLowerCase().endsWith(".yml")) {
                                    e.contentType = ContentTypes.APPLICATION_YAML;
                                } else if (contentFile.getPath().toLowerCase().endsWith(".xml")
                                        || contentFile.getPath().toLowerCase().endsWith(".wsdl")
                                        || contentFile.getPath().toLowerCase().endsWith(".xsd")) {
                                    e.contentType = ContentTypes.APPLICATION_XML;
                                } else if (contentFile.getPath().toLowerCase().endsWith(".proto")) {
                                    e.contentType = ContentTypes.APPLICATION_PROTOBUF;
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
     * Sets the callback to be invoked when watch events are received.
     */
    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }

    /**
     * Starts watching ConfigMaps for changes using the Kubernetes Watch API.
     */
    public void startWatch() {
        if (!config.isWatchEnabled()) {
            log.info("Watch disabled, using polling only");
            return;
        }

        try {
            String namespace = config.getEffectiveNamespace();
            String labelSelector = config.getRegistryIdLabel() + "=" + config.getRegistryId();

            log.info("Starting watch for ConfigMaps in namespace {} with selector {}",
                    namespace, labelSelector);

            configMapWatch = kubernetesClient.configMaps()
                    .inNamespace(namespace)
                    .withLabelSelector(labelSelector)
                    .watch(new Watcher<ConfigMap>() {
                        @Override
                        public void eventReceived(Action action, ConfigMap configMap) {
                            log.debug("ConfigMap event: {} on {}", action,
                                    configMap.getMetadata().getName());

                            if (action == Action.ADDED || action == Action.MODIFIED ||
                                    action == Action.DELETED) {
                                if (refreshCallback != null) {
                                    refreshCallback.run();
                                }
                            }
                        }

                        @Override
                        public void onClose(WatcherException e) {
                            watchActive = false;
                            if (e != null) {
                                log.warn("Watch closed with error: {}", e.getMessage());
                                scheduleReconnect();
                            } else {
                                log.info("Watch closed normally");
                            }
                        }
                    });

            watchActive = true;
            reconnectAttempts.set(0);
            log.info("Watch started successfully for ConfigMaps in namespace {}", namespace);

        } catch (Exception e) {
            log.error("Failed to start watch: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        int attempts = reconnectAttempts.incrementAndGet();
        long delayMs = calculateBackoff(attempts, config.getWatchReconnectDelay());

        log.info("Scheduling watch reconnect in {}ms (attempt {})", delayMs, attempts);

        scheduler.schedule(this::startWatch, delayMs, TimeUnit.MILLISECONDS);
    }

    private long calculateBackoff(int attempts, java.time.Duration baseDelay) {
        long baseMs = baseDelay.toMillis();
        long delayMs = (long) (baseMs * Math.pow(2, Math.min(attempts - 1, 5)));
        return Math.min(delayMs, 300_000); // Max 5 minutes
    }

    /**
     * Stops the active watch if running.
     */
    public void stopWatch() {
        if (configMapWatch != null) {
            log.info("Stopping ConfigMap watch");
            configMapWatch.close();
            configMapWatch = null;
            watchActive = false;
        }
        scheduler.shutdown();
    }

    /**
     * Returns whether the watch is currently active.
     */
    public boolean isWatchActive() {
        return watchActive;
    }
}
