package io.apicurio.registry.storage.impl.sql;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.core.System;
import io.apicurio.registry.events.ArtifactCreated;
import io.apicurio.registry.events.ArtifactDeleted;
import io.apicurio.registry.events.ArtifactVersionCreated;
import io.apicurio.registry.events.ArtifactVersionDeleted;
import io.apicurio.registry.exception.UnreachableCodeException;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.rest.ConflictException;
import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.semver.SemVerConfigProperties;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.StorageBehaviorProperties;
import io.apicurio.registry.storage.StorageEvent;
import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.BranchMetaDataDto;
import io.apicurio.registry.storage.dto.BranchSearchResultsDto;
import io.apicurio.registry.storage.dto.CommentDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableBranchMetaDataDto;
import io.apicurio.registry.storage.dto.EditableGroupMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.dto.RoleMappingDto;
import io.apicurio.registry.storage.dto.RoleMappingSearchResultsDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.SearchedGroupDto;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.ContentNotFoundException;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.RuleAlreadyExistsException;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactReferenceDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactVersionMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.DatabaseLockMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GAVMapper;
import io.apicurio.registry.storage.impl.sql.mappers.StoredArtifactMapper;
import io.apicurio.registry.storage.impl.sql.mappers.StringMapper;
import io.apicurio.registry.storage.impl.sql.repositories.SqlArtifactRepository;
import io.apicurio.registry.storage.impl.sql.repositories.SqlBranchRepository;
import io.apicurio.registry.storage.impl.sql.repositories.SqlCommentRepository;
import io.apicurio.registry.storage.impl.sql.repositories.SqlConfigRepository;
import io.apicurio.registry.storage.impl.sql.repositories.SqlDownloadRepository;
import io.apicurio.registry.storage.impl.sql.repositories.SqlRoleMappingRepository;
import io.apicurio.registry.storage.impl.sql.repositories.SqlSequenceRepository;
import io.apicurio.registry.storage.impl.sql.repositories.SqlContentRepository;
import io.apicurio.registry.storage.impl.sql.repositories.SqlExportRepository;
import io.apicurio.registry.storage.impl.sql.repositories.SqlGroupRepository;
import io.apicurio.registry.storage.impl.sql.repositories.SqlRuleRepository;
import io.apicurio.registry.storage.impl.sql.repositories.SqlSearchRepository;
import io.apicurio.registry.storage.impl.sql.repositories.SqlVersionRepository;
import io.apicurio.registry.storage.importing.DataImporter;
import io.apicurio.registry.storage.importing.v2.SqlDataUpgrader;
import io.apicurio.registry.storage.importing.v3.SqlDataImporter;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityInputStream;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.utils.impexp.v3.CommentEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;
import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;
import static io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils.notEmpty;
import static io.apicurio.registry.utils.StringUtil.limitStr;

/**
 * A SQL implementation of the {@link RegistryStorage} interface. This impl does not use any ORM technology -
 * it simply uses native SQL for all operations.
 */
public abstract class AbstractSqlRegistryStorage implements RegistryStorage {

