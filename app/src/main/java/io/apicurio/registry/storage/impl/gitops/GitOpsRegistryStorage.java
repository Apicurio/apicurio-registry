package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.DynamicConfigStorage;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.StorageMetricsApply;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.storage.impl.gitops.sql.BlueSqlStorage;
import io.apicurio.registry.storage.impl.gitops.sql.GreenSqlStorage;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.impexp.Entity;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
@StorageMetricsApply
@Logged
public class GitOpsRegistryStorage extends AbstractReadOnlyRegistryStorage {

    @Inject
    Logger log;

    @Inject
    BlueSqlStorage blue;

    @Inject
    GreenSqlStorage green;

    @Inject
    GitManager gitManager;

    @ConfigProperty(name = "apicurio.storage.kind")
    @Info(category = "storage", description = "Application storage variant, for example, sql, kafkasql, or gitops", availableSince = "3.0.0")
    String registryStorageType;

    // Fair lock, so we ensure the writer does not wait indefinitely under high throughput.
    ReentrantReadWriteLock switchLock = new ReentrantReadWriteLock(true);

    RegistryStorage active = null;
    RegistryStorage inactive = null;

    private volatile State state = State.READY_TO_WRITE;

    private enum State {
        READY_TO_SWITCH, // Data has been loaded to the inactive storage, but not yet published
        READY_TO_WRITE, // Latest data has been published, and we are ready to write to the inactive storage
    }

