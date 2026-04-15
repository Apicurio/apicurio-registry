package io.apicurio.registry.storage.impl.polling;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.DynamicConfigStorage;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.storage.impl.polling.sql.AbstractReadOnlyRegistryStorage;
import io.apicurio.registry.storage.impl.polling.sql.BlueSqlStorage;
import io.apicurio.registry.storage.impl.polling.sql.GreenSqlStorage;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.impexp.Entity;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;


/**
 * Abstract base class for polling-based registry storage implementations.
 * Provides the blue-green switching pattern for loading data from external sources
 * (Git, Kubernetes ConfigMaps, etc.) without service interruption.
 */
public abstract class AbstractPollingRegistryStorage<MARKER> extends AbstractReadOnlyRegistryStorage {

    @Inject
    Logger log;

    @Inject
    BlueSqlStorage blue;

    @Inject
    GreenSqlStorage green;

    // Fair lock, so we ensure the writer does not wait indefinitely under high throughput.
    private final ReentrantReadWriteLock switchLock = new ReentrantReadWriteLock(true);

    private RegistryStorage active = null;
    private RegistryStorage inactive = null;

    private volatile State state = State.READY_TO_WRITE;

    private enum State {
        READY_TO_SWITCH, // Data has been loaded to the inactive storage, but not yet published
        READY_TO_WRITE, // Latest data has been published, and we are ready to write to the inactive storage
    }

    // The poll result pending switch (holds the commit action)
    private PollingResult<MARKER> pendingResult = null;

    // Tracks consecutive switch failures for deadlock prevention
    private int switchRetryCount = 0;
    private static final int MAX_SWITCH_RETRIES = 3;

    // The processing result pending switch (holds load stats)
    private PollingProcessingResult pendingProcessingResult = null;

    // Tracks whether the first successful data load has completed.
    // Used by isReady() to prevent serving empty results before initial data is available.
    private volatile boolean isReady = false;

    // When set, the next refresh cycle will poll immediately without waiting
    // for the poll period. Used by requestSync() and K8s watch callbacks.
    private volatile boolean syncRequested = false;

    private volatile boolean initialized = false;
    private PollingStorageConfig pollingConfig;
    private PollingDataSourceManager<MARKER> pollingDataSourceManager;
    private Debouncer<PollingResult<MARKER>> debouncer;

    private final ReentrantLock refreshLock = new ReentrantLock();

    private Instant lastRefresh = Instant.MIN;

    private volatile PollingStorageStatus status = PollingStorageStatus.initializing();

    @Override
    public String storageName() {
        return pollingConfig.getStorageName();
    }

    /**
     * Converts a poll marker to a human-readable string for status reporting.
     * Override in subclasses if the marker's toString() is not suitable (e.g., Git RevCommit).
     */
    protected String markerToString(MARKER marker) {
        return marker != null ? marker.toString() : null;
    }

    /**
     * Extracts per-repo markers from the poll marker for status reporting.
     * Returns null by default. Override in subclasses that support multi-repo
     * (e.g., GitOps with multiple repositories).
     */
    protected Map<String, String> markerToSources(MARKER marker) {
        return null;
    }