    private static final int DB_VERSION = Integer
            .parseInt(IoUtil.toString(AbstractSqlRegistryStorage.class.getResourceAsStream("db-version")));

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, true);
    }

    @Inject
    Logger log;

    @Inject
    System system;

    @Inject
    SqlStatements sqlStatements;

    @Inject
    SecurityIdentity securityIdentity;

    HandleFactory handles;

    @Inject
    StorageBehaviorProperties storageBehaviorProps;

    @Inject
    RegistryStorageContentUtils utils;

    @Inject
    SemVerConfigProperties semVerConfigProps;

    @Inject
    RestConfig restConfig;

    @Inject

    protected SqlStatements sqlStatements() {
        return sqlStatements;
    }

    @ConfigProperty(name = "apicurio.sql.init", defaultValue = "true")
    @Info(category = CATEGORY_STORAGE, description = "SQL init", availableSince = "2.0.0.Final")
    boolean initDB;

    @ConfigProperty(name = "apicurio.sql.db-schema", defaultValue = "*")
    @Info(category = CATEGORY_STORAGE, description = "Database schema name (only needed when running two instances of Registry against the same database, in multiple schemas)", availableSince = "3.0.6")
    String dbSchema;

    @Inject
    @ConfigProperty(name = "apicurio.events.kafka.topic", defaultValue = "registry-events")
    @Info(category = CATEGORY_STORAGE, description = "Storage event topic")
    String eventsTopic;

    @ConfigProperty(name = "apicurio.storage.enable-automatic-group-creation", defaultValue = "true")
    @Info(category = CATEGORY_STORAGE, description = "Enable automatic creation of group when creating an artifact", availableSince = "3.0.15")
    boolean enableAutomaticGroupCreation;

    @Inject
    Event<SqlStorageEvent> sqlStorageEvent;

    @Inject
    Event<StorageEvent> storageEvent;

    @Inject
    Event<SqlOutboxEvent> outboxEvent;

    // Repository injections for delegated operations
    @Inject
    SqlArtifactRepository artifactRepository;

    @Inject
    SqlVersionRepository versionRepository;

    @Inject
    SqlGroupRepository groupRepository;

    @Inject
    SqlBranchRepository branchRepository;

    @Inject
    SqlRuleRepository ruleRepository;

    @Inject
    SqlContentRepository contentRepository;

    @Inject
    SqlSearchRepository searchRepository;

    @Inject
    SqlCommentRepository commentRepository;

    @Inject
    SqlConfigRepository configRepository;

    @Inject
    SqlRoleMappingRepository roleMappingRepository;

    @Inject
    SqlDownloadRepository downloadRepository;

    @Inject
    SqlSequenceRepository sequenceRepository;

    @Inject
    SqlExportRepository exportRepository;

    private volatile boolean isReady = false;
    private volatile Instant isAliveLastCheck = Instant.MIN;
    private volatile boolean isAliveCached = false;

    /**
     * @param emitStorageReadyEvent The concrete implementation needs to tell AbstractSqlRegistryStorage
     *            whether it should fire {@see io.apicurio.registry.storage.StorageEvent} in addition to
     *            {@see io.apicurio.registry.storage.impl.sql.SqlStorageEvent}. Multiple storage
     *            implementations may be present at the same time (in particular when using KafkaSQL
     *            persistence), but only the single {@see io.apicurio.registry.types.Current} one may fire the
     *            former event.
     */
    protected void initialize(HandleFactory handleFactory, boolean emitStorageReadyEvent) {
        this.handles = handleFactory;

        log.info("SqlRegistryStorage constructed successfully.");

        handles.withHandleNoException((handle) -> {
            if (initDB) {
                // Acquire database lock to prevent race conditions when multiple replicas
                // attempt to initialize or upgrade the database simultaneously
                log.info("Acquiring database initialization lock...");
                handle.createQuery(this.sqlStatements.acquireInitLock()).map(DatabaseLockMapper.instance).one();
                log.info("Database initialization lock acquired.");

                try {
                    if (!isDatabaseInitializedRaw(handle)) {
                        log.info("Database not initialized.");
                        initializeDatabaseRaw(handle);
                    } else {
                        log.info("Database was already initialized, skipping.");
                    }

                    if (!isDatabaseCurrentRaw(handle)) {
                        log.info("Old database version detected, upgrading.");
                        upgradeDatabaseRaw(handle);
                    }
                } finally {
                    // Always release the lock, even if initialization or upgrade fails
                    log.info("Releasing database initialization lock...");
                    handle.createQuery(this.sqlStatements.releaseInitLock()).map(DatabaseLockMapper.instance).one();
                    log.info("Database initialization lock released.");
                }
            } else {
                if (!isDatabaseInitializedRaw(handle)) {
                    log.error(
                            "Database not initialized.  Please use the DDL scripts to initialize the database before starting the application.");
                    throw new RuntimeException("Database not initialized.");
                }

                if (!isDatabaseCurrentRaw(handle)) {
                    log.error(
                            "Detected an old version of the database.  Please use the DDL upgrade scripts to bring your database up to date.");
                    throw new RuntimeException("Database not upgraded.");
                }
            }
            return null;
        });

        // If using H2, we need to initialize the sequence counters by querying for
        // the current max value of each in the DB.
        if (isH2()) {
            handles.withHandleNoException((handle) -> {
                sequenceRepository.initializeSequenceCounters(handle);
                return null;
            });
        }

        isReady = true;
        SqlStorageEvent initializeEvent = new SqlStorageEvent();
        initializeEvent.setType(SqlStorageEventType.READY);
        sqlStorageEvent.fire(initializeEvent);
        if (emitStorageReadyEvent) {
            /*
             * In cases where the observer of the event also injects the source bean, such as the
             * io.apicurio.registry.ImportLifecycleBean, a kind of recursion may happen. This is because the
             * event is fired in the @PostConstruct method, and is being processed in the same thread. We
             * avoid this by processing the event asynchronously. Note that this requires the
             * jakarta.enterprise.event.ObservesAsync annotation on the receiving side. If this becomes
             * cumbersome, try using ManagedExecutor.
             */
            storageEvent.fireAsync(StorageEvent.builder().type(StorageEventType.READY).build());
        }
    }

    /**
     * @return true if the database has already been initialized
     */
    private boolean isDatabaseInitializedRaw(Handle handle) {
        log.info("Checking to see if the DB is initialized.");
        if ("*".equals(dbSchema)) {
            int count = handle.createQuery(this.sqlStatements.isDatabaseInitialized()).mapTo(Integer.class)
                    .one();
            return count > 0;
        } else {
            int count = handle.createQuery(this.sqlStatements.isDatabaseSchemaInitialized()).bind(0, dbSchema)
                    .mapTo(Integer.class).one();
            return count > 0;
        }
    }

    /**
     * @return true if the database has already been initialized
     */
    private boolean isDatabaseCurrentRaw(Handle handle) {
        log.info("Checking to see if the DB is up-to-date.");
        log.info("Build's DB version is {}", DB_VERSION);
        int version = this.getDatabaseVersionRaw(handle);

        // Fast-fail if we try to run Registry v3 against a v2 DB.
        if (version < 100) {
            String message = "[Apicurio Registry 3.x] Detected legacy 2.x database.  Automatic upgrade from 2.x to 3.x is not supported.  Please see documentation for migration instructions.";
            log.error("--------------------------");
            log.error(message);
            log.error("--------------------------");
            throw new RuntimeException(message);
        }
        return version == DB_VERSION;
    }

    private void initializeDatabaseRaw(Handle handle) {
        log.info("Initializing the Apicurio Registry database.");
        log.info("\tDatabase type: " + this.sqlStatements.dbType());

        final List<String> statements = this.sqlStatements.databaseInitialization();
        log.debug("---");

        statements.forEach(statement -> {
            log.debug(statement);
            handle.createUpdate(statement).execute();
        });
        log.debug("---");
    }

    /**
     * Upgrades the database by executing a number of DDL statements found in DB-specific DDL upgrade scripts.
     */
    private void upgradeDatabaseRaw(Handle handle) {
        log.info("Upgrading the Apicurio Hub API database.");

        int fromVersion = this.getDatabaseVersionRaw(handle);
        int toVersion = DB_VERSION;

        log.info("\tDatabase type: {}", this.sqlStatements.dbType());
        log.info("\tFrom Version:  {}", fromVersion);
        log.info("\tTo Version:    {}", toVersion);

        final List<String> statements = this.sqlStatements.databaseUpgrade(fromVersion, toVersion);
        log.debug("---");
        statements.forEach(statement -> {
            log.debug(statement);

            if (statement.startsWith("UPGRADER:")) {
                String cname = statement.substring(9).trim();
                applyUpgraderRaw(handle, cname);
            } else {
                handle.createUpdate(statement).execute();
            }
        });
        log.debug("---");
    }

    /**
     * Instantiates an instance of the given upgrader class and then invokes it. Used to perform advanced
     * upgrade logic when upgrading the DB (logic that cannot be handled in simple SQL statements).
     *
     * @param handle
     * @param cname
     */
    private void applyUpgraderRaw(Handle handle, String cname) {
        try {
            @SuppressWarnings("unchecked")
            Class<IDbUpgrader> upgraderClass = (Class<IDbUpgrader>) Class.forName(cname);
            IDbUpgrader upgrader = upgraderClass.getConstructor().newInstance();
            upgrader.upgrade(handle);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reuturns the current DB version by selecting the value in the 'apicurio' table.
     */
    private int getDatabaseVersionRaw(Handle handle) {
        try {
            int version = handle.createQuery(this.sqlStatements.getDatabaseVersion()).bind(0, "db_version")
                    .mapTo(Integer.class).one();
            return version;
        } catch (Exception e) {
            log.error("Error getting DB version.", e);
            return 0;
        }
    }

    @Override
    public boolean isReady() {
        return isReady;
    }

    @Override
    public boolean isAlive() {
        if (!isReady) {
            return false;
        }
        if (Instant.now().isAfter(isAliveLastCheck.plus(Duration.ofSeconds(2)))) { // Tradeoff between
                                                                                   // reducing load on the DB
                                                                                   // and responsiveness: 2s
            isAliveLastCheck = Instant.now();
            try {
                getGlobalRules();
                isAliveCached = true;
            } catch (Exception ex) {
                isAliveCached = false;
            }
        }
        return isAliveCached;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String storageName() {
        return "sql";
    }

    @Override
    public ContentWrapperDto getContentById(long contentId)
            throws ContentNotFoundException, RegistryStorageException {
        return contentRepository.getContentById(contentId);
    }

    public ContentWrapperDto getContentByIdRaw(Handle handle, long contentId)
            throws ContentNotFoundException, RegistryStorageException {
        return contentRepository.getContentByIdRaw(handle, contentId);
    }

    @Override
    public ContentWrapperDto getContentByHash(String contentHash)
            throws ContentNotFoundException, RegistryStorageException {
        return contentRepository.getContentByHash(contentHash);
    }

    @Override
    public List<ArtifactVersionMetaDataDto> getArtifactVersionsByContentId(long contentId) {
        return handles.withHandleNoException(handle -> {
            List<ArtifactVersionMetaDataDto> dtos = handle
                    .createQuery(sqlStatements().selectArtifactVersionMetaDataByContentId())
                    .bind(0, contentId).map(ArtifactVersionMetaDataDtoMapper.instance).list();
            if (dtos.isEmpty()) {
                throw new ContentNotFoundException(contentId);
            }
            return dtos;
        });
    }

    /**
     * @see RegistryStorage#getEnabledArtifactContentIds(String, String)
     */
    @Override
    public List<Long> getEnabledArtifactContentIds(String groupId, String artifactId) {
        return contentRepository.getEnabledArtifactContentIds(groupId, artifactId);
    }

    @Override
    public Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> createArtifact(String groupId,
            String artifactId, String artifactType, EditableArtifactMetaDataDto artifactMetaData,
            String version, ContentWrapperDto versionContent, EditableVersionMetaDataDto versionMetaData,
            List<String> versionBranches, boolean versionIsDraft, boolean dryRun, String owner)
            throws RegistryStorageException {
        log.debug("Inserting an artifact row for: {} {}", groupId, artifactId);

        Date createdOn = new Date();

        EditableArtifactMetaDataDto amd = artifactMetaData == null
            ? EditableArtifactMetaDataDto.builder().build() : artifactMetaData;

        // Create the group if it doesn't exist yet.
        if (groupId != null && !isGroupExists(groupId)) {
            if (enableAutomaticGroupCreation) {
                // Only create group metadata for non-default groups.
                ensureGroup(GroupMetaDataDto.builder().groupId(groupId).createdOn(createdOn.getTime())
                        .modifiedOn(createdOn.getTime()).owner(owner).modifiedBy(owner).build());
            } else {
                throw new GroupNotFoundException(groupId);
            }
        }

        // Ensure the content exists. If this is a dryRun, or if the create fails, this
        // could result in orphaned content. That's OK because we have an async process
        // that will later delete any orphaned content.
        long cid = -1;
        if (versionContent != null) {
            // Put the content in the DB and get the unique content ID back.
            cid = ensureContentAndGetId(artifactType, versionContent, versionIsDraft);
        }
        final long contentId = cid;

        try {
            return handles.withHandle(handle -> {
                // Always roll back the transaction if this is a dryRun
                if (dryRun) {
                    handle.setRollback(true);
                }

                Map<String, String> labels = amd.getLabels();
                String labelsStr = RegistryContentUtils.serializeLabels(labels);

                // Create a row in the artifacts table.
                handle.createUpdate(sqlStatements.insertArtifact()).bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId).bind(2, artifactType).bind(3, owner).bind(4, createdOn)
                        .bind(5, owner) // modifiedBy
                        .bind(6, createdOn) // modifiedOn
                        .bind(7, limitStr(amd.getName(), 512))
                        .bind(8, limitStr(amd.getDescription(), 1024, true)).bind(9, labelsStr).execute();

                // Insert labels into the "artifact_labels" table
                if (labels != null && !labels.isEmpty()) {
                    labels.forEach((k, v) -> {
                        handle.createUpdate(sqlStatements.insertArtifactLabel())
                                .bind(0, normalizeGroupId(groupId)).bind(1, artifactId)
                                .bind(2, limitStr(k.toLowerCase(), 256))
                                .bind(3, limitStr(v.toLowerCase(), 512)).execute();
                    });
                }

                // Return an artifact metadata dto
                ArtifactMetaDataDto amdDto = ArtifactMetaDataDto.builder().groupId(groupId)
                        .artifactId(artifactId).name(amd.getName()).description(amd.getDescription())
                        .createdOn(createdOn.getTime()).owner(owner).modifiedOn(createdOn.getTime())
                        .modifiedBy(owner).artifactType(artifactType).labels(labels).build();

                // The artifact was successfully created! Create the version as well, if one was included.
                ImmutablePair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> pair;
                if (versionContent != null) {
                    ArtifactVersionMetaDataDto vmdDto = createArtifactVersionRaw(handle, true, groupId,
                            artifactId, version, versionMetaData, owner, createdOn, contentId,
                            versionBranches, versionIsDraft);

                    pair = ImmutablePair.of(amdDto, vmdDto);
                } else {
                    pair = ImmutablePair.of(amdDto, null);
                }

                outboxEvent.fire(SqlOutboxEvent.of(ArtifactCreated.of(amdDto)));

                return pair;
            });
        } catch (Exception ex) {
            if (sqlStatements.isPrimaryKeyViolation(ex)) {
                throw new ArtifactAlreadyExistsException(groupId, artifactId);
            }
            throw ex;
        }
    }

    private ArtifactVersionMetaDataDto createArtifactVersionRaw(Handle handle, boolean firstVersion,
            String groupId, String artifactId, String version, EditableVersionMetaDataDto metaData,
            String owner, Date createdOn, Long contentId, List<String> branches, boolean isDraft) {
        if (metaData == null) {
            metaData = EditableVersionMetaDataDto.builder().build();
        }

        VersionState state = isDraft ? VersionState.DRAFT : VersionState.ENABLED;
        String labelsStr = RegistryContentUtils.serializeLabels(metaData.getLabels());

        Long globalId = nextGlobalIdRaw(handle);
        GAV gav;

        // Create a row in the "versions" table
        if (firstVersion) {
            if (version == null) {
                version = "1";
            }
            final String finalVersion1 = version; // Lambda requirement
            handle.createUpdate(sqlStatements.insertVersion(true)).bind(0, globalId)
                    .bind(1, normalizeGroupId(groupId)).bind(2, artifactId).bind(3, finalVersion1)
                    .bind(4, state).bind(5, limitStr(metaData.getName(), 512))
                    .bind(6, limitStr(metaData.getDescription(), 1024, true)).bind(7, owner)
                    .bind(8, createdOn).bind(9, owner).bind(10, createdOn).bind(11, labelsStr)
                    .bind(12, contentId).execute();

            gav = new GAV(groupId, artifactId, finalVersion1);
        } else {
            handle.createUpdate(sqlStatements.insertVersion(false)).bind(0, globalId)
                    .bind(1, normalizeGroupId(groupId)).bind(2, artifactId).bind(3, version)
                    .bind(4, normalizeGroupId(groupId)).bind(5, artifactId).bind(6, state)
                    .bind(7, limitStr(metaData.getName(), 512))
                    .bind(8, limitStr(metaData.getDescription(), 1024, true)).bind(9, owner)
                    .bind(10, createdOn).bind(11, owner).bind(12, createdOn).bind(13, labelsStr)
                    .bind(14, contentId).execute();

            // If version is null, update the row we just inserted to set the version to the generated
            // versionOrder
            if (version == null) {
                handle.createUpdate(sqlStatements.autoUpdateVersionForGlobalId()).bind(0, globalId).execute();
            }

            gav = getGAVByGlobalIdRaw(handle, globalId);
        }

        // Insert labels into the "version_labels" table
        if (metaData.getLabels() != null && !metaData.getLabels().isEmpty()) {
            metaData.getLabels().forEach((k, v) -> {
                handle.createUpdate(sqlStatements.insertVersionLabel()).bind(0, globalId)
                        .bind(1, limitStr(k.toLowerCase(), 256)).bind(2, limitStr(v.toLowerCase(), 512))
                        .execute();
            });
        }

        // Update system generated branches
        if (isDraft) {
            createOrUpdateBranchRaw(handle, gav, BranchId.DRAFTS, true);
        } else {
            createOrUpdateBranchRaw(handle, gav, BranchId.LATEST, true);
            createOrUpdateSemverBranchesRaw(handle, gav);
        }

        // Create any user defined branches
        if (branches != null && !branches.isEmpty()) {
            branches.forEach(branch -> {
                BranchId branchId = new BranchId(branch);
                createOrUpdateBranchRaw(handle, gav, branchId, false);
            });
        }

        ArtifactVersionMetaDataDto avmd = handle
                .createQuery(sqlStatements.selectArtifactVersionMetaDataByGlobalId()).bind(0, globalId)
                .map(ArtifactVersionMetaDataDtoMapper.instance).one();

        outboxEvent.fire(SqlOutboxEvent.of(ArtifactVersionCreated.of(avmd)));

        return avmd;
    }

    /**
     * If SemVer support is enabled, create (or update) the automatic system generated semantic versioning
     * branches.
     *
     * @param handle
     * @param gav
     */
    private void createOrUpdateSemverBranchesRaw(Handle handle, GAV gav) {
        branchRepository.createOrUpdateSemverBranchesRaw(handle, gav);
    }

    /**
     * Make sure the content exists in the database (try to insert it). Regardless of whether it already
     * existed or not, return the contentId of the content in the DB.
     */
    private Long ensureContentAndGetId(String artifactType, ContentWrapperDto contentDto, boolean isDraft) {
        List<ArtifactReferenceDto> references = contentDto.getReferences();

        // Deduplicate references to handle cases where callers (like Avro converters)
        // may send duplicate references for nested schemas. This must be done BEFORE
        // calculating content hashes to ensure consistency.
        if (references != null && !references.isEmpty()) {
            references = references.stream()
                    .distinct()
                    .collect(Collectors.toList());
        }

        TypedContent content = TypedContent.create(contentDto.getContent(), contentDto.getContentType());
        String contentHash;
        String canonicalContentHash;
        String serializedReferences;

        // Need to create the content hash and canonical content hash. If the content is DRAFT
        // content, then do NOT calculate those hashes because we don't want DRAFT content to
        // be looked up by those hashes.
        //
        // So we have three different paths to calculate the hashes:
        // 1. If DRAFT state
        // 2. If the content has references
        // 3. If the content has no references

        if (isDraft) {
            contentHash = "draft:" + UUID.randomUUID().toString();
            canonicalContentHash = "draft:" + UUID.randomUUID().toString();
            serializedReferences = null;
        } else if (notEmpty(references)) {
            Function<List<ArtifactReferenceDto>, Map<String, TypedContent>> referenceResolver = (refs) -> {
                return handles.withHandle(handle -> {
                    return resolveReferencesRaw(handle, refs);
                });
            };
            contentHash = utils.getContentHash(content, references);
            canonicalContentHash = utils.getCanonicalContentHash(content, artifactType, references,
                    referenceResolver);
            serializedReferences = RegistryContentUtils.serializeReferences(references);
        } else {
            contentHash = utils.getContentHash(content, null);
            canonicalContentHash = utils.getCanonicalContentHash(content, artifactType, null, null);
            serializedReferences = null;
        }

        // Ensure the content is in the DB.
        ensureContent(content, contentHash, canonicalContentHash, references, serializedReferences);

        // Get the contentId using the unique contentHash.
        Optional<Long> contentId = contentIdFromHash(contentHash);
        return contentId.orElseThrow(() -> new RegistryStorageException("Failed to ensure content."));
    }

    /**
     * Store the content in the database and return the content ID of the new row. If the content already
     * exists, just return the content ID of the existing row.
     */
    private void ensureContent(TypedContent content, String contentHash, String canonicalContentHash,
            List<ArtifactReferenceDto> references, String referencesSerialized) {

        handles.withHandleNoException(handle -> {

            // Insert the content into the content table.
            long contentId = nextContentIdRaw(handle);

            try {
                handle.createUpdate(sqlStatements.insertContent())
                        .bind(0, contentId)
                        .bind(1, canonicalContentHash)
                        .bind(2, contentHash)
                        .bind(3, content.getContentType())
                        .bind(4, content.getContent().bytes())
                        .bind(5, referencesSerialized)
                        .execute();
            } catch (Exception e) {
                if (sqlStatements.isPrimaryKeyViolation(e)) {
                    log.debug("Content with content hash {} already exists: {}", contentHash, content);
                    return;
                } else {
                    throw e;
                }
            }

            // If we get here, then the content was inserted and we need to insert the references.
            insertReferencesRaw(contentId, references);
        });
    }

    private void insertReferencesRaw(Long contentId, List<ArtifactReferenceDto> references) {
        if (references != null && !references.isEmpty()) {
            handles.withHandleNoException(handle -> {
                references.forEach(reference -> {
                    try {
                        handle.createUpdate(sqlStatements.insertContentReference())
                                .bind(0, contentId)
                                .bind(1, normalizeGroupId(reference.getGroupId()))
                                .bind(2, reference.getArtifactId())
                                .bind(3, reference.getVersion())
                                .bind(4, reference.getName())
                                .execute();
                    } catch (Exception e) {
                        if (sqlStatements.isPrimaryKeyViolation(e)) {
                            // We have to fail the transaction because the content hash would otherwise be invalid (duplicate references).
                            throw new ConflictException("Duplicate reference found: " + reference);
                        } else {
                            throw e;
                        }
                    }
                });
            });
        }
    }

    @Override
    public List<String> deleteArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Deleting an artifact: {} {}", groupId, artifactId);
        return handles.withHandle(handle -> {
            // Get the list of versions of the artifact (will be deleted)
            List<String> versions = handle.createQuery(sqlStatements.selectArtifactVersions())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).mapTo(String.class).list();

            // Note: delete artifact rules as well. Artifact rules are not set to cascade on delete
            // because the Confluent API allows users to configure rules for artifacts that don't exist. :(
            handle.createUpdate(sqlStatements.deleteArtifactRules()).bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId).execute();

            // Delete artifact row (should be just one)
            int rowCount = handle.createUpdate(sqlStatements.deleteArtifact())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).execute();

            if (rowCount == 0) {
                throw new ArtifactNotFoundException(groupId, artifactId);
            }

            deleteAllOrphanedContentRaw(handle);

            outboxEvent.fire(SqlOutboxEvent.of(ArtifactDeleted.of(groupId, artifactId)));

            return versions;
        });
    }

    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        log.debug("Deleting all artifacts in group: {}", groupId);
        handles.withHandle(handle -> {
            // Note: delete artifact rules separately. Artifact rules are not set to cascade on delete
            // because the Confluent API allows users to configure rules for artifacts that don't exist. :(
            handle.createUpdate(sqlStatements.deleteArtifactRulesByGroupId())
                    .bind(0, normalizeGroupId(groupId)).execute();

            // Delete all artifacts in the group
            int rowCount = handle.createUpdate(sqlStatements.deleteArtifactsByGroupId())
                    .bind(0, normalizeGroupId(groupId)).execute();

            if (rowCount == 0) {
                throw new ArtifactNotFoundException(groupId, null);
            }

            deleteAllOrphanedContentRaw(handle);

            return null;
        });
    }

    @Override
    public ArtifactVersionMetaDataDto createArtifactVersion(String groupId, String artifactId, String version,
            String artifactType, ContentWrapperDto content, EditableVersionMetaDataDto metaData,
            List<String> branches, boolean isDraft, boolean dryRun, String owner)
            throws VersionAlreadyExistsException, RegistryStorageException {
        log.debug("Creating new artifact version for {} {} (version {}).", groupId, artifactId, version);

        Date createdOn = new Date();

        // Put the content in the DB and get the unique content ID back.
        long contentId = ensureContentAndGetId(artifactType, content, isDraft);

        try {
            // Create version and return
            return handles.withHandle(handle -> {
                // Always roll back the transaction if this is a dryRun
                if (dryRun) {
                    handle.setRollback(true);
                }

                boolean isFirstVersion = countArtifactVersionsRaw(handle, groupId, artifactId) == 0;

                // Now create the version and return the new version metadata.
                ArtifactVersionMetaDataDto versionDto = createArtifactVersionRaw(handle, isFirstVersion,
                        groupId, artifactId, version,
                        metaData == null ? EditableVersionMetaDataDto.builder().build() : metaData, owner,
                        createdOn, contentId, branches, isDraft);
                return versionDto;
            });
        } catch (Exception ex) {
            if (sqlStatements.isPrimaryKeyViolation(ex)) {
                throw new VersionAlreadyExistsException(groupId, artifactId, version);
            }
            throw ex;
        }
    }

    @Override
    public long countActiveArtifactVersions(String groupId, String artifactId)
            throws RegistryStorageException {
        log.debug("Searching for versions of artifact {} {}", groupId, artifactId);
        return handles.withHandleNoException(handle -> {
            Integer count = handle.createQuery(sqlStatements.selectActiveArtifactVersionsCount())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).mapTo(Integer.class).one();
            return count.longValue();
        });

    }

    @Override
    public Set<String> getArtifactIds(Integer limit) { // TODO Paging and order by
        return artifactRepository.getArtifactIds(limit);
    }

    @Override
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit) {
        return searchRepository.searchArtifacts(filters, orderBy, orderDirection, offset, limit);
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return artifactRepository.getArtifactMetaData(groupId, artifactId);
    }

    private ArtifactMetaDataDto getArtifactMetaDataRaw(Handle handle, String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        Optional<ArtifactMetaDataDto> res = handle.createQuery(sqlStatements.selectArtifactMetaData())
                .bind(0, normalizeGroupId(groupId)).bind(1, artifactId)
                .map(ArtifactMetaDataDtoMapper.instance).findOne();
        return res.orElseThrow(() -> new ArtifactNotFoundException(groupId, artifactId));
    }

    /**
     * @param references may be null
     */
    private String getContentHashRaw(Handle handle, String groupId, String artifactId, boolean canonical,
            TypedContent content, List<ArtifactReferenceDto> references) {
        if (canonical) {
            var artifactMetaData = getArtifactMetaDataRaw(handle, groupId, artifactId);
            Function<List<ArtifactReferenceDto>, Map<String, TypedContent>> referenceResolver = (refs) -> {
                return resolveReferencesRaw(handle, refs);
            };
            return utils.getCanonicalContentHash(content, artifactMetaData.getArtifactType(), references,
                    referenceResolver);
        } else {
            return utils.getContentHash(content, references);
        }
    }

    /**
     * @param references may be null
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaDataByContent(String groupId, String artifactId,
            boolean canonical, TypedContent content, List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, RegistryStorageException {

        return handles.withHandle(handle -> {
            String hash = getContentHashRaw(handle, groupId, artifactId, canonical, content, references);

            String sql;
            if (canonical) {
                sql = sqlStatements.selectArtifactVersionMetaDataByCanonicalHash();
            } else {
                sql = sqlStatements.selectArtifactVersionMetaDataByContentHash();
            }
            Optional<ArtifactVersionMetaDataDto> res = handle.createQuery(sql)
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, hash)
                    .map(ArtifactVersionMetaDataDtoMapper.instance).findFirst();
            return res.orElseThrow(() -> new ArtifactNotFoundException(groupId, artifactId));
        });
    }

    @Override
    public void updateArtifactMetaData(String groupId, String artifactId,
            EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        artifactRepository.updateArtifactMetaData(groupId, artifactId, metaData);
    }

    @Override
    public List<RuleType> getArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return ruleRepository.getArtifactRules(groupId, artifactId);
    }

    @Override
    public List<RuleType> getGroupRules(String groupId) throws RegistryStorageException {
        return ruleRepository.getGroupRules(groupId);
    }

    @Override
    public void createArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        ruleRepository.createArtifactRule(groupId, artifactId, rule, config);
    }

    @Override
    public void createGroupRule(String groupId, RuleType rule, RuleConfigurationDto config)
            throws RegistryStorageException {
        ruleRepository.createGroupRule(groupId, rule, config);
    }

    @Override
    public void deleteArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        ruleRepository.deleteArtifactRules(groupId, artifactId);
    }

    @Override
    public void deleteGroupRules(String groupId) throws RegistryStorageException {
        ruleRepository.deleteGroupRules(groupId);
    }

    @Override
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        return ruleRepository.getArtifactRule(groupId, artifactId, rule);
    }

    @Override
    public RuleConfigurationDto getGroupRule(String groupId, RuleType rule) throws RegistryStorageException {
        return ruleRepository.getGroupRule(groupId, rule);
    }

    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        ruleRepository.updateArtifactRule(groupId, artifactId, rule, config);
    }

    @Override
    public void updateGroupRule(String groupId, RuleType rule, RuleConfigurationDto config)
            throws RegistryStorageException {
        ruleRepository.updateGroupRule(groupId, rule, config);
    }

    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        ruleRepository.deleteArtifactRule(groupId, artifactId, rule);
    }

    @Override
    public void deleteGroupRule(String groupId, RuleType rule) throws RegistryStorageException {
        ruleRepository.deleteGroupRule(groupId, rule);
    }

    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return getArtifactVersions(groupId, artifactId,
                storageBehaviorProps.getDefaultArtifactRetrievalBehavior());
    }

    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId, Set<VersionState> filterBy)
            throws ArtifactNotFoundException, RegistryStorageException {
        return versionRepository.getArtifactVersions(groupId, artifactId, filterBy);
    }

    private List<String> getArtifactVersionsRaw(Handle handle, String groupId, String artifactId,
            String sqlStatement) throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting a list of versions for artifact: {} {}", groupId, artifactId);
        List<String> versions = handle.createQuery(sqlStatement).bind(0, normalizeGroupId(groupId))
                .bind(1, artifactId).map(StringMapper.instance).list();

        // If there aren't any versions, it might be because the artifact does not exist
        if (versions.isEmpty()) {
            if (!isArtifactExistsRaw(handle, groupId, artifactId)) {
                throw new ArtifactNotFoundException(groupId, artifactId);
            }
        }
        return versions;
    }

    @Override
    public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit) throws RegistryStorageException {
        return searchRepository.searchVersions(filters, orderBy, orderDirection, offset, limit);
    }

    @Override
    public StoredArtifactVersionDto getArtifactVersionContent(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact version by globalId: {}", globalId);
        return handles.withHandle(handle -> {
            Optional<StoredArtifactVersionDto> res = handle
                    .createQuery(sqlStatements.selectArtifactVersionContentByGlobalId()).bind(0, globalId)
                    .map(StoredArtifactMapper.instance).findOne();
            return res.orElseThrow(() -> new ArtifactNotFoundException(null, "gid-" + globalId));
        });
    }

    @Override
    public StoredArtifactVersionDto getArtifactVersionContent(String groupId, String artifactId,
            String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact version by artifactId: {} {} and version {}", groupId,
                artifactId, version);
        return handles.withHandle(handle -> {
            Optional<StoredArtifactVersionDto> res = handle
                    .createQuery(sqlStatements.selectArtifactVersionContent())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version)
                    .map(StoredArtifactMapper.instance).findOne();
            return res.orElseThrow(() -> new ArtifactNotFoundException(groupId, artifactId));
        });
    }

    @Override
    public void updateArtifactVersionContent(String groupId, String artifactId, String version,
            String artifactType, ContentWrapperDto content) throws RegistryStorageException {
        log.debug("Updating content for artifact version: {} {} @ {}", groupId, artifactId, version);

        // Put the new content in the DB and get the unique content ID back.
        long contentId = ensureContentAndGetId(artifactType, content, true);

        String modifiedBy = securityIdentity.getPrincipal().getName();
        Date modifiedOn = new Date();

        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.updateArtifactVersionContent())
                    .bind(0, contentId).bind(1, modifiedBy).bind(2, modifiedOn)
                    .bind(3, normalizeGroupId(groupId)).bind(4, artifactId).bind(5, version).execute();
            if (rowCount == 0) {
                throw new VersionNotFoundException(groupId, artifactId, version);
            }

            // Updating content will typically leave a row in the content table orphaned.
            deleteAllOrphanedContentRaw(handle);

            return null;
        });
    }

    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Deleting version {} of artifact {} {}", version, groupId, artifactId);

        handles.withHandle(handle -> {
            // Delete version
            int rows = handle.createUpdate(sqlStatements.deleteVersion()).bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId).bind(2, version).execute();

            if (rows == 0) {
                throw new VersionNotFoundException(groupId, artifactId, version);
            }

            if (rows > 1) {
                // How would this even happen?
                throw new UnreachableCodeException();
            }

            deleteAllOrphanedContentRaw(handle);

            outboxEvent.fire(SqlOutboxEvent.of(ArtifactVersionDeleted.of(groupId, artifactId, version)));

            return null;
        });
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(Long globalId)
            throws VersionNotFoundException, RegistryStorageException {
        return versionRepository.getArtifactVersionMetaData(globalId);
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId,
            String version) {
        return versionRepository.getArtifactVersionMetaData(groupId, artifactId, version);
    }

    public ArtifactVersionMetaDataDto getArtifactVersionMetaDataRaw(Handle handle, String groupId,
            String artifactId, String version) {
        Optional<ArtifactVersionMetaDataDto> res = handle
                .createQuery(sqlStatements.selectArtifactVersionMetaData()).bind(0, normalizeGroupId(groupId))
                .bind(1, artifactId).bind(2, version).map(ArtifactVersionMetaDataDtoMapper.instance)
                .findOne();
        return res.orElseThrow(() -> new VersionNotFoundException(groupId, artifactId, version));
    }

    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
            EditableVersionMetaDataDto editableMetadata)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        versionRepository.updateArtifactVersionMetaData(groupId, artifactId, version, editableMetadata);
    }

    @Override
    public CommentDto createArtifactVersionComment(String groupId, String artifactId, String version,
            String value) {
        return commentRepository.createArtifactVersionComment(groupId, artifactId, version, value);
    }

    @Override
    public List<CommentDto> getArtifactVersionComments(String groupId, String artifactId, String version) {
        return commentRepository.getArtifactVersionComments(groupId, artifactId, version);
    }

    @Override
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId) {
        commentRepository.deleteArtifactVersionComment(groupId, artifactId, version, commentId);
    }

    @Override
    public void updateArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId, String value) {
        commentRepository.updateArtifactVersionComment(groupId, artifactId, version, commentId, value);
    }

    @Override
    public VersionState getArtifactVersionState(String groupId, String artifactId, String version) {
        return versionRepository.getArtifactVersionState(groupId, artifactId, version);
    }

    @Override
    public void updateArtifactVersionState(String groupId, String artifactId, String version,
            VersionState newState, boolean dryRun) {
        versionRepository.updateArtifactVersionState(groupId, artifactId, version, newState, dryRun);
    }

    @Override
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return ruleRepository.getGlobalRules();
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        ruleRepository.createGlobalRule(rule, config);
    }

    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        ruleRepository.deleteGlobalRules();
    }

    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule)
            throws RuleNotFoundException, RegistryStorageException {
        return ruleRepository.getGlobalRule(rule);
    }

    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleNotFoundException, RegistryStorageException {
        ruleRepository.updateGlobalRule(rule, config);
    }

    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        ruleRepository.deleteGlobalRule(rule);
    }

    @Override
    public List<DynamicConfigPropertyDto> getConfigProperties() throws RegistryStorageException {
        return configRepository.getConfigProperties();
    }

    @Override
    public DynamicConfigPropertyDto getConfigProperty(String propertyName) throws RegistryStorageException {
        return configRepository.getConfigProperty(propertyName);
    }

    @Override
    public DynamicConfigPropertyDto getRawConfigProperty(String propertyName) {
        return configRepository.getRawConfigProperty(propertyName);
    }

    @Override
    public void setConfigProperty(DynamicConfigPropertyDto propertyDto) throws RegistryStorageException {
        configRepository.setConfigProperty(propertyDto);
    }

    @Override
    public void deleteConfigProperty(String propertyName) throws RegistryStorageException {
        configRepository.deleteConfigProperty(propertyName);
    }

    @Override
    public List<DynamicConfigPropertyDto> getStaleConfigProperties(Instant lastRefresh)
            throws RegistryStorageException {
        return configRepository.getStaleConfigProperties(lastRefresh);
    }

    private void ensureGroup(GroupMetaDataDto group) {
        try {
            createGroup(group);
        } catch (GroupAlreadyExistsException e) {
            // This is OK - we're happy if the group already exists.
        }
    }

    /**
     * @see RegistryStorage#createGroup(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override
    public void createGroup(GroupMetaDataDto group)
            throws GroupAlreadyExistsException, RegistryStorageException {
        groupRepository.createGroup(group);
    }

    /**
     * Deletes a group and all artifacts in that group.
     *
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGroup(java.lang.String)
     */
    @Override
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        groupRepository.deleteGroup(groupId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateGroupMetaData(java.lang.String,
     *      io.apicurio.registry.storage.dto.EditableGroupMetaDataDto)
     */
    @Override
    public void updateGroupMetaData(String groupId, EditableGroupMetaDataDto dto) {
        groupRepository.updateGroupMetaData(groupId, dto);
    }

    @Override
    public List<String> getGroupIds(Integer limit) throws RegistryStorageException {
        return groupRepository.getGroupIds(limit);
    }

    @Override
    public GroupMetaDataDto getGroupMetaData(String groupId)
            throws GroupNotFoundException, RegistryStorageException {
        return groupRepository.getGroupMetaData(groupId);
    }

    /**
     * Called to export all data in the Registry out to e.g. a .zip file.  Called by the
     * DataExporter class when exporting all Registry data.
     */
    @Override
    public void exportData(Function<Entity, Void> handler) throws RegistryStorageException {
        // Export a simple manifest file
        ManifestEntity manifest = new ManifestEntity();
        if (securityIdentity != null && securityIdentity.getPrincipal() != null) {
            manifest.exportedBy = securityIdentity.getPrincipal().getName();
        }
        manifest.systemName = system.getName();
        manifest.systemDescription = system.getDescription();
        manifest.systemVersion = system.getVersion();
        manifest.dbVersion = "" + DB_VERSION;
        handler.apply(manifest);

        // Delegate all export operations to the export repository
        exportRepository.exportContent(handler);
        exportRepository.exportGroups(handler);
        exportRepository.exportGroupRules(handler);
        exportRepository.exportArtifacts(handler);
        exportRepository.exportArtifactVersions(handler);
        exportRepository.exportVersionComments(handler);
        exportRepository.exportBranches(handler);
        exportRepository.exportArtifactRules(handler);
        exportRepository.exportGlobalRules(handler);
    }

    @Override
    public void importData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId) {
        DataImporter dataImporter = new SqlDataImporter(log, utils, this, preserveGlobalId,
                preserveContentId);
        dataImporter.importData(entities, () -> {
        });
    }

    @Override
    public void upgradeData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId) {
        DataImporter dataImporter = new SqlDataUpgrader(log, utils, this, preserveGlobalId,
                preserveContentId);
        dataImporter.importData(entities, () -> {
        });
    }

    @Override
    public long countArtifacts() throws RegistryStorageException {
        return artifactRepository.countArtifacts();
    }

    @Override
    public long countArtifactVersions(String groupId, String artifactId) throws RegistryStorageException {
        return handles.withHandle(handle -> {
            if (!isArtifactExistsRaw(handle, groupId, artifactId)) {
                throw new ArtifactNotFoundException(groupId, artifactId);
            }
            return countArtifactVersionsRaw(handle, groupId, artifactId);
        });
    }

    private long countArtifactVersionsRaw(Handle handle, String groupId, String artifactId)
            throws RegistryStorageException {
        return handle.createQuery(sqlStatements.selectAllArtifactVersionsCount())
                .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).mapTo(Long.class).one();
    }

    @Override
    public long countTotalArtifactVersions() throws RegistryStorageException {
        return versionRepository.countTotalArtifactVersions();
    }

    @Override
    public void createRoleMapping(String principalId, String role, String principalName)
            throws RegistryStorageException {
        roleMappingRepository.createRoleMapping(principalId, role, principalName);
    }

    @Override
    public void deleteRoleMapping(String principalId) throws RegistryStorageException {
        roleMappingRepository.deleteRoleMapping(principalId);
    }

    @Override
    public RoleMappingDto getRoleMapping(String principalId) throws RegistryStorageException {
        return roleMappingRepository.getRoleMapping(principalId);
    }

    @Override
    public String getRoleForPrincipal(String principalId) throws RegistryStorageException {
        return roleMappingRepository.getRoleForPrincipal(principalId);
    }

    @Override
    public List<RoleMappingDto> getRoleMappings() throws RegistryStorageException {
        return roleMappingRepository.getRoleMappings();
    }

    @Override
    public RoleMappingSearchResultsDto searchRoleMappings(int offset, int limit)
            throws RegistryStorageException {
        return roleMappingRepository.searchRoleMappings(offset, limit);
    }

    @Override
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException {
        roleMappingRepository.updateRoleMapping(principalId, role);
    }

    @Override
    public String createDownload(DownloadContextDto context) throws RegistryStorageException {
        return downloadRepository.createDownload(context);
    }

    @Override
    public DownloadContextDto consumeDownload(String downloadId) throws RegistryStorageException {
        return downloadRepository.consumeDownload(downloadId);
    }

    @Override
    public void deleteAllExpiredDownloads() throws RegistryStorageException {
        downloadRepository.deleteAllExpiredDownloads();
    }

    @Override
    public void deleteAllUserData() {
        log.debug("Deleting all user data");

        deleteGlobalRules();

        handles.withHandleNoException(handle -> {
            // Delete all artifacts and related data

            handle.createUpdate(sqlStatements.deleteAllContentReferences()).execute();

            handle.createUpdate(sqlStatements.deleteVersionLabelsByAll()).execute();

            handle.createUpdate(sqlStatements.deleteAllVersionComments()).execute();

            handle.createUpdate(sqlStatements.deleteAllBranchVersions()).execute();

            handle.createUpdate(sqlStatements.deleteAllBranches()).execute();

            handle.createUpdate(sqlStatements.deleteAllVersions()).execute();

            handle.createUpdate(sqlStatements.deleteAllArtifactRules()).execute();

            handle.createUpdate(sqlStatements.deleteAllArtifacts()).execute();

            // Delete all groups
            handle.createUpdate(sqlStatements.deleteAllGroups()).execute();

            // Delete all role mappings
            handle.createUpdate(sqlStatements.deleteAllRoleMappings()).execute();

            // Delete all content
            handle.createUpdate(sqlStatements.deleteAllContent()).execute();

            // Delete all config properties
            handle.createUpdate(sqlStatements.deleteAllConfigProperties()).execute();

            // TODO Do we need to delete comments?

            return null;
        });

    }

    private Map<String, TypedContent> resolveReferencesRaw(Handle handle,
            List<ArtifactReferenceDto> references) {
        return contentRepository.resolveReferencesRaw(handle, references);
    }

    @Override
    public boolean isArtifactExists(String groupId, String artifactId) throws RegistryStorageException {
        return artifactRepository.isArtifactExists(groupId, artifactId);
    }

    private boolean isArtifactExistsRaw(Handle handle, String groupId, String artifactId)
            throws RegistryStorageException {
        return handle.createQuery(sqlStatements().selectArtifactCountById())
                .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).mapTo(Integer.class).one() > 0;
    }

    @Override
    public boolean isGroupExists(String groupId) throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return isGroupExistsRaw(handle, groupId);
        });
    }

    private boolean isGroupExistsRaw(Handle handle, String groupId) throws RegistryStorageException {
        return handle.createQuery(sqlStatements().selectGroupCountById()).bind(0, normalizeGroupId(groupId))
                .mapTo(Integer.class).one() > 0;
    }

    @Override
    public List<Long> getContentIdsReferencingArtifactVersion(String groupId, String artifactId,
            String version) {
        return contentRepository.getContentIdsReferencingArtifactVersion(groupId, artifactId, version);
    }

    @Override
    public List<Long> getGlobalIdsReferencingArtifactVersion(String groupId, String artifactId,
            String version) {
        return contentRepository.getGlobalIdsReferencingArtifactVersion(groupId, artifactId, version);
    }

    @Override
    public List<Long> getGlobalIdsReferencingArtifact(String groupId, String artifactId) {
        return contentRepository.getGlobalIdsReferencingArtifact(groupId, artifactId);
    }

    @Override
    public List<ArtifactReferenceDto> getInboundArtifactReferences(String groupId, String artifactId,
            String version) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectInboundContentReferencesByGAV())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version)
                    .map(ArtifactReferenceDtoMapper.instance).list();
        });
    }

    @Override
    public boolean isArtifactVersionExists(String groupId, String artifactId, String version)
            throws RegistryStorageException {
        try {
            getArtifactVersionMetaData(groupId, artifactId, version);
            return true;
        } catch (VersionNotFoundException ignored) {
            return false; // TODO Similar exception is thrown in some method callers, do we need this? Or use
            // a different query.
        }
    }

    @Override
    public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, Integer offset, Integer limit) {
        return groupRepository.searchGroups(filters, orderBy, orderDirection, offset, limit);
    }

    @Override
    public ContentWrapperDto getContentByReference(ArtifactReferenceDto reference) {
        return contentRepository.getContentByReference(reference);
    }

    // TODO call this in a cleanup cron job instead?
    private void deleteAllOrphanedContentRaw(Handle handle) {
        contentRepository.deleteAllOrphanedContentRaw(handle);
    }

    private long getMaxGlobalIdRaw(Handle handle) {
        return getMaxIdRaw(handle, sqlStatements.selectMaxGlobalId());
    }

    private long getMaxContentIdRaw(Handle handle) {
        return getMaxIdRaw(handle, sqlStatements.selectMaxContentId());
    }

    private long getMaxVersionCommentIdRaw(Handle handle) {
        return getMaxIdRaw(handle, sqlStatements.selectMaxVersionCommentId());
    }

    private long getMaxIdRaw(Handle handle, String sql) {
        Optional<Long> maxIdTable = handle.createQuery(sql).mapTo(Long.class).findOne();
        return maxIdTable.orElse(1L);
    }

    @Override
    public void resetGlobalId() {
        sequenceRepository.resetGlobalId();
    }

    @Override
    public void resetContentId() {
        sequenceRepository.resetContentId();
    }

    @Override
    public void resetCommentId() {
        sequenceRepository.resetCommentId();
    }

    @Override
    public void importGroupRule(GroupRuleEntity entity) {
        ruleRepository.importGroupRule(entity);
    }

    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) {
        ruleRepository.importArtifactRule(entity);
    }

    @Override
    public void importArtifact(ArtifactEntity entity) {
        artifactRepository.importArtifact(entity);
    }

    @Override
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        versionRepository.importArtifactVersion(entity);
    }

    @Override
    public void importContent(ContentEntity entity) {
        contentRepository.importContent(entity);
    }

    @Override
    public void importGlobalRule(GlobalRuleEntity entity) {
        ruleRepository.importGlobalRule(entity);
    }

    @Override
    public void importGroup(GroupEntity entity) {
        groupRepository.importGroup(entity);
    }

    @Override
    public void importComment(CommentEntity entity) {
        commentRepository.importCommentRaw(entity);
    }

    @Override
    public boolean isEmpty() {
        return handles.withHandle(handle -> {
            return handle.createQuery(sqlStatements.selectAllContentCount()).mapTo(Long.class).one() == 0;
        });
    }

    private boolean isContentExistsRaw(Handle handle, long contentId) {
        return contentRepository.isContentExistsRaw(handle, contentId);
    }

    private boolean isGlobalIdExistsRaw(Handle handle, long globalId) {
        return handle.createQuery(sqlStatements().selectGlobalIdExists()).bind(0, globalId)
                .mapTo(Integer.class).one() > 0;
    }

    @Override
    public long nextContentId() {
        return sequenceRepository.nextContentId();
    }

    private long nextContentIdRaw(Handle handle) {
        return sequenceRepository.nextContentIdRaw(handle);
    }

    @Override
    public long nextGlobalId() {
        return sequenceRepository.nextGlobalId();
    }

    private long nextGlobalIdRaw(Handle handle) {
        return sequenceRepository.nextGlobalIdRaw(handle);
    }

    @Override
    public long nextCommentId() {
        return sequenceRepository.nextCommentId();
    }

    private long nextCommentIdRaw(Handle handle) {
        return sequenceRepository.nextCommentIdRaw(handle);
    }

    @Override
    public boolean isContentExists(String contentHash) throws RegistryStorageException {
        return contentRepository.isContentExists(contentHash);
    }

    @Override
    public boolean isArtifactRuleExists(String groupId, String artifactId, RuleType rule)
            throws RegistryStorageException {
        return ruleRepository.isArtifactRuleExists(groupId, artifactId, rule);
    }

    @Override
    public boolean isGlobalRuleExists(RuleType rule) throws RegistryStorageException {
        return ruleRepository.isGlobalRuleExists(rule);
    }

    @Override
    public boolean isRoleMappingExists(String principalId) {
        return roleMappingRepository.isRoleMappingExists(principalId);
    }

    @Override
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) {
        contentRepository.updateContentCanonicalHash(newCanonicalHash, contentId, contentHash);
    }

    @Override
    public Optional<Long> contentIdFromHash(String contentHash) {
        return contentRepository.contentIdFromHash(contentHash);
    }

    private Optional<Long> contentIdFromHashRaw(Handle handle, String contentHash) {
        return contentRepository.contentIdFromHashRaw(handle, contentHash);
    }

    @Override
    public BranchMetaDataDto createBranch(GA ga, BranchId branchId, String description,
            List<String> versions) {
        return branchRepository.createBranch(ga, branchId, description, versions);
    }

    @Override
    public void updateBranchMetaData(GA ga, BranchId branchId, EditableBranchMetaDataDto dto) {
        branchRepository.updateBranchMetaData(ga, branchId, dto);
    }

    @Override
    public BranchSearchResultsDto getBranches(GA ga, int offset, int limit) {
        // Check artifact exists first
        getArtifactMetaData(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());
        return branchRepository.getBranches(ga, offset, limit);
    }

    @Override
    public BranchMetaDataDto getBranchMetaData(GA ga, BranchId branchId) {
        return branchRepository.getBranchMetaData(ga, branchId);
    }

    private List<String> getBranchVersionNumbersRaw(Handle handle, GA ga, BranchId branchId) {
        return branchRepository.getBranchVersionNumbersRaw(handle, ga, branchId);
    }

    @Override
    public VersionSearchResultsDto getBranchVersions(GA ga, BranchId branchId, int offset, int limit) {
        VersionSearchResultsDto results = branchRepository.getBranchVersions(ga, branchId, offset, limit);
        limitReturnedLabelsInVersions(results.getVersions());
        return results;
    }

    @Override
    public void appendVersionToBranch(GA ga, BranchId branchId, VersionId version) {
        branchRepository.appendVersionToBranch(ga, branchId, version);
    }

    private void appendVersionToBranchRaw(Handle handle, GA ga, BranchId branchId, VersionId version) {
        branchRepository.appendVersionToBranchRaw(handle, ga, branchId, version);
    }

    @Override
    public void replaceBranchVersions(GA ga, BranchId branchId, List<VersionId> versions) {
        branchRepository.replaceBranchVersions(ga, branchId, versions);
    }

    private void createOrUpdateBranchRaw(Handle handle, GAV gav, BranchId branchId, boolean systemDefined) {
        branchRepository.createOrUpdateBranchRaw(handle, gav, branchId, systemDefined);
    }

    private void removeVersionFromBranchRaw(Handle handle, GAV gav, BranchId branchId) {
        branchRepository.removeVersionFromBranchRaw(handle, gav, branchId);
    }

    private void updateBranchModifiedTimeRaw(Handle handle, GA ga, BranchId branchId) {
        branchRepository.updateBranchModifiedTimeRaw(handle, ga, branchId);
    }

    @Override
    public GAV getBranchTip(GA ga, BranchId branchId, Set<VersionState> filterBy) {
        return branchRepository.getBranchTip(ga, branchId, filterBy);
    }

    private GAV getGAVByGlobalIdRaw(Handle handle, long globalId) {
        return handle.createQuery(sqlStatements.selectGAVByGlobalId()).bind(0, globalId)
                .map(GAVMapper.instance).findOne().orElseThrow(() -> new VersionNotFoundException(globalId));
    }

    @Override
    public void deleteBranch(GA ga, BranchId branchId) {
        branchRepository.deleteBranch(ga, branchId);
    }

    @Override
    public void importBranch(BranchEntity entity) {
        branchRepository.importBranch(entity);
    }

    @Override
    public String triggerSnapshotCreation() throws RegistryStorageException {
        throw new RegistryStorageException(
                "Directly triggering the snapshot creation is not supported for sql storages.");
    }

    @Override
    public String createSnapshot(String location) throws RegistryStorageException {
        return exportRepository.createSnapshot(location);
    }

    @Override
    public String createEvent(OutboxEvent event) {
        if (supportsDatabaseEvents()) {
            // Create outbox event
            handles.withHandle(handle -> {
                handle.createUpdate(sqlStatements.createOutboxEvent()).bind(0, event.getId())
                        .bind(1, eventsTopic).bind(2, event.getAggregateId()).bind(3, event.getType())
                        .bind(4, event.getPayload().toString()).execute();

                return handle.createUpdate(sqlStatements.deleteOutboxEvent()).bind(0, event.getId())
                        .execute();
            });
        }
        return event.getId();
    }

    @Override
    public boolean supportsDatabaseEvents() {
        return isPostgresql() || isMssql();
    }

    private boolean isPostgresql() {
        return sqlStatements.dbType().equals("postgresql");
    }

    private boolean isMssql() {
        return sqlStatements.dbType().equals("mssql");
    }

    private boolean isH2() {
        return sqlStatements.dbType().equals("h2");
    }

    private boolean isMysql() {
        return sqlStatements.dbType().equals("mysql");
    }

    /*
     * Ensures that only a reasonable number/size of labels for each item in the list are returned. This is to
     * guard against an unexpectedly enormous response size to a REST API search operation.
     */

    private Map<String, String> limitReturnedLabels(Map<String, String> labels) {
        int maxBytes = restConfig.getLabelsInSearchResultsMaxSize();
        if (labels != null && !labels.isEmpty()) {
            Map<String, String> cappedLabels = new HashMap<>();
            int totalBytes = 0;
            for (String key : labels.keySet()) {
                if (totalBytes < maxBytes) {
                    String value = labels.get(key);
                    cappedLabels.put(key, value);
                    totalBytes += key.length() + (value != null ? value.length() : 0);
                }
            }
            return cappedLabels;
        }

        return null;
    }

    private void limitReturnedLabelsInGroups(List<SearchedGroupDto> groups) {
        groups.forEach(group -> {
            Map<String, String> labels = group.getLabels();
            Map<String, String> cappedLabels = limitReturnedLabels(labels);
            group.setLabels(cappedLabels);
        });
    }

    private void limitReturnedLabelsInArtifacts(List<SearchedArtifactDto> artifacts) {
        artifacts.forEach(artifact -> {
            Map<String, String> labels = artifact.getLabels();
            Map<String, String> cappedLabels = limitReturnedLabels(labels);
            artifact.setLabels(cappedLabels);
        });
    }

    private void limitReturnedLabelsInVersions(List<SearchedVersionDto> versions) {
        versions.forEach(version -> {
            Map<String, String> labels = version.getLabels();
            Map<String, String> cappedLabels = limitReturnedLabels(labels);
            version.setLabels(cappedLabels);
        });
    }

}