    @Override
    public void initialize() {
        log.info("Using GitOps storage");

        green.initialize();
        blue.initialize();

        try {
            active = green;
            inactive = blue;
            gitManager.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Scheduled(concurrentExecution = SKIP, every = "{apicurio.gitops.refresh.every}")
    void refresh() {
        if (registryStorageType.equals("gitops")) {
            log.debug("Running GitOps refresh. Active database is {} and state is {}.",
                    active == green ? "green" : "blue", state);
            switch (state) {
                case READY_TO_SWITCH: {
                    try {
                        if (switchLock.writeLock().tryLock(5, TimeUnit.SECONDS)) {
                            var previous = active;
                            try {
                                active = inactive;
                                inactive = previous;
                            } finally {
                                state = State.READY_TO_WRITE;
                                switchLock.writeLock().unlock();
                                log.info("GitOps update published");
                            }
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                    break;
                case READY_TO_WRITE: {
                    try {
                        var updatedCommit = gitManager.poll();
                        if (!updatedCommit.equals(gitManager.getPreviousCommit())) {
                            // TODO Delete *all* data
                            // TODO Improve performance by remembering when the storage is clean
                            inactive.deleteAllUserData();

                            var processingState = new ProcessingState(inactive);
                            gitManager.run(processingState, updatedCommit);

                            if (processingState.isSuccessful()) {
                                log.info("GitOps update loaded successfully");
                                gitManager.updateCurrentCommit(updatedCommit);
                                state = State.READY_TO_SWITCH;
                            } else {
                                log.error("GitOps update failed to load");
                                processingState.getErrors().forEach(e -> {
                                    log.error("Error: {}", e);
                                });
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                    break;
            }
            log.debug("GitOps refresh finished. Active database is {} and state is {}.",
                    active == green ? "green" : "blue", state);
        }
    }

    public <T> T proxy(Function<RegistryStorage, T> operation) {
        try {
            if (switchLock.readLock().tryLock(5, TimeUnit.SECONDS)) {
                try {
                    return operation.apply(active);
                } finally {
                    switchLock.readLock().unlock();
                }
            } else {
                throw new RegistryStorageException(
                        "Could not acquire read lock to get the active storage within 5 seconds");
            }
        } catch (InterruptedException ex) {
            throw new RegistryStorageException("Could not acquire read lock to get the active storage", ex);
        }
    }

    public void proxyAction(Consumer<RegistryStorage> action) {
        try {
            if (switchLock.readLock().tryLock(5, TimeUnit.SECONDS)) {
                try {
                    action.accept(active);
                } finally {
                    switchLock.readLock().unlock();
                }
            } else {
                throw new RegistryStorageException(
                        "Could not acquire read lock to get the active storage within 5 seconds");
            }
        } catch (InterruptedException ex) {
            throw new RegistryStorageException("Could not acquire read lock to get the active storage", ex);
        }
    }

    @Override
    public String storageName() {
        return "gitops";
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @PreDestroy
    void onDestroy() {
    }

    @Override
    public ContentWrapperDto getContentById(long contentId) {
        return proxy(storage -> storage.getContentById(contentId));
    }

    @Override
    public ContentWrapperDto getContentByHash(String contentHash) {
        return proxy(storage -> storage.getContentByHash(contentHash));
    }

    @Override
    public List<ArtifactVersionMetaDataDto> getArtifactVersionsByContentId(long contentId) {
        return proxy(storage -> storage.getArtifactVersionsByContentId(contentId));
    }

    @Override
    public List<Long> getEnabledArtifactContentIds(String groupId, String artifactId) {
        return proxy(storage -> storage.getEnabledArtifactContentIds(groupId, artifactId));
    }

    @Override
    public Set<String> getArtifactIds(Integer limit) {
        return proxy(storage -> storage.getArtifactIds(limit));
    }

    @Override
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit) {
        return proxy(storage -> storage.searchArtifacts(filters, orderBy, orderDirection, offset, limit));
    }

    @Override
    public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit) throws RegistryStorageException {
        return proxy(storage -> storage.searchVersions(filters, orderBy, orderDirection, offset, limit));
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId) {
        return proxy(storage -> storage.getArtifactMetaData(groupId, artifactId));
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaDataByContent(String groupId, String artifactId,
            boolean canonical, TypedContent content, List<ArtifactReferenceDto> artifactReferences) {
        return proxy(storage -> storage.getArtifactVersionMetaDataByContent(groupId, artifactId, canonical,
                content, artifactReferences));
    }

    @Override
    public List<RuleType> getArtifactRules(String groupId, String artifactId) {
        return proxy(storage -> storage.getArtifactRules(groupId, artifactId));
    }

    @Override
    public List<RuleType> getGroupRules(String groupId) throws RegistryStorageException {
        return proxy(storage -> storage.getGroupRules(groupId));
    }

    @Override
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule) {
        return proxy(storage -> storage.getArtifactRule(groupId, artifactId, rule));
    }

    @Override
    public RuleConfigurationDto getGroupRule(String groupId, RuleType rule) throws RegistryStorageException {
        return proxy(storage -> storage.getGroupRule(groupId, rule));
    }

    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId) {
        return proxy(storage -> storage.getArtifactVersions(groupId, artifactId));
    }

    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId, Set<VersionState> behavior) {
        return proxy(storage -> storage.getArtifactVersions(groupId, artifactId, behavior));
    }

    @Override
    public StoredArtifactVersionDto getArtifactVersionContent(long globalId) {
        return proxy(storage -> storage.getArtifactVersionContent(globalId));
    }

    @Override
    public StoredArtifactVersionDto getArtifactVersionContent(String groupId, String artifactId,
            String version) {
        return proxy(storage -> storage.getArtifactVersionContent(groupId, artifactId, version));
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId,
            String version) {
        return proxy(storage -> storage.getArtifactVersionMetaData(groupId, artifactId, version));
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(Long globalId)
            throws VersionNotFoundException, RegistryStorageException {
        return proxy(storage -> storage.getArtifactVersionMetaData(globalId));
    }

    @Override
    public List<RuleType> getGlobalRules() {
        return proxy(RegistryStorage::getGlobalRules);
    }

    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule) {
        return proxy(storage -> storage.getGlobalRule(rule));
    }

    @Override
    public List<String> getGroupIds(Integer limit) {
        return proxy(storage -> storage.getGroupIds(limit));
    }

    @Override
    public GroupMetaDataDto getGroupMetaData(String groupId) {
        return proxy(storage -> storage.getGroupMetaData(groupId));
    }

    @Override
    public void exportData(Function<Entity, Void> handler) {
        proxyAction(storage -> storage.exportData(handler));
    }

    @Override
    public long countArtifacts() {
        return proxy(RegistryStorage::countArtifacts);
    }

    @Override
    public long countActiveArtifactVersions(String groupId, String artifactId) {
        return proxy(storage -> storage.countActiveArtifactVersions(groupId, artifactId));
    }

    @Override
    public long countArtifactVersions(String groupId, String artifactId) {
        return proxy(storage -> storage.countArtifactVersions(groupId, artifactId));
    }

    @Override
    public long countTotalArtifactVersions() {
        return proxy(RegistryStorage::countTotalArtifactVersions);
    }

    @Override
    public List<RoleMappingDto> getRoleMappings() {
        return proxy(RegistryStorage::getRoleMappings);
    }

    @Override
    public RoleMappingSearchResultsDto searchRoleMappings(int offset, int limit)
            throws RegistryStorageException {
        return proxy(storage -> storage.searchRoleMappings(offset, limit));
    }

    @Override
    public RoleMappingDto getRoleMapping(String principalId) {
        return proxy(storage -> storage.getRoleMapping(principalId));
    }

    @Override
    public String getRoleForPrincipal(String principalId) {
        return proxy(storage -> storage.getRoleForPrincipal(principalId));
    }

    @Override
    public DynamicConfigPropertyDto getRawConfigProperty(String propertyName) {
        return proxy(storage -> storage.getRawConfigProperty(propertyName));
    }

    @Override
    public List<DynamicConfigPropertyDto> getStaleConfigProperties(Instant since) {
        return proxy(storage -> storage.getStaleConfigProperties(since));
    }

    @Override
    public ContentWrapperDto getContentByReference(ArtifactReferenceDto reference) {
        return proxy(storage -> storage.getContentByReference(reference));
    }

    @Override
    public boolean isContentExists(String contentHash) {
        return proxy(storage -> storage.isContentExists(contentHash));
    }

    @Override
    public boolean isArtifactRuleExists(String groupId, String artifactId, RuleType rule) {
        return false;
    }

    @Override
    public boolean isGlobalRuleExists(RuleType rule) {
        return proxy(storage -> storage.isGlobalRuleExists(rule));
    }

    @Override
    public boolean isRoleMappingExists(String principalId) {
        return proxy(storage -> storage.isRoleMappingExists(principalId));
    }

    @Override
    public Optional<Long> contentIdFromHash(String contentHash) {
        return proxy(storage -> storage.contentIdFromHash(contentHash));
    }

    @Override
    public boolean isArtifactExists(String groupId, String artifactId) {
        return proxy(storage -> storage.isArtifactExists(groupId, artifactId));
    }

    @Override
    public boolean isEmpty() {
        return proxy(storage -> storage.isEmpty());
    }

    @Override
    public boolean isGroupExists(String groupId) {
        return proxy(storage -> storage.isGroupExists(groupId));
    }

    @Override
    public List<Long> getContentIdsReferencingArtifactVersion(String groupId, String artifactId,
            String version) {
        return proxy(
                storage -> storage.getContentIdsReferencingArtifactVersion(groupId, artifactId, version));
    }

    @Override
    public List<Long> getGlobalIdsReferencingArtifactVersion(String groupId, String artifactId,
            String version) {
        return proxy(storage -> storage.getGlobalIdsReferencingArtifactVersion(groupId, artifactId, version));
    }

    @Override
    public List<Long> getGlobalIdsReferencingArtifact(String groupId, String artifactId) {
        return proxy(storage -> storage.getGlobalIdsReferencingArtifact(groupId, artifactId));
    }

    @Override
    public List<ArtifactReferenceDto> getInboundArtifactReferences(String groupId, String artifactId,
            String version) {
        return proxy(storage -> storage.getInboundArtifactReferences(groupId, artifactId, version));
    }

    @Override
    public boolean isArtifactVersionExists(String groupId, String artifactId, String version) {
        return proxy(storage -> storage.isArtifactVersionExists(groupId, artifactId, version));
    }

    @Override
    public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, Integer offset, Integer limit) {
        return proxy(storage -> storage.searchGroups(filters, orderBy, orderDirection, offset, limit));
    }

    @Override
    public List<CommentDto> getArtifactVersionComments(String groupId, String artifactId, String version) {
        return proxy(storage -> storage.getArtifactVersionComments(groupId, artifactId, version));
    }

    @Override
    public VersionState getArtifactVersionState(String groupId, String artifactId, String version) {
        return proxy(storage -> storage.getArtifactVersionState(groupId, artifactId, version));
    }

    @Override
    public DynamicConfigPropertyDto getConfigProperty(String propertyName) {
        return proxy(storage -> storage.getConfigProperty(propertyName));
    }

    @Override
    public List<DynamicConfigPropertyDto> getConfigProperties() {
        return proxy(DynamicConfigStorage::getConfigProperties);
    }

    @Override
    public BranchMetaDataDto getBranchMetaData(GA ga, BranchId branchId) {
        return proxy(storage -> storage.getBranchMetaData(ga, branchId));
    }

    @Override
    public BranchSearchResultsDto getBranches(GA ga, int offset, int limit) {
        return proxy(storage -> storage.getBranches(ga, offset, limit));
    }

    @Override
    public VersionSearchResultsDto getBranchVersions(GA ga, BranchId branchId, int offset, int limit) {
        return proxy(storage -> storage.getBranchVersions(ga, branchId, offset, limit));
    }

    @Override
    public GAV getBranchTip(GA ga, BranchId branchId, Set<VersionState> behavior) {
        return proxy(storage -> storage.getBranchTip(ga, branchId, behavior));
    }

    @Override
    public String triggerSnapshotCreation() throws RegistryStorageException {
        return proxy((RegistryStorage::triggerSnapshotCreation));
    }

    @Override
    public String createSnapshot(String snapshotLocation) throws RegistryStorageException {
        return proxy((storage -> storage.createSnapshot(snapshotLocation)));
    }

    @Override
    public String createEvent(OutboxEvent event) {
        return proxy((storage -> storage.createEvent(event)));
    }

    @Override
    public boolean supportsDatabaseEvents() {
        return proxy((RegistryStorage::supportsDatabaseEvents));
    }
}