    protected void initialize(PollingStorageConfig pollingConfig, PollingDataSourceManager<MARKER> pollingDataSourceManager) {
        this.pollingConfig = pollingConfig;
        this.pollingDataSourceManager = pollingDataSourceManager;
        this.debouncer = new Debouncer<>(pollingConfig.getDebounceQuietPeriod(), pollingConfig.getDebounceMaxWaitPeriod());

        log.info("Using {} storage", storageName());

        green.initialize();
        blue.initialize();

        try {
            active = green;
            inactive = blue;
            pollingDataSourceManager.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        initialized = true;
    }

    /**
     * Periodically called to check for changes and update storage.
     * <p>
     * It is expected that this method will be called often,
     * and has its own logic for timing actual storage refreshes.
     * <p>
     * This method is thread-safe.
     */
    protected void tryRefresh() {
        if (!initialized) {
            return;
        }
        if (refreshLock.tryLock()) {
            try {
                log.trace("Running {} refresh. Active database is {} and state is {}.",
                        storageName(), active == green ? "green" : "blue", state);

                switch (state) {
                    case READY_TO_SWITCH -> {
                        MARKER completedMarker = pendingResult.getMarker();
                        String markerStr = markerToString(completedMarker);
                        if (doSwitch()) {
                            switchRetryCount = 0;
                            pendingResult.commit();
                            pendingResult = null;
                            var completedResult = pendingProcessingResult;
                            pendingProcessingResult = null;
                            status = PollingStorageStatus.builder()
                                    .syncState(PollingStorageStatus.SyncState.IDLE)
                                    .currentMarker(markerStr)
                                    .sources(markerToSources(completedMarker))
                                    .lastSuccessfulSync(Instant.now())
                                    .lastSyncAttempt(status.getLastSyncAttempt())
                                    .groupCount(completedResult.getGroupCount())
                                    .artifactCount(completedResult.getArtifactCount())
                                    .versionCount(completedResult.getVersionCount())
                                    .lastErrors(Collections.emptyList())
                                    .build();
                            if (!isReady) {
                                isReady = true;
                                log.info("{} initial data load completed, storage is now ready", storageName());
                            }
                        } else {
                            switchRetryCount++;
                            if (switchRetryCount >= MAX_SWITCH_RETRIES) {
                                log.error("{} failed to acquire write lock after {} attempts, " +
                                        "discarding pending update to prevent deadlock", storageName(), MAX_SWITCH_RETRIES);
                                status = status.toBuilder()
                                        .syncState(PollingStorageStatus.SyncState.ERROR)
                                        .lastErrors(List.of("Failed to publish update: could not acquire write lock after "
                                                + MAX_SWITCH_RETRIES + " attempts"))
                                        .build();
                                pendingResult = null;
                                pendingProcessingResult = null;
                                state = State.READY_TO_WRITE;
                                switchRetryCount = 0;
                            } else {
                                status = status.toBuilder()
                                        .syncState(PollingStorageStatus.SyncState.SWITCHING)
                                        .build();
                            }
                        }
                    }
                    case READY_TO_WRITE -> {
                        var now = Instant.now();
                        boolean forceRefresh = syncRequested;
                        if (forceRefresh) {
                            syncRequested = false;
                        }
                        if (forceRefresh || now.isAfter(lastRefresh.plus(pollingConfig.getPollPeriod()))) {
                            lastRefresh = now;
                            status = status.toBuilder()
                                    .lastSyncAttempt(now)
                                    .build();
                            log.debug("Running {} poll. Active database is {} and state is {}.",
                                    storageName(), active == green ? "green" : "blue", state);
                            try {
                                var pollResult = pollingDataSourceManager.poll();
                                if (pollResult.isHasChanges()) {
                                    debouncer.onChange(pollResult);
                                }
                                if (debouncer.isReady()) {
                                    loadInactive(debouncer.pending());
                                    debouncer.reset();
                                }
                            } catch (Exception e) {
                                log.error("{} poll/load failed: {}", storageName(), e.getMessage(), e);
                            }
                        }
                    }
                }
                log.trace("{} refresh finished. Active database is {} and state is {}.",
                        storageName(), active == green ? "green" : "blue", state);
            } finally {
                refreshLock.unlock();
            }
        }
    }

    /**
     * Loads data from a poll result into the inactive database and prepares for a switch.
     */
    private void loadInactive(PollingResult<MARKER> pollResult) {
        try {
            String markerStr = markerToString(pollResult.getMarker());
            status = status.toBuilder()
                    .syncState(PollingStorageStatus.SyncState.LOADING)
                    .build();

            inactive.deleteAllUserData();
            var result = pollingDataSourceManager.process(inactive, pollResult);
            if (result.isSuccessful()) {
                log.info("{} update loaded successfully (marker: {})", storageName(), pollResult.getMarker());
                pendingResult = pollResult;
                pendingProcessingResult = result;
                state = State.READY_TO_SWITCH;
            } else {
                log.error("{} update failed to load (marker: {}). {} error(s):",
                        storageName(), pollResult.getMarker(), result.getErrors().size());
                result.getErrors().forEach(e -> log.error("  - {}", e));
                // Data errors won't be fixed by retrying the same commit — commit the marker
                // so we stop re-detecting this change. A new commit will trigger a fresh attempt.
                pollResult.commit();
                status = status.toBuilder()
                        .syncState(PollingStorageStatus.SyncState.ERROR)
                        .lastErrors(result.getErrors())
                        .build();
            }
        } catch (Exception e) {
            // Transient errors (H2 issues, OOM, etc.) — don't commit the marker,
            // so the next poll cycle will retry the same commit.
            log.error("{} failed to load data into inactive storage: {}", storageName(), e.getMessage(), e);
            status = status.toBuilder()
                    .syncState(PollingStorageStatus.SyncState.ERROR)
                    .lastErrors(List.of("Transient error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Atomically swaps the active and inactive databases.
     *
     * @return true if the switch was successful, false if the write lock could not be acquired
     */
    private boolean doSwitch() {
        try {
            if (switchLock.writeLock().tryLock(5, TimeUnit.SECONDS)) {
                try {
                    var previous = active;
                    active = inactive;
                    inactive = previous;
                } finally {
                    state = State.READY_TO_WRITE;
                    switchLock.writeLock().unlock();
                    log.info("{} update published", storageName());
                }
                return true;
            } else {
                log.warn("{} could not acquire write lock for switch within 5 seconds, will retry", storageName());
                return false;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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

    /**
     * Returns the current synchronization status of this storage.
     */
    public PollingStorageStatus getStatus() {
        return status;
    }

    /**
     * Requests an immediate synchronization.
     * The next scheduler invocation will poll without waiting for the poll period.
     */
    public void requestSync() {
        syncRequested = true;
    }

    @Override
    public boolean isReady() {
        return isReady;
    }

    @Override
    public boolean isAlive() {
        return true;
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
    public ContractRuleSetDto getArtifactContractRuleset(String groupId, String artifactId) {
        return proxy(storage -> storage.getArtifactContractRuleset(groupId, artifactId));
    }

    @Override
    public ContractRuleSetDto getVersionContractRuleset(String groupId, String artifactId,
            String version) {
        return proxy(storage -> storage.getVersionContractRuleset(groupId, artifactId, version));
    }

    @Override
    public List<ContractRuleWithCoordinatesDto> getContractRulesByTag(String tag) {
        return proxy(storage -> storage.getContractRulesByTag(tag));
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
    public void exportData(String groupId, Function<Entity, Void> handler) {
        proxyAction(storage -> storage.exportData(groupId, handler));
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
        return proxy(storage -> storage.isArtifactRuleExists(groupId, artifactId, rule));
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
    public void forEachVersion(long sinceTimestamp, Consumer<VersionContentDto> consumer) {
        proxyAction(storage -> storage.forEachVersion(sinceTimestamp, consumer));
    }

    @Override
    public void forEachVersion(Consumer<VersionContentDto> consumer) {
        proxyAction(storage -> storage.forEachVersion(consumer));
    }

    @Override
    public List<ArtifactVersionMetaDataDto> getVersionsModifiedSince(long sinceTimestamp) {
        return proxy(storage -> storage.getVersionsModifiedSince(sinceTimestamp));
    }

    @Override
    public long countVersionsModifiedSince(long sinceTimestamp) {
        return proxy(storage -> storage.countVersionsModifiedSince(sinceTimestamp));
    }

    @Override
    public long getLatestVersionTimestamp() {
        return proxy(RegistryStorage::getLatestVersionTimestamp);
    }

    @Override
    public List<Long> getAllVersionGlobalIds() {
        return proxy(RegistryStorage::getAllVersionGlobalIds);
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
