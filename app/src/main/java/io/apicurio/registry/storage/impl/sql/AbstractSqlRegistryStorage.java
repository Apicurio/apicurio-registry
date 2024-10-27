package io.apicurio.registry.storage.impl.sql;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.Info;
import io.apicurio.common.apps.core.System;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.events.ArtifactCreated;
import io.apicurio.registry.events.ArtifactDeleted;
import io.apicurio.registry.events.ArtifactMetadataUpdated;
import io.apicurio.registry.events.ArtifactRuleConfigured;
import io.apicurio.registry.events.ArtifactVersionCreated;
import io.apicurio.registry.events.ArtifactVersionDeleted;
import io.apicurio.registry.events.ArtifactVersionMetadataUpdated;
import io.apicurio.registry.events.ArtifactVersionStateChanged;
import io.apicurio.registry.events.GlobalRuleConfigured;
import io.apicurio.registry.events.GroupCreated;
import io.apicurio.registry.events.GroupDeleted;
import io.apicurio.registry.events.GroupMetadataUpdated;
import io.apicurio.registry.events.GroupRuleConfigured;
import io.apicurio.registry.exception.UnreachableCodeException;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
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
import io.apicurio.registry.storage.dto.SearchedBranchDto;
import io.apicurio.registry.storage.dto.SearchedGroupDto;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.BranchAlreadyExistsException;
import io.apicurio.registry.storage.error.BranchNotFoundException;
import io.apicurio.registry.storage.error.CommentNotFoundException;
import io.apicurio.registry.storage.error.ContentAlreadyExistsException;
import io.apicurio.registry.storage.error.ContentNotFoundException;
import io.apicurio.registry.storage.error.DownloadNotFoundException;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.NotAllowedException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.RoleMappingAlreadyExistsException;
import io.apicurio.registry.storage.error.RoleMappingNotFoundException;
import io.apicurio.registry.storage.error.RuleAlreadyExistsException;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsOnBranchException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.Query;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactReferenceDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactRuleEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactVersionEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactVersionMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.BranchEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.BranchMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.CommentDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.CommentEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ContentEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ContentMapper;
import io.apicurio.registry.storage.impl.sql.mappers.DynamicConfigPropertyDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GAVMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GlobalRuleEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GroupEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GroupMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GroupRuleEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.RoleMappingDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.RuleConfigurationDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedArtifactMapper;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedBranchMapper;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedGroupMapper;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedVersionMapper;
import io.apicurio.registry.storage.impl.sql.mappers.StoredArtifactMapper;
import io.apicurio.registry.storage.impl.sql.mappers.StringMapper;
import io.apicurio.registry.storage.impl.sql.mappers.VersionStateMapper;
import io.apicurio.registry.storage.importing.DataImporter;
import io.apicurio.registry.storage.importing.v2.SqlDataUpgrader;
import io.apicurio.registry.storage.importing.v3.SqlDataImporter;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.DtoUtil;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.StringUtil;
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
import jakarta.transaction.Transactional;
import jakarta.validation.ValidationException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.semver4j.Semver;
import org.slf4j.Logger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;
import static io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils.notEmpty;
import static io.apicurio.registry.utils.StringUtil.asLowerCase;
import static io.apicurio.registry.utils.StringUtil.limitStr;
import static java.util.stream.Collectors.toList;

/**
 * A SQL implementation of the {@link RegistryStorage} interface. This impl does not use any ORM technology -
 * it simply uses native SQL for all operations.
 */
public abstract class AbstractSqlRegistryStorage implements RegistryStorage {

    private static int DB_VERSION = Integer
            .valueOf(IoUtil.toString(AbstractSqlRegistryStorage.class.getResourceAsStream("db-version")))
            .intValue();
    private static final Object inmemorySequencesMutex = new Object();

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, true);
    }

    private static final String GLOBAL_ID_SEQUENCE = "globalId";
    private static final String CONTENT_ID_SEQUENCE = "contentId";
    private static final String COMMENT_ID_SEQUENCE = "commentId";

    // Sequence counters ** Note: only used for H2 in-memory (and as a result KafkaSQL) **
    private static final Map<String, AtomicLong> sequenceCounters = new HashMap<>();
    static {
        sequenceCounters.put(GLOBAL_ID_SEQUENCE, new AtomicLong(0));
        sequenceCounters.put(CONTENT_ID_SEQUENCE, new AtomicLong(0));
        sequenceCounters.put(COMMENT_ID_SEQUENCE, new AtomicLong(0));
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
    @Info(category = "storage", description = "SQL init", availableSince = "2.0.0.Final")
    boolean initDB;

    @Inject
    @ConfigProperty(name = "apicurio.events.kafka.topic", defaultValue = "registry-events")
    @Info(category = "storage", description = "Storage event topic")
    String eventsTopic;

    @Inject
    Event<SqlStorageEvent> sqlStorageEvent;

    @Inject
    Event<StorageEvent> storageEvent;

    @Inject
    Event<SqlOutboxEvent> outboxEvent;

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
                sequenceCounters.get(GLOBAL_ID_SEQUENCE).set(getMaxGlobalIdRaw(handle));
                sequenceCounters.get(CONTENT_ID_SEQUENCE).set(getMaxContentIdRaw(handle));
                sequenceCounters.get(COMMENT_ID_SEQUENCE).set(getMaxVersionCommentIdRaw(handle));
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
        int count = handle.createQuery(this.sqlStatements.isDatabaseInitialized()).mapTo(Integer.class).one();
        return count > 0;
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
        return handles.withHandleNoException(handle -> {
            return getContentByIdRaw(handle, contentId);
        });
    }

    public ContentWrapperDto getContentByIdRaw(Handle handle, long contentId)
            throws ContentNotFoundException, RegistryStorageException {
        Optional<ContentWrapperDto> res = handle.createQuery(sqlStatements().selectContentById())
                .bind(0, contentId).map(ContentMapper.instance).findFirst();
        return res.orElseThrow(() -> new ContentNotFoundException(contentId));
    }

    @Override
    public ContentWrapperDto getContentByHash(String contentHash)
            throws ContentNotFoundException, RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            Optional<ContentWrapperDto> res = handle.createQuery(sqlStatements().selectContentByContentHash())
                    .bind(0, contentHash).map(ContentMapper.instance).findFirst();
            return res.orElseThrow(() -> new ContentNotFoundException(contentHash));
        });
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
        return handles.withHandleNoException(handle -> {
            String sql = sqlStatements().selectArtifactContentIds();

            return handle.createQuery(sql).bind(0, normalizeGroupId(groupId)).bind(1, artifactId)
                    .mapTo(Long.class).list();
        });
    }

    @Override
    public Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> createArtifact(String groupId,
            String artifactId, String artifactType, EditableArtifactMetaDataDto artifactMetaData,
            String version, ContentWrapperDto versionContent, EditableVersionMetaDataDto versionMetaData,
            List<String> versionBranches, boolean versionIsDraft, boolean dryRun)
            throws RegistryStorageException {
        log.debug("Inserting an artifact row for: {} {}", groupId, artifactId);

        String owner = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();

        EditableArtifactMetaDataDto amd = artifactMetaData == null
            ? EditableArtifactMetaDataDto.builder().build() : artifactMetaData;

        // Create the group if it doesn't exist yet.
        if (groupId != null && !isGroupExists(groupId)) {
            // Only create group metadata for non-default groups.
            ensureGroup(GroupMetaDataDto.builder().groupId(groupId).createdOn(createdOn.getTime())
                    .modifiedOn(createdOn.getTime()).owner(owner).modifiedBy(owner).build());
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
        boolean validationEnabled = semVerConfigProps.validationEnabled.get();
        boolean branchingEnabled = semVerConfigProps.branchingEnabled.get();
        boolean coerceInvalidVersions = semVerConfigProps.coerceInvalidVersions.get();

        // Validate the version if validation is enabled.
        if (validationEnabled) {
            Semver semver = Semver.parse(gav.getRawVersionId());
            if (semver == null) {
                throw new ValidationException("Version '" + gav.getRawVersionId()
                        + "' does not conform to Semantic Versioning 2 format.");
            }
        }

        // Create branches if branching is enabled
        if (!branchingEnabled) {
            return;
        }

        Semver semver = null;
        if (coerceInvalidVersions) {
            semver = Semver.coerce(gav.getRawVersionId());
            if (semver == null) {
                throw new ValidationException("Version '" + gav.getRawVersionId()
                        + "' cannot be coerced to Semantic Versioning 2 format.");
            }
        } else {
            semver = Semver.parse(gav.getRawVersionId());
            if (semver == null) {
                throw new ValidationException("Version '" + gav.getRawVersionId()
                        + "' does not conform to Semantic Versioning 2 format.");
            }
        }
        if (semver == null) {
            throw new UnreachableCodeException("Unexpectedly reached unreachable code!");
        }
        createOrUpdateBranchRaw(handle, gav, new BranchId(semver.getMajor() + ".x"), true);
        createOrUpdateBranchRaw(handle, gav, new BranchId(semver.getMajor() + "." + semver.getMinor() + ".x"),
                true);
    }

    /**
     * Make sure the content exists in the database (try to insert it). Regardless of whether it already
     * existed or not, return the contentId of the content in the DB.
     */
    private Long ensureContentAndGetId(String artifactType, ContentWrapperDto contentDto, boolean isDraft) {
        List<ArtifactReferenceDto> references = contentDto.getReferences();
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
            byte[] contentBytes = content.getContent().bytes();

            // Insert the content into the content table.
            String sql = sqlStatements.insertContent();
            long contentId = nextContentIdRaw(handle);

            try {
                handle.createUpdate(sql).bind(0, contentId).bind(1, canonicalContentHash).bind(2, contentHash)
                        .bind(3, content.getContentType()).bind(4, contentBytes).bind(5, referencesSerialized)
                        .execute();
            } catch (Exception e) {
                if (sqlStatements.isPrimaryKeyViolation(e)) {
                    return;
                } else {
                    throw e;
                }
            }

            // If we get here, then the content was inserted and we need to insert the references.
            insertReferencesRaw(handle, contentId, references);
        });
    }

    private void insertReferencesRaw(Handle handle, Long contentId, List<ArtifactReferenceDto> references) {
        if (references != null && !references.isEmpty()) {
            references.forEach(reference -> {
                try {
                    handle.createUpdate(sqlStatements.insertContentReference()).bind(0, contentId)
                            .bind(1, normalizeGroupId(reference.getGroupId()))
                            .bind(2, reference.getArtifactId()).bind(3, reference.getVersion())
                            .bind(4, reference.getName()).execute();
                } catch (Exception e) {
                    if (sqlStatements.isPrimaryKeyViolation(e)) {
                        // Do nothing, the reference already exist, only needed for H2
                    } else {
                        throw e;
                    }
                }
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
            List<String> branches, boolean isDraft, boolean dryRun)
            throws VersionAlreadyExistsException, RegistryStorageException {
        log.debug("Creating new artifact version for {} {} (version {}).", groupId, artifactId, version);

        String owner = securityIdentity.getPrincipal().getName();
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
        // Set limit to max integer in case limit is null (not allowed)
        final Integer adjustedLimit = limit == null ? Integer.MAX_VALUE : limit;
        log.debug("Getting the set of all artifact IDs");
        return handles.withHandleNoException(handle -> {
            Query query = handle.createQuery(sqlStatements.selectArtifactIds());
            query.bind(0, adjustedLimit);
            return new HashSet<>(query.mapTo(String.class).list());
        });
    }

    @Override
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit) {
        return handles.withHandleNoException(handle -> {
            List<SqlStatementVariableBinder> binders = new LinkedList<>();

            StringBuilder selectTemplate = new StringBuilder();
            StringBuilder where = new StringBuilder();
            StringBuilder orderByQuery = new StringBuilder();
            StringBuilder limitOffset = new StringBuilder();

            // Formulate the SELECT clause for the artifacts query
            selectTemplate.append("SELECT {{selectColumns}} FROM artifacts a ");

            // Formulate the WHERE clause for both queries
            String op;
            boolean first = true;
            for (SearchFilter filter : filters) {
                if (first) {
                    where.append(" WHERE (");
                    first = false;
                } else {
                    where.append(" AND (");
                }
                switch (filter.getType()) {
                    case description:
                        op = filter.isNot() ? "NOT LIKE" : "LIKE";
                        where.append("a.description ");
                        where.append(op);
                        where.append(" ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        break;
                    case name:
                        op = filter.isNot() ? "NOT LIKE" : "LIKE";
                        where.append("a.name " + op + " ? OR a.artifactId " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        break;
                    case groupId:
                        op = filter.isNot() ? "!=" : "=";
                        where.append("a.groupId " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, normalizeGroupId(filter.getStringValue()));
                        });
                        break;
                    case contentHash:
                        op = filter.isNot() ? "!=" : "=";
                        where.append(
                                "EXISTS(SELECT c.* FROM content c JOIN versions v ON c.contentId = v.contentId WHERE v.groupId = a.groupId AND v.artifactId = a.artifactId AND ");
                        where.append("c.contentHash " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getStringValue());
                        });
                        where.append(")");
                        break;
                    case canonicalHash:
                        op = filter.isNot() ? "!=" : "=";
                        where.append(
                                "EXISTS(SELECT c.* FROM content c JOIN versions v ON c.contentId = v.contentId WHERE v.groupId = a.groupId AND v.artifactId = a.artifactId AND ");
                        where.append("c.canonicalHash " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getStringValue());
                        });
                        where.append(")");
                        break;
                    case labels:
                        op = filter.isNot() ? "!=" : "=";
                        Pair<String, String> label = filter.getLabelFilterValue();
                        // Note: convert search to lowercase when searching for labels (case-insensitivity
                        // support).
                        String labelKey = label.getKey().toLowerCase();
                        where.append(
                                "EXISTS(SELECT l.* FROM artifact_labels l WHERE l.labelKey " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, labelKey);
                        });
                        if (label.getValue() != null) {
                            String labelValue = label.getValue().toLowerCase();
                            where.append(" AND l.labelValue " + op + " ?");
                            binders.add((query, idx) -> {
                                query.bind(idx, labelValue);
                            });
                        }
                        where.append(" AND l.groupId = a.groupId AND l.artifactId = a.artifactId)");
                        break;
                    case globalId:
                        op = filter.isNot() ? "!=" : "=";
                        where.append(
                                "EXISTS(SELECT v.* FROM versions v WHERE v.groupId = a.groupId AND v.artifactId = a.artifactId AND ");
                        where.append("v.globalId " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getNumberValue().longValue());
                        });
                        where.append(")");
                        break;
                    case contentId:
                        op = filter.isNot() ? "!=" : "=";
                        where.append(
                                "EXISTS(SELECT c.* FROM content c JOIN versions v ON c.contentId = v.contentId WHERE v.groupId = a.groupId AND v.artifactId = a.artifactId AND ");
                        where.append("v.contentId " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getNumberValue().longValue());
                        });
                        where.append(")");
                        break;
                    case state:
                        op = filter.isNot() ? "!=" : "=";
                        where.append(
                                "EXISTS(SELECT v.* FROM versions v WHERE v.groupId = a.groupId AND v.artifactId = a.artifactId AND ");
                        where.append("v.state " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getStringValue());
                        });
                        where.append(")");
                        break;
                }
                where.append(")");
            }

            // Add order by to artifact query
            switch (orderBy) {
                case name:
                    orderByQuery.append(" ORDER BY coalesce(a.name, a.artifactId)");
                    break;
                case artifactType:
                    orderByQuery.append(" ORDER BY a.type");
                    break;
                case groupId:
                case artifactId:
                case createdOn:
                case modifiedOn:
                    orderByQuery.append(" ORDER BY a." + orderBy.name());
                    break;
                default:
                    throw new RuntimeException("Sort by " + orderBy.name() + " not supported.");
            }
            orderByQuery.append(" ").append(orderDirection.name());

            // Add limit and offset to artifact query
            if ("mssql".equals(sqlStatements.dbType())) {
                limitOffset.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            } else {
                limitOffset.append(" LIMIT ? OFFSET ?");
            }

            // Query for the artifacts
            String artifactsQuerySql = new StringBuilder(selectTemplate).append(where).append(orderByQuery)
                    .append(limitOffset).toString().replace("{{selectColumns}}", "a.*");
            Query artifactsQuery = handle.createQuery(artifactsQuerySql);
            String countQuerySql = new StringBuilder(selectTemplate).append(where).toString()
                    .replace("{{selectColumns}}", "count(a.artifactId)");
            Query countQuery = handle.createQuery(countQuerySql);

            // Bind all query parameters
            int idx = 0;
            for (SqlStatementVariableBinder binder : binders) {
                binder.bind(artifactsQuery, idx);
                binder.bind(countQuery, idx);
                idx++;
            }
            // TODO find a better way to swap arguments
            if ("mssql".equals(sqlStatements.dbType())) {
                artifactsQuery.bind(idx++, offset);
                artifactsQuery.bind(idx++, limit);
            } else {
                artifactsQuery.bind(idx++, limit);
                artifactsQuery.bind(idx++, offset);
            }

            // Execute artifact query
            List<SearchedArtifactDto> artifacts = artifactsQuery.map(SearchedArtifactMapper.instance).list();
            limitReturnedLabelsInArtifacts(artifacts);
            // Execute count query
            Integer count = countQuery.mapTo(Integer.class).one();

            ArtifactSearchResultsDto results = new ArtifactSearchResultsDto();
            results.setArtifacts(artifacts);
            results.setCount(count);
            return results;
        });
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Selecting artifact meta-data: {} {}", groupId, artifactId);

        return handles.withHandle(handle -> getArtifactMetaDataRaw(handle, groupId, artifactId));
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
        log.debug("Updating meta-data for an artifact: {} {}", groupId, artifactId);

        handles.withHandle(handle -> {
            boolean modified = false;

            // Update name
            if (metaData.getName() != null) {
                int rowCount = handle.createUpdate(sqlStatements.updateArtifactName())
                        .bind(0, limitStr(metaData.getName(), 512)).bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId).execute();
                modified = true;
                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
            }

            // Update description
            if (metaData.getDescription() != null) {
                modified = true;

                int rowCount = handle.createUpdate(sqlStatements.updateArtifactDescription())
                        .bind(0, limitStr(metaData.getDescription(), 1024)).bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId).execute();
                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
            }

            // TODO versions shouldn't have owners, only groups and artifacts?
            if (metaData.getOwner() != null && !metaData.getOwner().trim().isEmpty()) {
                modified = true;

                int rowCount = handle.createUpdate(sqlStatements.updateArtifactOwner())
                        .bind(0, metaData.getOwner()).bind(1, normalizeGroupId(groupId)).bind(2, artifactId)
                        .execute();
                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
            }

            // Update labels
            if (metaData.getLabels() != null) {
                modified = true;

                int rowCount = handle.createUpdate(sqlStatements.updateArtifactLabels())
                        .bind(0, RegistryContentUtils.serializeLabels(metaData.getLabels()))
                        .bind(1, normalizeGroupId(groupId)).bind(2, artifactId).execute();
                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }

                // Delete all appropriate rows in the "artifact_labels" table
                handle.createUpdate(sqlStatements.deleteArtifactLabels()).bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId).execute();

                // Insert new labels into the "artifact_labels" table
                Map<String, String> labels = metaData.getLabels();
                if (labels != null && !labels.isEmpty()) {
                    labels.forEach((k, v) -> {
                        String sqli = sqlStatements.insertArtifactLabel();
                        handle.createUpdate(sqli).bind(0, normalizeGroupId(groupId)).bind(1, artifactId)
                                .bind(2, limitStr(k.toLowerCase(), 256))
                                .bind(3, limitStr(asLowerCase(v), 512)).execute();
                    });
                }
            }

            if (modified) {
                String modifiedBy = securityIdentity.getPrincipal().getName();
                Date modifiedOn = new Date();

                int rowCount = handle.createUpdate(sqlStatements.updateArtifactModifiedByOn())
                        .bind(0, modifiedBy).bind(1, modifiedOn).bind(2, normalizeGroupId(groupId))
                        .bind(3, artifactId).execute();
                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                } else {
                    outboxEvent.fire(
                            SqlOutboxEvent.of(ArtifactMetadataUpdated.of(groupId, artifactId, metaData)));
                }
            }

            return null;
        });
    }

    @Override
    public List<RuleType> getArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting a list of all artifact rules for: {} {}", groupId, artifactId);
        return handles.withHandle(handle -> {
            List<RuleType> rules = handle.createQuery(sqlStatements.selectArtifactRules())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).map(new RowMapper<RuleType>() {
                        @Override
                        public RuleType map(ResultSet rs) throws SQLException {
                            return RuleType.fromValue(rs.getString("type"));
                        }
                    }).list();
            if (rules.isEmpty()) {
                if (!isArtifactExistsRaw(handle, groupId, artifactId)) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
            }
            return rules;
        });
    }

    @Override
    public List<RuleType> getGroupRules(String groupId) throws RegistryStorageException {
        log.debug("Getting a list of all group rules for: {}", groupId);
        return handles.withHandle(handle -> {
            List<RuleType> rules = handle.createQuery(sqlStatements.selectGroupRules())
                    .bind(0, normalizeGroupId(groupId)).map(new RowMapper<RuleType>() {
                        @Override
                        public RuleType map(ResultSet rs) throws SQLException {
                            return RuleType.fromValue(rs.getString("type"));
                        }
                    }).list();
            if (rules.isEmpty()) {
                if (!isGroupExistsRaw(handle, groupId)) {
                    throw new GroupNotFoundException(groupId);
                }
            }
            return rules;
        });
    }

    @Override
    public void createArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        log.debug("Inserting an artifact rule row for artifact: {} {} rule: {}", groupId, artifactId,
                rule.name());
        try {
            handles.withHandle(handle -> {
                handle.createUpdate(sqlStatements.insertArtifactRule()).bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId).bind(2, rule.name()).bind(3, config.getConfiguration())
                        .execute();

                outboxEvent.fire(
                        SqlOutboxEvent.of(ArtifactRuleConfigured.of(groupId, artifactId, rule, config)));

                return null;
            });
        } catch (Exception ex) {
            if (sqlStatements.isPrimaryKeyViolation(ex)) {
                throw new RuleAlreadyExistsException(rule);
            }
            if (sqlStatements.isForeignKeyViolation(ex)) {
                throw new ArtifactNotFoundException(groupId, artifactId, ex);
            }
            throw ex;
        }
        log.debug("Artifact rule row successfully inserted.");
    }

    @Override
    public void createGroupRule(String groupId, RuleType rule, RuleConfigurationDto config)
            throws RegistryStorageException {
        log.debug("Inserting a group rule row for group: {} rule: {}", groupId, rule.name());
        try {
            handles.withHandle(handle -> {
                handle.createUpdate(sqlStatements.insertGroupRule()).bind(0, normalizeGroupId(groupId))
                        .bind(1, rule.name()).bind(2, config.getConfiguration()).execute();

                outboxEvent.fire(SqlOutboxEvent.of(GroupRuleConfigured.of(groupId, rule, config)));

                return null;
            });
        } catch (Exception ex) {
            if (sqlStatements.isPrimaryKeyViolation(ex)) {
                throw new RuleAlreadyExistsException(rule);
            }
            if (sqlStatements.isForeignKeyViolation(ex)) {
                throw new GroupNotFoundException(groupId, ex);
            }
            throw ex;
        }
        log.debug("Group rule row successfully inserted.");
    }

    @Override
    public void deleteArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Deleting all artifact rules for artifact: {} {}", groupId, artifactId);
        handles.withHandle(handle -> {
            int count = handle.createUpdate(sqlStatements.deleteArtifactRules())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).execute();
            if (count == 0) {
                if (!isArtifactExistsRaw(handle, groupId, artifactId)) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
            }
            return null;
        });
    }

    @Override
    public void deleteGroupRules(String groupId) throws RegistryStorageException {
        log.debug("Deleting all group rules for group: {}", groupId);
        handles.withHandle(handle -> {
            int count = handle.createUpdate(sqlStatements.deleteGroupRules())
                    .bind(0, normalizeGroupId(groupId)).execute();
            if (count == 0) {
                if (!isGroupExistsRaw(handle, groupId)) {
                    throw new GroupNotFoundException(groupId);
                }
            }
            return null;
        });
    }

    @Override
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact rule for artifact: {} {} and rule: {}", groupId, artifactId,
                rule.name());
        return handles.withHandle(handle -> {
            Optional<RuleConfigurationDto> res = handle.createQuery(sqlStatements.selectArtifactRuleByType())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, rule.name())
                    .map(RuleConfigurationDtoMapper.instance).findOne();
            return res.orElseThrow(() -> {
                if (!isArtifactExistsRaw(handle, groupId, artifactId)) {
                    return new ArtifactNotFoundException(groupId, artifactId);
                }
                return new RuleNotFoundException(rule);
            });
        });
    }

    @Override
    public RuleConfigurationDto getGroupRule(String groupId, RuleType rule) throws RegistryStorageException {
        log.debug("Selecting a single group rule for group: {} and rule: {}", groupId, rule.name());
        return handles.withHandle(handle -> {
            Optional<RuleConfigurationDto> res = handle.createQuery(sqlStatements.selectGroupRuleByType())
                    .bind(0, normalizeGroupId(groupId)).bind(1, rule.name())
                    .map(RuleConfigurationDtoMapper.instance).findOne();
            return res.orElseThrow(() -> {
                if (!isGroupExistsRaw(handle, groupId)) {
                    return new GroupNotFoundException(groupId);
                }
                return new RuleNotFoundException(rule);
            });
        });
    }

    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Updating an artifact rule for artifact: {} {} and rule: {}::{}", groupId, artifactId,
                rule.name(), config.getConfiguration());
        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.updateArtifactRule())
                    .bind(0, config.getConfiguration()).bind(1, normalizeGroupId(groupId)).bind(2, artifactId)
                    .bind(3, rule.name()).execute();
            if (rowCount == 0) {
                if (!isArtifactExistsRaw(handle, groupId, artifactId)) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
                throw new RuleNotFoundException(rule);
            }

            outboxEvent.fire(SqlOutboxEvent.of(ArtifactRuleConfigured.of(groupId, artifactId, rule, config)));

            return null;
        });
    }

    @Override
    public void updateGroupRule(String groupId, RuleType rule, RuleConfigurationDto config)
            throws RegistryStorageException {
        log.debug("Updating a group rule for group: {} and rule: {}::{}", groupId, rule.name(),
                config.getConfiguration());
        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.updateGroupRule())
                    .bind(0, config.getConfiguration()).bind(1, normalizeGroupId(groupId))
                    .bind(2, rule.name()).execute();
            if (rowCount == 0) {
                if (!isGroupExistsRaw(handle, groupId)) {
                    throw new GroupNotFoundException(groupId);
                }
                throw new RuleNotFoundException(rule);
            }

            outboxEvent.fire(SqlOutboxEvent.of(GroupRuleConfigured.of(groupId, rule, config)));

            return null;
        });
    }

    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Deleting an artifact rule for artifact: {} {} and rule: {}", groupId, artifactId,
                rule.name());
        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.deleteArtifactRule())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, rule.name()).execute();
            if (rowCount == 0) {
                if (!isArtifactExistsRaw(handle, groupId, artifactId)) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
                throw new RuleNotFoundException(rule);
            }

            switch (rule) {
                case VALIDITY -> outboxEvent.fire(SqlOutboxEvent.of(ArtifactRuleConfigured.of(groupId,
                        artifactId, rule,
                        RuleConfigurationDto.builder().configuration(ValidityLevel.NONE.name()).build())));
                case COMPATIBILITY -> outboxEvent.fire(SqlOutboxEvent
                        .of(ArtifactRuleConfigured.of(groupId, artifactId, rule, RuleConfigurationDto
                                .builder().configuration(CompatibilityLevel.NONE.name()).build())));
                case INTEGRITY -> outboxEvent.fire(SqlOutboxEvent.of(ArtifactRuleConfigured.of(groupId,
                        artifactId, rule,
                        RuleConfigurationDto.builder().configuration(IntegrityLevel.NONE.name()).build())));
            }

            return null;
        });
    }

    @Override
    public void deleteGroupRule(String groupId, RuleType rule) throws RegistryStorageException {
        log.debug("Deleting an group rule for group: {} and rule: {}", groupId, rule.name());
        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.deleteGroupRule())
                    .bind(0, normalizeGroupId(groupId)).bind(1, rule.name()).execute();
            if (rowCount == 0) {
                if (!isGroupExistsRaw(handle, groupId)) {
                    throw new GroupNotFoundException(groupId);
                }
                throw new RuleNotFoundException(rule);
            }

            switch (rule) {
                case VALIDITY -> outboxEvent.fire(SqlOutboxEvent.of(GroupRuleConfigured.of(groupId, rule,
                        RuleConfigurationDto.builder().configuration(ValidityLevel.NONE.name()).build())));
                case COMPATIBILITY -> outboxEvent
                        .fire(SqlOutboxEvent.of(GroupRuleConfigured.of(groupId, rule, RuleConfigurationDto
                                .builder().configuration(CompatibilityLevel.NONE.name()).build())));
                case INTEGRITY -> outboxEvent.fire(SqlOutboxEvent.of(GroupRuleConfigured.of(groupId, rule,
                        RuleConfigurationDto.builder().configuration(IntegrityLevel.NONE.name()).build())));
            }

            return null;
        });
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
        log.debug("Getting a list of versions for artifact: {} {}", groupId, artifactId);

        return handles.withHandle(handle -> {
            String sql = sqlStatements.selectArtifactVersions();
            if (filterBy != null && !filterBy.isEmpty()) {
                sql = sqlStatements.selectArtifactVersionsFilteredByState();
                String jclause = filterBy.stream().map(vs -> "'" + vs.name() + "'")
                        .collect(Collectors.joining(",", "(", ")"));
                sql = sql.replace("(?)", jclause);
            }
            return getArtifactVersionsRaw(handle, groupId, artifactId, sql);
        });
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

        log.debug("Searching for versions");
        return handles.withHandleNoException(handle -> {
            List<SqlStatementVariableBinder> binders = new LinkedList<>();
            String op;

            StringBuilder selectTemplate = new StringBuilder();
            StringBuilder where = new StringBuilder();
            StringBuilder orderByQuery = new StringBuilder();
            StringBuilder limitOffset = new StringBuilder();

            // Formulate the SELECT clause for the query
            selectTemplate.append(
                    "SELECT {{selectColumns}} FROM versions v JOIN artifacts a ON v.groupId = a.groupId AND v.artifactId = a.artifactId");

            // Formulate the WHERE clause for both queries
            where.append(" WHERE (1 = 1)");
            for (SearchFilter filter : filters) {
                where.append(" AND (");
                switch (filter.getType()) {
                    case groupId:
                        op = filter.isNot() ? "!=" : "=";
                        where.append("a.groupId " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, normalizeGroupId(filter.getStringValue()));
                        });
                        break;
                    case artifactId:
                    case contentId:
                    case globalId:
                    case version:
                        op = filter.isNot() ? "!=" : "=";
                        where.append("v.");
                        where.append(filter.getType().name());
                        where.append(" ");
                        where.append(op);
                        where.append(" ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getStringValue());
                        });
                        break;
                    case name:
                    case description:
                        op = filter.isNot() ? "NOT LIKE" : "LIKE";
                        where.append("v.");
                        where.append(filter.getType().name());
                        where.append(" ");
                        where.append(op);
                        where.append(" ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        break;
                    case labels:
                        op = filter.isNot() ? "!=" : "=";
                        Pair<String, String> label = filter.getLabelFilterValue();
                        // Note: convert search to lowercase when searching for labels (case-insensitivity
                        // support).
                        String labelKey = label.getKey().toLowerCase();
                        where.append("EXISTS(SELECT l.* FROM version_labels l WHERE l.labelKey " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, labelKey);
                        });
                        if (label.getValue() != null) {
                            String labelValue = label.getValue().toLowerCase();
                            where.append(" AND l.labelValue " + op + " ?");
                            binders.add((query, idx) -> {
                                query.bind(idx, labelValue);
                            });
                        }
                        where.append(" AND l.globalId = v.globalId)");
                        break;
                    case contentHash:
                        op = filter.isNot() ? "!=" : "=";
                        where.append("EXISTS(SELECT c.* FROM content c WHERE c.contentId = v.contentId AND ");
                        where.append("c.contentHash " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getStringValue());
                        });
                        where.append(")");
                        break;
                    case canonicalHash:
                        op = filter.isNot() ? "!=" : "=";
                        where.append("EXISTS(SELECT c.* FROM content c WHERE c.contentId = v.contentId AND ");
                        where.append("c.canonicalHash " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getStringValue());
                        });
                        where.append(")");
                        break;
                    case state:
                        op = filter.isNot() ? "!=" : "=";
                        where.append("v.state " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, normalizeGroupId(filter.getStringValue()));
                        });
                        break;
                    default:
                        break;
                }
                where.append(")");
            }

            // Add order by to query
            switch (orderBy) {
                case name:
                    orderByQuery.append(" ORDER BY coalesce(v.name, v.version)");
                    break;
                case groupId:
                case artifactId:
                case version:
                case globalId:
                case createdOn:
                case modifiedOn:
                    orderByQuery.append(" ORDER BY v." + orderBy.name());
                    break;
                default:
                    throw new RuntimeException("Sort by " + orderBy.name() + " not supported.");
            }
            orderByQuery.append(" ").append(orderDirection.name());

            // Add limit and offset to artifact query
            if ("mssql".equals(sqlStatements.dbType())) {
                limitOffset.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            } else {
                limitOffset.append(" LIMIT ? OFFSET ?");
            }

            // Query for the versions
            String versionsQuerySql = new StringBuilder(selectTemplate).append(where).append(orderByQuery)
                    .append(limitOffset).toString().replace("{{selectColumns}}", "v.*, a.type");
            Query versionsQuery = handle.createQuery(versionsQuerySql);
            // Query for the total row count
            String countQuerySql = new StringBuilder(selectTemplate).append(where).toString()
                    .replace("{{selectColumns}}", "count(v.globalId)");
            Query countQuery = handle.createQuery(countQuerySql);

            // Bind all query parameters
            int idx = 0;
            for (SqlStatementVariableBinder binder : binders) {
                binder.bind(versionsQuery, idx);
                binder.bind(countQuery, idx);
                idx++;
            }

            // TODO find a better way to swap arguments
            if ("mssql".equals(sqlStatements.dbType())) {
                versionsQuery.bind(idx++, offset);
                versionsQuery.bind(idx++, limit);
            } else {
                versionsQuery.bind(idx++, limit);
                versionsQuery.bind(idx++, offset);
            }

            // Execute query
            List<SearchedVersionDto> versions = versionsQuery.map(SearchedVersionMapper.instance).list();
            limitReturnedLabelsInVersions(versions);
            // Execute count query
            Integer count = countQuery.mapTo(Integer.class).one();

            VersionSearchResultsDto results = new VersionSearchResultsDto();
            results.setVersions(versions);
            results.setCount(count);
            return results;
        });
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
        return handles.withHandle(handle -> {
            Optional<ArtifactVersionMetaDataDto> res = handle
                    .createQuery(sqlStatements.selectArtifactVersionMetaDataByGlobalId()).bind(0, globalId)
                    .map(ArtifactVersionMetaDataDtoMapper.instance).findOne();
            return res.orElseThrow(() -> new VersionNotFoundException(globalId));
        });
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId,
            String version) {
        return handles.withHandle(handle -> {
            return getArtifactVersionMetaDataRaw(handle, groupId, artifactId, version);
        });
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
        log.debug("Updating meta-data for an artifact version: {} {}", groupId, artifactId);

        var metadata = getArtifactVersionMetaData(groupId, artifactId, version);
        long globalId = metadata.getGlobalId();
        handles.withHandle(handle -> {
            boolean modified = false;

            if (editableMetadata.getName() != null) {
                modified = true;

                int rowCount = handle.createUpdate(sqlStatements.updateArtifactVersionNameByGAV())
                        .bind(0, limitStr(editableMetadata.getName(), 512)).bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId).bind(3, version).execute();
                if (rowCount == 0) {
                    throw new VersionNotFoundException(groupId, artifactId, version);
                }
            }

            if (editableMetadata.getDescription() != null) {
                modified = true;

                int rowCount = handle.createUpdate(sqlStatements.updateArtifactVersionDescriptionByGAV())
                        .bind(0, limitStr(editableMetadata.getDescription(), 1024))
                        .bind(1, normalizeGroupId(groupId)).bind(2, artifactId).bind(3, version).execute();
                if (rowCount == 0) {
                    throw new VersionNotFoundException(groupId, artifactId, version);
                }
            }

            Map<String, String> labels = editableMetadata.getLabels();
            if (labels != null) {
                modified = true;

                int rowCount = handle.createUpdate(sqlStatements.updateArtifactVersionLabelsByGAV())
                        .bind(0, RegistryContentUtils.serializeLabels(editableMetadata.getLabels()))
                        .bind(1, normalizeGroupId(groupId)).bind(2, artifactId).bind(3, version).execute();
                if (rowCount == 0) {
                    throw new VersionNotFoundException(groupId, artifactId, version);
                }

                // Delete all appropriate rows in the "version_labels" table
                handle.createUpdate(sqlStatements.deleteVersionLabelsByGlobalId()).bind(0, globalId)
                        .execute();

                // Insert new labels into the "version_labels" table
                labels.forEach((k, v) -> {
                    String sqli = sqlStatements.insertVersionLabel();
                    handle.createUpdate(sqli).bind(0, globalId).bind(1, limitStr(k.toLowerCase(), 256))
                            .bind(2, limitStr(asLowerCase(v), 512)).execute();
                });

                if (modified) {
                    String modifiedBy = securityIdentity.getPrincipal().getName();
                    Date modifiedOn = new Date();

                    rowCount = handle.createUpdate(sqlStatements.updateArtifactVersionModifiedByOn())
                            .bind(0, modifiedBy).bind(1, modifiedOn).bind(2, normalizeGroupId(groupId))
                            .bind(3, artifactId).bind(4, version).execute();
                    if (rowCount == 0) {
                        throw new VersionNotFoundException(groupId, artifactId, version);
                    }
                }
            }

            outboxEvent.fire(SqlOutboxEvent
                    .of(ArtifactVersionMetadataUpdated.of(groupId, artifactId, version, editableMetadata)));

            return null;
        });
    }

    @Override
    public CommentDto createArtifactVersionComment(String groupId, String artifactId, String version,
            String value) {
        log.debug("Inserting an artifact comment row for artifact: {} {} version: {}", groupId, artifactId,
                version);

        String owner = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();

        try {
            var metadata = getArtifactVersionMetaData(groupId, artifactId, version);

            var entity = CommentEntity.builder().commentId(String.valueOf(nextCommentId()))
                    .globalId(metadata.getGlobalId()).owner(owner).createdOn(createdOn.getTime()).value(value)
                    .build();

            importComment(entity);

            log.debug("Comment row successfully inserted.");

            return CommentDto.builder().commentId(entity.commentId).owner(owner)
                    .createdOn(createdOn.getTime()).value(value).build();
        } catch (VersionNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            if (sqlStatements.isForeignKeyViolation(ex)) {
                throw new ArtifactNotFoundException(groupId, artifactId, ex);
            }
            throw ex;
        }
    }

    @Override
    public List<CommentDto> getArtifactVersionComments(String groupId, String artifactId, String version) {
        log.debug("Getting a list of all artifact version comments for: {} {} @ {}", groupId, artifactId,
                version);

        try {
            return handles.withHandle(handle -> {
                return handle.createQuery(sqlStatements.selectVersionComments())
                        .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version)
                        .map(CommentDtoMapper.instance).list();
            });
        } catch (ArtifactNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId) {
        log.debug("Deleting a version comment for artifact: {} {} @ {}", groupId, artifactId, version);
        String deletedBy = securityIdentity.getPrincipal().getName();

        handles.withHandle(handle -> {
            Optional<ArtifactVersionMetaDataDto> res = handle
                    .createQuery(sqlStatements.selectArtifactVersionMetaData())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version)
                    .map(ArtifactVersionMetaDataDtoMapper.instance).findOne();
            ArtifactVersionMetaDataDto avmdd = res
                    .orElseThrow(() -> new VersionNotFoundException(groupId, artifactId, version));

            int rowCount = handle.createUpdate(sqlStatements.deleteVersionComment())
                    .bind(0, avmdd.getGlobalId()).bind(1, commentId).bind(2, deletedBy).execute();
            if (rowCount == 0) {
                throw new CommentNotFoundException(commentId);
            }
            return null;
        });
    }

    @Override
    public void updateArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId, String value) {
        log.debug("Updating a comment for artifact: {} {} @ {}", groupId, artifactId, version);
        String modifiedBy = securityIdentity.getPrincipal().getName();

        handles.withHandle(handle -> {
            Optional<ArtifactVersionMetaDataDto> res = handle
                    .createQuery(sqlStatements.selectArtifactVersionMetaData())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version)
                    .map(ArtifactVersionMetaDataDtoMapper.instance).findOne();
            ArtifactVersionMetaDataDto avmdd = res
                    .orElseThrow(() -> new VersionNotFoundException(groupId, artifactId, version));

            int rowCount = handle.createUpdate(sqlStatements.updateVersionComment()).bind(0, value)
                    .bind(1, avmdd.getGlobalId()).bind(2, commentId).bind(3, modifiedBy).execute();
            if (rowCount == 0) {
                throw new CommentNotFoundException(commentId);
            }
            return null;
        });
    }

    @Override
    public VersionState getArtifactVersionState(String groupId, String artifactId, String version) {
        return handles.withHandle(handle -> {
            Optional<VersionState> res = handle.createQuery(sqlStatements.selectArtifactVersionState())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version)
                    .map(VersionStateMapper.instance).findOne();
            return res.orElseThrow(() -> new VersionNotFoundException(groupId, artifactId, version));
        });
    }

    @Override
    public void updateArtifactVersionState(String groupId, String artifactId, String version,
            VersionState newState, boolean dryRun) {
        handles.withHandle(handle -> {
            if (dryRun) {
                handle.setRollback(true);
            }

            Optional<VersionState> res = handle
                    .createQuery(sqlStatements.selectArtifactVersionStateForUpdate())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version)
                    .map(VersionStateMapper.instance).findOne();
            VersionState currentState = res
                    .orElseThrow(() -> new VersionNotFoundException(groupId, artifactId, version));

            handle.createUpdate(sqlStatements.updateArtifactVersionStateByGAV()).bind(0, newState.name())
                    .bind(1, normalizeGroupId(groupId)).bind(2, artifactId).bind(3, version).execute();

            String modifiedBy = securityIdentity.getPrincipal().getName();
            Date modifiedOn = new Date();
            handle.createUpdate(sqlStatements.updateArtifactVersionModifiedByOn()).bind(0, modifiedBy)
                    .bind(1, modifiedOn).bind(2, normalizeGroupId(groupId)).bind(3, artifactId)
                    .bind(4, version).execute();

            // If transitioning from DRAFT state to something else, then we need to maintain
            // the system branches.
            if (currentState == VersionState.DRAFT) {
                GAV gav = new GAV(groupId, artifactId, version);
                createOrUpdateBranchRaw(handle, gav, BranchId.LATEST, true);
                createOrUpdateSemverBranchesRaw(handle, gav);
                removeVersionFromBranchRaw(handle, gav, BranchId.DRAFTS);
            }

            outboxEvent.fire(SqlOutboxEvent.of(
                    ArtifactVersionStateChanged.of(groupId, artifactId, version, currentState, newState)));

            return null;
        });
    }

    @Override
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectGlobalRules())
                    .map(rs -> RuleType.fromValue(rs.getString("type"))).list();
        });
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        log.debug("Inserting a global rule row for: {}", rule.name());
        try {
            handles.withHandle(handle -> {
                handle.createUpdate(sqlStatements.insertGlobalRule()).bind(0, rule.name())
                        .bind(1, config.getConfiguration()).execute();

                outboxEvent.fire(SqlOutboxEvent.of(GlobalRuleConfigured.of(rule, config)));

                return null;
            });
        } catch (Exception ex) {
            if (sqlStatements.isPrimaryKeyViolation(ex)) {
                throw new RuleAlreadyExistsException(rule);
            }
            throw ex;
        }
    }

    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        log.debug("Deleting all Global Rules");
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.deleteGlobalRules()).execute();
            return null;
        });
    }

    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule)
            throws RuleNotFoundException, RegistryStorageException {
        log.debug("Selecting a single global rule: {}", rule.name());
        return handles.withHandle(handle -> {
            Optional<RuleConfigurationDto> res = handle.createQuery(sqlStatements.selectGlobalRuleByType())
                    .bind(0, rule.name()).map(RuleConfigurationDtoMapper.instance).findOne();
            return res.orElseThrow(() -> new RuleNotFoundException(rule));
        });
    }

    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleNotFoundException, RegistryStorageException {
        log.debug("Updating a global rule: {}::{}", rule.name(), config.getConfiguration());
        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.updateGlobalRule())
                    .bind(0, config.getConfiguration()).bind(1, rule.name()).execute();
            if (rowCount == 0) {
                throw new RuleNotFoundException(rule);
            }

            outboxEvent.fire(SqlOutboxEvent.of(GlobalRuleConfigured.of(rule, config)));

            return null;
        });
    }

    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        log.debug("Deleting a global rule: {}", rule.name());
        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.deleteGlobalRule()).bind(0, rule.name())
                    .execute();
            if (rowCount == 0) {
                throw new RuleNotFoundException(rule);
            }

            switch (rule) {
                case VALIDITY -> outboxEvent.fire(SqlOutboxEvent.of(GlobalRuleConfigured.of(rule,
                        RuleConfigurationDto.builder().configuration(ValidityLevel.NONE.name()).build())));
                case COMPATIBILITY ->
                    outboxEvent.fire(SqlOutboxEvent.of(GlobalRuleConfigured.of(rule, RuleConfigurationDto
                            .builder().configuration(CompatibilityLevel.NONE.name()).build())));
                case INTEGRITY -> outboxEvent.fire(SqlOutboxEvent.of(GlobalRuleConfigured.of(rule,
                        RuleConfigurationDto.builder().configuration(IntegrityLevel.NONE.name()).build())));
            }

            return null;
        });
    }

    @Override
    public List<DynamicConfigPropertyDto> getConfigProperties() throws RegistryStorageException {
        log.debug("Getting all config properties.");
        return handles.withHandleNoException(handle -> {
            String sql = sqlStatements.selectConfigProperties();
            return handle.createQuery(sql).map(DynamicConfigPropertyDtoMapper.instance).list().stream()
                    // Filter out possible null values.
                    .filter(Objects::nonNull).collect(toList());
        });
    }

    @Override
    public DynamicConfigPropertyDto getConfigProperty(String propertyName) throws RegistryStorageException {
        return getRawConfigProperty(propertyName); // TODO Replace this?
    }

    @Override
    public DynamicConfigPropertyDto getRawConfigProperty(String propertyName) {
        log.debug("Selecting a single config property: {}", propertyName);
        return handles.withHandle(handle -> {
            final String normalizedPropertyName = DtoUtil.appAuthPropertyToRegistry(propertyName);
            Optional<DynamicConfigPropertyDto> res = handle
                    .createQuery(sqlStatements.selectConfigPropertyByName()).bind(0, normalizedPropertyName)
                    .map(DynamicConfigPropertyDtoMapper.instance).findOne();
            return res.orElse(null);
        });
    }

    @Override
    public void setConfigProperty(DynamicConfigPropertyDto propertyDto) throws RegistryStorageException {
        log.debug("Setting a config property with name: {}  and value: {}", propertyDto.getName(),
                propertyDto.getValue());
        handles.withHandleNoException(handle -> {
            String propertyName = propertyDto.getName();
            String propertyValue = propertyDto.getValue();

            // First delete the property row from the table
            // TODO Use deleteConfigProperty
            handle.createUpdate(sqlStatements.deleteConfigProperty()).bind(0, propertyName).execute();

            // Then create the row again with the new value
            handle.createUpdate(sqlStatements.insertConfigProperty()).bind(0, propertyName)
                    .bind(1, propertyValue).bind(2, java.lang.System.currentTimeMillis()).execute();

            return null;
        });
    }

    @Override
    public void deleteConfigProperty(String propertyName) throws RegistryStorageException {
        handles.withHandle(handle -> {
            handle.createUpdate(sqlStatements.deleteConfigProperty()).bind(0, propertyName).execute();
            return null;
        });
    }

    @Override
    public List<DynamicConfigPropertyDto> getStaleConfigProperties(Instant lastRefresh)
            throws RegistryStorageException {
        log.debug("Getting all stale config properties.");
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectStaleConfigProperties())
                    .bind(0, lastRefresh.toEpochMilli()).map(DynamicConfigPropertyDtoMapper.instance).list()
                    .stream()
                    // Filter out possible null values.
                    .filter(Objects::nonNull).collect(toList());
        });
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
        try {
            handles.withHandle(handle -> {
                // Insert a row into the groups table
                handle.createUpdate(sqlStatements.insertGroup()).bind(0, group.getGroupId())
                        .bind(1, group.getDescription()).bind(2, group.getArtifactsType())
                        .bind(3, group.getOwner())
                        // TODO io.apicurio.registry.storage.dto.GroupMetaDataDto should not use raw numeric
                        // timestamps
                        .bind(4, group.getCreatedOn() == 0 ? new Date() : new Date(group.getCreatedOn()))
                        .bind(5, group.getModifiedBy())
                        .bind(6, group.getModifiedOn() == 0 ? new Date() : new Date(group.getModifiedOn()))
                        .bind(7, RegistryContentUtils.serializeLabels(group.getLabels())).execute();

                // Insert new labels into the "group_labels" table
                Map<String, String> labels = group.getLabels();
                if (labels != null && !labels.isEmpty()) {
                    labels.forEach((k, v) -> {
                        String sqli = sqlStatements.insertGroupLabel();
                        handle.createUpdate(sqli).bind(0, group.getGroupId())
                                .bind(1, limitStr(k.toLowerCase(), 256))
                                .bind(2, limitStr(asLowerCase(v), 512)).execute();
                    });
                }

                outboxEvent.fire(SqlOutboxEvent.of(GroupCreated.of(group)));

                return null;
            });
        } catch (Exception ex) {
            if (sqlStatements.isPrimaryKeyViolation(ex)) {
                throw new GroupAlreadyExistsException(group.getGroupId());
            }
            throw ex;
        }
    }

    /**
     * Deletes a group and all artifacts in that group.
     *
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGroup(java.lang.String)
     */
    @Override
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        handles.withHandleNoException(handle -> {
            // Note: delete artifact rules separately. Artifact rules are not set to cascade on delete
            // because the Confluent API allows users to configure rules for artifacts that don't exist. :(
            handle.createUpdate(sqlStatements.deleteArtifactRulesByGroupId())
                    .bind(0, normalizeGroupId(groupId)).execute();

            // Delete all artifacts in the group (TODO there is currently no FK from artifacts to groups)
            handle.createUpdate(sqlStatements.deleteArtifactsByGroupId()).bind(0, normalizeGroupId(groupId))
                    .execute();

            // Now delete the group (labels and rules etc will cascade)
            int rows = handle.createUpdate(sqlStatements.deleteGroup()).bind(0, groupId).execute();
            if (rows == 0) {
                throw new GroupNotFoundException(groupId);
            }

            outboxEvent.fire(SqlOutboxEvent.of(GroupDeleted.of(groupId)));

            return null;
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateGroupMetaData(java.lang.String,
     *      io.apicurio.registry.storage.dto.EditableGroupMetaDataDto)
     */
    @Override
    public void updateGroupMetaData(String groupId, EditableGroupMetaDataDto dto) {
        String modifiedBy = securityIdentity.getPrincipal().getName();
        Date modifiedOn = new Date();
        log.debug("Updating metadata for group {}.", groupId);

        handles.withHandleNoException(handle -> {
            // Update the row in the groups table
            int rows = handle.createUpdate(sqlStatements.updateGroup()).bind(0, dto.getDescription())
                    .bind(1, modifiedBy).bind(2, modifiedOn)
                    .bind(3, RegistryContentUtils.serializeLabels(dto.getLabels())).bind(4, groupId)
                    .execute();
            if (rows == 0) {
                throw new GroupNotFoundException(groupId);
            }

            // Delete all appropriate rows in the "group_labels" table
            handle.createUpdate(sqlStatements.deleteGroupLabelsByGroupId()).bind(0, groupId).execute();

            // Insert new labels into the "group_labels" table
            if (dto.getLabels() != null && !dto.getLabels().isEmpty()) {
                dto.getLabels().forEach((k, v) -> {
                    String sqli = sqlStatements.insertGroupLabel();
                    handle.createUpdate(sqli).bind(0, groupId).bind(1, limitStr(k.toLowerCase(), 256))
                            .bind(2, limitStr(asLowerCase(v), 512)).execute();
                });
            }

            outboxEvent.fire(SqlOutboxEvent.of(GroupMetadataUpdated.of(groupId, dto)));

            return null;
        });
    }

    @Override
    public List<String> getGroupIds(Integer limit) throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            Query query = handle.createQuery(sqlStatements.selectGroups());
            query.bind(0, limit);
            return query.map(rs -> rs.getString("groupId")).list();
        });
    }

    @Override
    public GroupMetaDataDto getGroupMetaData(String groupId)
            throws GroupNotFoundException, RegistryStorageException {
        return handles.withHandle(handle -> {
            Optional<GroupMetaDataDto> res = handle.createQuery(sqlStatements.selectGroupByGroupId())
                    .bind(0, groupId).map(GroupMetaDataDtoMapper.instance).findOne();
            return res.orElseThrow(() -> new GroupNotFoundException(groupId));
        });
    }

    /**
     * NOTE: Does not export the manifest file TODO
     */
    @Override
    public void exportData(Function<Entity, Void> handler) throws RegistryStorageException {
        // Export a simple manifest file
        /////////////////////////////////
        ManifestEntity manifest = new ManifestEntity();
        if (securityIdentity != null && securityIdentity.getPrincipal() != null) {
            manifest.exportedBy = securityIdentity.getPrincipal().getName();
        }
        manifest.systemName = system.getName();
        manifest.systemDescription = system.getDescription();
        manifest.systemVersion = system.getVersion();
        manifest.dbVersion = "" + DB_VERSION;
        handler.apply(manifest);

        // Export all content
        /////////////////////////////////
        handles.withHandle(handle -> {
            Stream<ContentEntity> stream = handle.createQuery(sqlStatements.exportContent()).setFetchSize(50)
                    .map(ContentEntityMapper.instance).stream();
            // Process and then close the stream.
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });

        // Export all groups
        /////////////////////////////////
        handles.withHandle(handle -> {
            Stream<GroupEntity> stream = handle.createQuery(sqlStatements.exportGroups()).setFetchSize(50)
                    .map(GroupEntityMapper.instance).stream();
            // Process and then close the stream.
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });

        // Export all group rules
        /////////////////////////////////
        handles.withHandle(handle -> {
            Stream<GroupRuleEntity> stream = handle.createQuery(sqlStatements.exportGroupRules())
                    .setFetchSize(50).map(GroupRuleEntityMapper.instance).stream();
            // Process and then close the stream.
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });

        // Export all artifacts
        /////////////////////////////////
        handles.withHandle(handle -> {
            Stream<ArtifactEntity> stream = handle.createQuery(sqlStatements.exportArtifacts())
                    .setFetchSize(50).map(ArtifactEntityMapper.instance).stream();
            // Process and then close the stream.
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });

        // Export all artifact versions
        /////////////////////////////////
        handles.withHandle(handle -> {
            Stream<ArtifactVersionEntity> stream = handle.createQuery(sqlStatements.exportArtifactVersions())
                    .setFetchSize(50).map(ArtifactVersionEntityMapper.instance).stream();
            // Process and then close the stream.
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });

        // Export all artifact comments
        /////////////////////////////////
        handles.withHandle(handle -> {
            Stream<CommentEntity> stream = handle.createQuery(sqlStatements.exportVersionComments())
                    .setFetchSize(50).map(CommentEntityMapper.instance).stream();
            // Process and then close the stream.
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });

        // Export all branches
        /////////////////////////////////
        handles.withHandle(handle -> {
            Stream<BranchEntity> stream = handle.createQuery(sqlStatements.exportBranches()).setFetchSize(50)
                    .map(BranchEntityMapper.instance).stream();
            // Process and then close the stream.
            try (stream) {
                stream.forEach(branch -> {
                    branch.versions = getBranchVersionNumbersRaw(handle, branch.toGA(), branch.toBranchId());
                    handler.apply(branch);
                });
            }
            return null;
        });

        // Export all artifact rules
        /////////////////////////////////
        handles.withHandle(handle -> {
            Stream<ArtifactRuleEntity> stream = handle.createQuery(sqlStatements.exportArtifactRules())
                    .setFetchSize(50).map(ArtifactRuleEntityMapper.instance).stream();
            // Process and then close the stream.
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });

        // Export all global rules
        /////////////////////////////////
        handles.withHandle(handle -> {
            Stream<GlobalRuleEntity> stream = handle.createQuery(sqlStatements.exportGlobalRules())
                    .setFetchSize(50).map(GlobalRuleEntityMapper.instance).stream();
            // Process and then close the stream.
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });
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
        return handles.withHandle(handle -> {
            return handle.createQuery(sqlStatements.selectAllArtifactCount()).mapTo(Long.class).one();
        });
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
        return handles.withHandle(handle -> {
            return handle.createQuery(sqlStatements.selectTotalArtifactVersionsCount()).mapTo(Long.class)
                    .one();
        });
    }

    @Override
    public void createRoleMapping(String principalId, String role, String principalName)
            throws RegistryStorageException {
        log.debug("Inserting a role mapping row for: {}", principalId);
        try {
            handles.withHandle(handle -> {
                handle.createUpdate(sqlStatements.insertRoleMapping()).bind(0, principalId).bind(1, role)
                        .bind(2, principalName).execute();
                return null;
            });
        } catch (Exception ex) {
            if (sqlStatements.isPrimaryKeyViolation(ex)) {
                throw new RoleMappingAlreadyExistsException(principalId, role);
            }
            throw ex;
        }
    }

    @Override
    public void deleteRoleMapping(String principalId) throws RegistryStorageException {
        log.debug("Deleting a role mapping row for: {}", principalId);
        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.deleteRoleMapping()).bind(0, principalId)
                    .execute();
            if (rowCount == 0) {
                throw new RoleMappingNotFoundException(principalId);
            }
            return null;
        });
    }

    @Override
    public RoleMappingDto getRoleMapping(String principalId) throws RegistryStorageException {
        log.debug("Selecting a single role mapping for: {}", principalId);
        return handles.withHandle(handle -> {
            Optional<RoleMappingDto> res = handle.createQuery(sqlStatements.selectRoleMappingByPrincipalId())
                    .bind(0, principalId).map(RoleMappingDtoMapper.instance).findOne();
            return res.orElseThrow(() -> new RoleMappingNotFoundException(principalId));
        });
    }

    @Override
    public String getRoleForPrincipal(String principalId) throws RegistryStorageException {
        log.debug("Selecting the role for: {}", principalId);
        return handles.withHandle(handle -> {
            Optional<String> res = handle.createQuery(sqlStatements.selectRoleByPrincipalId())
                    .bind(0, principalId).mapTo(String.class).findOne();
            return res.orElse(null);
        });
    }

    @Override
    public List<RoleMappingDto> getRoleMappings() throws RegistryStorageException {
        log.debug("Getting a list of all role mappings.");
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectRoleMappings()).map(RoleMappingDtoMapper.instance)
                    .list();
        });
    }

    @Override
    public RoleMappingSearchResultsDto searchRoleMappings(int offset, int limit)
            throws RegistryStorageException {
        log.debug("Searching role mappings.");
        return handles.withHandleNoException(handle -> {
            String query = sqlStatements.selectRoleMappings() + " LIMIT ? OFFSET ?";
            String countQuery = sqlStatements.countRoleMappings();
            List<RoleMappingDto> mappings = handle.createQuery(query).bind(0, limit).bind(1, offset)
                    .map(RoleMappingDtoMapper.instance).list();
            Integer count = handle.createQuery(countQuery).mapTo(Integer.class).one();
            return RoleMappingSearchResultsDto.builder().count(count).roleMappings(mappings).build();
        });
    }

    @Override
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException {
        log.debug("Updating a role mapping: {}::{}", principalId, role);
        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.updateRoleMapping()).bind(0, role)
                    .bind(1, principalId).execute();
            if (rowCount == 0) {
                throw new RoleMappingNotFoundException(principalId, role);
            }
            return null;
        });
    }

    @Override
    public String createDownload(DownloadContextDto context) throws RegistryStorageException {
        log.debug("Inserting a download.");
        String downloadId = UUID.randomUUID().toString();
        return handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.insertDownload()).bind(0, downloadId)
                    .bind(1, context.getExpires()).bind(2, mapper.writeValueAsString(context)).execute();
            return downloadId;
        });
    }

    @Override
    public DownloadContextDto consumeDownload(String downloadId) throws RegistryStorageException {
        log.debug("Consuming a download ID: {}", downloadId);

        return handles.withHandleNoException(handle -> {
            long now = java.lang.System.currentTimeMillis();

            // Select the download context.
            Optional<String> res = handle.createQuery(sqlStatements.selectDownloadContext())
                    .bind(0, downloadId).bind(1, now).mapTo(String.class).findOne();
            String downloadContext = res.orElseThrow(DownloadNotFoundException::new);

            // Attempt to delete the row.
            int rowCount = handle.createUpdate(sqlStatements.deleteDownload()).bind(0, downloadId).execute();
            if (rowCount == 0) {
                throw new DownloadNotFoundException();
            }

            // Return what we consumed
            return mapper.readValue(downloadContext, DownloadContextDto.class);
        });
    }

    @Override
    public void deleteAllExpiredDownloads() throws RegistryStorageException {
        log.debug("Deleting all expired downloads");
        long now = java.lang.System.currentTimeMillis();
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.deleteExpiredDownloads()).bind(0, now).execute();
            return null;
        });
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
        if (references == null || references.isEmpty()) {
            return Collections.emptyMap();
        } else {
            Map<String, TypedContent> result = new LinkedHashMap<>();
            resolveReferencesRaw(handle, result, references);
            return result;
        }
    }

    @Override
    public boolean isArtifactExists(String groupId, String artifactId) throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return isArtifactExistsRaw(handle, groupId, artifactId);
        });
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
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectContentIdsReferencingArtifactBy())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version).mapTo(Long.class)
                    .list();
        });
    }

    @Override
    public List<Long> getGlobalIdsReferencingArtifactVersion(String groupId, String artifactId,
            String version) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectGlobalIdsReferencingArtifactBy())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version).mapTo(Long.class)
                    .list();
        });
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
        return handles.withHandleNoException(handle -> {
            List<SqlStatementVariableBinder> binders = new LinkedList<>();
            String op;

            StringBuilder selectTemplate = new StringBuilder();
            StringBuilder where = new StringBuilder();
            StringBuilder orderByQuery = new StringBuilder();
            StringBuilder limitOffset = new StringBuilder();

            // Formulate the SELECT clause for the artifacts query
            selectTemplate.append("SELECT {{selectColumns}} FROM groups g ");

            // Formulate the WHERE clause for both queries
            where.append(" WHERE (1 = 1)");
            for (SearchFilter filter : filters) {
                where.append(" AND (");
                switch (filter.getType()) {
                    case description:
                        op = filter.isNot() ? "NOT LIKE" : "LIKE";
                        where.append("g.description ");
                        where.append(op);
                        where.append(" ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        break;
                    case groupId:
                        op = filter.isNot() ? "!=" : "=";
                        where.append("g.groupId ");
                        where.append(op);
                        where.append(" ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getStringValue());
                        });
                        break;
                    case labels:
                        op = filter.isNot() ? "!=" : "=";
                        Pair<String, String> label = filter.getLabelFilterValue();
                        // Note: convert search to lowercase when searching for labels (case-insensitivity
                        // support).
                        String labelKey = label.getKey().toLowerCase();
                        where.append("EXISTS(SELECT l.* FROM group_labels l WHERE l.labelKey " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, labelKey);
                        });
                        if (label.getValue() != null) {
                            String labelValue = label.getValue().toLowerCase();
                            where.append(" AND l.labelValue " + op + " ?");
                            binders.add((query, idx) -> {
                                query.bind(idx, labelValue);
                            });
                        }
                        where.append(" AND l.groupId = g.groupId)");
                        break;
                    default:

                        break;
                }
                where.append(")");
            }

            // Add order by to artifact query
            switch (orderBy) {
                case groupId:
                case createdOn:
                case modifiedOn:
                    orderByQuery.append(" ORDER BY g.").append(orderBy.name());
                    break;
                default:
                    throw new RuntimeException("Sort by " + orderBy.name() + " not supported.");
            }
            orderByQuery.append(" ").append(orderDirection.name());

            // Add limit and offset to query
            if ("mssql".equals(sqlStatements.dbType())) {
                limitOffset.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            } else {
                limitOffset.append(" LIMIT ? OFFSET ?");
            }

            // Query for the group
            String groupsQuerySql = new StringBuilder(selectTemplate).append(where).append(orderByQuery)
                    .append(limitOffset).toString().replace("{{selectColumns}}", "*");
            Query groupsQuery = handle.createQuery(groupsQuerySql);
            // Query for the total row count
            String countQuerySql = new StringBuilder(selectTemplate).append(where).toString()
                    .replace("{{selectColumns}}", "count(g.groupId)");
            Query countQuery = handle.createQuery(countQuerySql);

            // Bind all query parameters
            int idx = 0;
            for (SqlStatementVariableBinder binder : binders) {
                binder.bind(groupsQuery, idx);
                binder.bind(countQuery, idx);
                idx++;
            }
            // TODO find a better way to swap arguments
            if ("mssql".equals(sqlStatements.dbType())) {
                groupsQuery.bind(idx++, offset);
                groupsQuery.bind(idx++, limit);
            } else {
                groupsQuery.bind(idx++, limit);
                groupsQuery.bind(idx++, offset);
            }

            // Execute query
            List<SearchedGroupDto> groups = groupsQuery.map(SearchedGroupMapper.instance).list();
            limitReturnedLabelsInGroups(groups);

            // Execute count query
            Integer count = countQuery.mapTo(Integer.class).one();

            GroupSearchResultsDto results = new GroupSearchResultsDto();
            results.setGroups(groups);
            results.setCount(count);
            return results;
        });
    }

    @Override
    @Transactional
    public ContentWrapperDto getContentByReference(ArtifactReferenceDto reference) {
        try {
            var meta = getArtifactVersionMetaData(reference.getGroupId(), reference.getArtifactId(),
                    reference.getVersion());
            ContentWrapperDto artifactByContentId = getContentById(meta.getContentId());
            artifactByContentId.setArtifactType(meta.getArtifactType());
            return artifactByContentId;
        } catch (VersionNotFoundException e) {
            return null;
        }
    }

    private void resolveReferencesRaw(Handle handle, Map<String, TypedContent> resolvedReferences,
            List<ArtifactReferenceDto> references) {
        if (references != null && !references.isEmpty()) {
            for (ArtifactReferenceDto reference : references) {
                if (reference.getArtifactId() == null || reference.getName() == null
                        || reference.getVersion() == null) {
                    throw new IllegalStateException("Invalid reference: " + reference);
                } else {
                    if (!resolvedReferences.containsKey(reference.getName())) {
                        // TODO improve exception handling
                        try {
                            final ArtifactVersionMetaDataDto referencedArtifactMetaData = getArtifactVersionMetaDataRaw(
                                    handle, reference.getGroupId(), reference.getArtifactId(),
                                    reference.getVersion());
                            final ContentWrapperDto referencedContent = getContentByIdRaw(handle,
                                    referencedArtifactMetaData.getContentId());
                            resolveReferencesRaw(handle, resolvedReferences,
                                    referencedContent.getReferences());
                            TypedContent typedContent = TypedContent.create(referencedContent.getContent(),
                                    referencedContent.getContentType());
                            resolvedReferences.put(reference.getName(), typedContent);
                        } catch (VersionNotFoundException ex) {
                            // Ignored
                        }
                    }
                }
            }
        }
    }

    // TODO call this in a cleanup cron job instead?
    private void deleteAllOrphanedContentRaw(Handle handle) {
        log.debug("Deleting all orphaned content");

        // Delete orphaned references
        handle.createUpdate(sqlStatements.deleteOrphanedContentReferences()).execute();

        // Delete orphaned content
        handle.createUpdate(sqlStatements.deleteAllOrphanedContent()).execute();
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
        handles.withHandleNoException(handle -> {
            resetSequenceRaw(handle, GLOBAL_ID_SEQUENCE, sqlStatements.selectMaxGlobalId());
        });
    }

    @Override
    public void resetContentId() {
        handles.withHandleNoException(handle -> {
            resetSequenceRaw(handle, CONTENT_ID_SEQUENCE, sqlStatements.selectMaxContentId());
        });
    }

    @Override
    public void resetCommentId() {
        handles.withHandleNoException(handle -> {
            resetSequenceRaw(handle, COMMENT_ID_SEQUENCE, sqlStatements.selectMaxVersionCommentId());
        });
    }

    private void resetSequenceRaw(Handle handle, String sequenceName, String sqlMaxIdFromTable) {
        Optional<Long> maxIdTable = handle.createQuery(sqlMaxIdFromTable).mapTo(Long.class).findOne();

        Optional<Long> current;
        if (isH2()) {
            current = Optional.of(sequenceCounters.get(sequenceName).get());
        } else {
            current = handle.createQuery(sqlStatements.selectCurrentSequenceValue()).bind(0, sequenceName)
                    .mapTo(Long.class).findOne();
        }
        final Optional<Long> currentIdSeq = current;

        // TODO maybe do this in one query?
        Optional<Long> maxId = maxIdTable.map(maxIdTableValue -> {
            if (currentIdSeq.isPresent()) {
                if (currentIdSeq.get() > maxIdTableValue) {
                    // id in sequence is bigger than max value in table
                    return currentIdSeq.get();
                }
            }
            // max value in table is bigger that id in sequence
            return maxIdTableValue;
        });

        if (maxId.isPresent()) {
            log.info("Resetting {} sequence", sequenceName);
            long id = maxId.get();

            if (isPostgresql()) {
                handle.createUpdate(sqlStatements.resetSequenceValue()).bind(0, sequenceName).bind(1, id)
                        .bind(2, id).execute();
            } else if (isH2()) {
                // H2 uses atomic counters instead of the DB for sequences.
                sequenceCounters.get(sequenceName).set(id);
            } else {
                handle.createUpdate(sqlStatements.resetSequenceValue()).bind(0, sequenceName).bind(1, id)
                        .execute();
            }

            log.info("Successfully reset {} to {}", sequenceName, id);
        }
    }

    @Override
    public void importGroupRule(GroupRuleEntity entity) {
        handles.withHandleNoException(handle -> {
            if (isGroupExistsRaw(handle, entity.groupId)) {
                handle.createUpdate(sqlStatements.importGroupRule()).bind(0, normalizeGroupId(entity.groupId))
                        .bind(1, entity.type.name()).bind(2, entity.configuration).execute();
            } else {
                throw new GroupNotFoundException(entity.groupId);
            }
            return null;
        });
    }

    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) {
        handles.withHandleNoException(handle -> {
            if (isArtifactExistsRaw(handle, entity.groupId, entity.artifactId)) {
                handle.createUpdate(sqlStatements.importArtifactRule())
                        .bind(0, normalizeGroupId(entity.groupId)).bind(1, entity.artifactId)
                        .bind(2, entity.type.name()).bind(3, entity.configuration).execute();
            } else {
                throw new ArtifactNotFoundException(entity.groupId, entity.artifactId);
            }
            return null;
        });
    }

    @Override
    public void importArtifact(ArtifactEntity entity) {
        handles.withHandleNoException(handle -> {
            if (!isArtifactExistsRaw(handle, entity.groupId, entity.artifactId)) {
                String labelsStr = RegistryContentUtils.serializeLabels(entity.labels);
                handle.createUpdate(sqlStatements.insertArtifact()).bind(0, normalizeGroupId(entity.groupId))
                        .bind(1, entity.artifactId).bind(2, entity.artifactType).bind(3, entity.owner)
                        .bind(4, new Date(entity.createdOn)).bind(5, entity.modifiedBy)
                        .bind(6, new Date(entity.modifiedOn)).bind(7, entity.name).bind(8, entity.description)
                        .bind(9, labelsStr).execute();

                // Insert labels into the "artifact_labels" table
                if (entity.labels != null && !entity.labels.isEmpty()) {
                    entity.labels.forEach((k, v) -> {
                        handle.createUpdate(sqlStatements.insertArtifactLabel())
                                .bind(0, normalizeGroupId(entity.groupId)).bind(1, entity.artifactId)
                                .bind(2, k.toLowerCase()).bind(3, v == null ? null : v.toLowerCase())
                                .execute();
                    });
                }
            } else {
                throw new ArtifactAlreadyExistsException(entity.groupId, entity.artifactId);
            }
            return null;
        });
    }

    @Override
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        handles.withHandleNoException(handle -> {
            if (!isArtifactExistsRaw(handle, entity.groupId, entity.artifactId)) {
                throw new ArtifactNotFoundException(entity.groupId, entity.artifactId);
            }
            if (isGlobalIdExistsRaw(handle, entity.globalId)) {
                throw new VersionAlreadyExistsException(entity.globalId);
            }
            if (!isGlobalIdExistsRaw(handle, entity.globalId)) {
                handle.createUpdate(sqlStatements.importArtifactVersion()).bind(0, entity.globalId)
                        .bind(1, normalizeGroupId(entity.groupId)).bind(2, entity.artifactId)
                        .bind(3, entity.version).bind(4, entity.versionOrder).bind(5, entity.state)
                        .bind(6, entity.name).bind(7, entity.description).bind(8, entity.owner)
                        .bind(9, new Date(entity.createdOn)).bind(10, entity.modifiedBy)
                        .bind(11, new Date(entity.modifiedOn))
                        .bind(12, RegistryContentUtils.serializeLabels(entity.labels))
                        .bind(13, entity.contentId).execute();

                // Insert labels into the "version_labels" table
                if (entity.labels != null && !entity.labels.isEmpty()) {
                    entity.labels.forEach((k, v) -> {
                        handle.createUpdate(sqlStatements.insertVersionLabel()).bind(0, entity.globalId)
                                .bind(1, k.toLowerCase()).bind(2, v == null ? null : v.toLowerCase())
                                .execute();
                    });
                }

            } else {
                throw new VersionAlreadyExistsException(entity.globalId);
            }

            return null;
        });
    }

    @Override
    public void importContent(ContentEntity entity) {
        handles.withHandleNoException(handle -> {
            if (!isContentExistsRaw(handle, entity.contentId)) {
                handle.createUpdate(sqlStatements.importContent()).bind(0, entity.contentId)
                        .bind(1, entity.canonicalHash).bind(2, entity.contentHash).bind(3, entity.contentType)
                        .bind(4, entity.contentBytes).bind(5, entity.serializedReferences).execute();

                insertReferencesRaw(handle, entity.contentId,
                        RegistryContentUtils.deserializeReferences(entity.serializedReferences));
            } else {
                throw new ContentAlreadyExistsException(entity.contentId);
            }
            return null;
        });
    }

    @Override
    public void importGlobalRule(GlobalRuleEntity entity) {
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.importGlobalRule()) // TODO Duplicated SQL query
                    .bind(0, entity.ruleType.name()).bind(1, entity.configuration).execute();
            return null;
        });
    }

    @Override
    public void importGroup(GroupEntity entity) {
        handles.withHandleNoException(handle -> {
            if (isGroupExistsRaw(handle, entity.groupId)) {
                throw new GroupAlreadyExistsException(entity.groupId);
            }

            handle.createUpdate(sqlStatements.importGroup())
                    .bind(0, RegistryContentUtils.normalizeGroupId(entity.groupId))
                    .bind(1, entity.description).bind(2, entity.artifactsType).bind(3, entity.owner)
                    .bind(4, new Date(entity.createdOn)).bind(5, entity.modifiedBy)
                    .bind(6, new Date(entity.modifiedOn))
                    .bind(7, RegistryContentUtils.serializeLabels(entity.labels)).execute();

            // Insert labels into the "group_labels" table
            if (entity.labels != null && !entity.labels.isEmpty()) {
                entity.labels.forEach((k, v) -> {
                    handle.createUpdate(sqlStatements.insertGroupLabel())
                            .bind(0, normalizeGroupId(entity.groupId)).bind(1, k.toLowerCase())
                            .bind(2, v.toLowerCase()).execute();
                });
            }

            return null;
        });
    }

    @Override
    public void importComment(CommentEntity entity) {
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.insertVersionComment()).bind(0, entity.commentId)
                    .bind(1, entity.globalId).bind(2, entity.owner).bind(3, new Date(entity.createdOn))
                    .bind(4, entity.value).execute();
            return null;
        });
    }

    @Override
    public boolean isEmpty() {
        return handles.withHandle(handle -> {
            return handle.createQuery(sqlStatements.selectAllContentCount()).mapTo(Long.class).one() == 0;
        });
    }

    private boolean isContentExistsRaw(Handle handle, long contentId) {
        return handle.createQuery(sqlStatements().selectContentExists()).bind(0, contentId)
                .mapTo(Integer.class).one() > 0;
    }

    private boolean isGlobalIdExistsRaw(Handle handle, long globalId) {
        return handle.createQuery(sqlStatements().selectGlobalIdExists()).bind(0, globalId)
                .mapTo(Integer.class).one() > 0;
    }

    @Override
    public long nextContentId() {
        return handles.withHandleNoException(this::nextContentIdRaw);
    }

    private long nextContentIdRaw(Handle handle) {
        return nextSequenceValueRaw(handle, CONTENT_ID_SEQUENCE);
    }

    @Override
    public long nextGlobalId() {
        return handles.withHandleNoException(this::nextGlobalIdRaw);
    }

    private long nextGlobalIdRaw(Handle handle) {
        return nextSequenceValueRaw(handle, GLOBAL_ID_SEQUENCE);
    }

    @Override
    public long nextCommentId() {
        return handles.withHandleNoException(this::nextCommentIdRaw);
    }

    private long nextCommentIdRaw(Handle handle) {
        return nextSequenceValueRaw(handle, COMMENT_ID_SEQUENCE);
    }

    private long nextSequenceValueRaw(Handle handle, String sequenceName) {
        if (isH2()) {
            return sequenceCounters.get(sequenceName).incrementAndGet();
        } else if (isMysql()) {
            handle.createUpdate(sqlStatements.getNextSequenceValue()).bind(0, sequenceName).execute();
            return handle.createQuery(sqlStatements.selectCurrentSequenceValue()).bind(0, sequenceName)
                    .mapTo(Long.class).one();
        } else {
            return handle.createQuery(sqlStatements.getNextSequenceValue()).bind(0, sequenceName)
                    .mapTo(Long.class).one(); // TODO Handle non-existing sequence (see resetSequence)
        }
    }

    @Override
    public boolean isContentExists(String contentHash) throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectContentCountByHash()).bind(0, contentHash)
                    .mapTo(Integer.class).one() > 0;
        });
    }

    @Override
    public boolean isArtifactRuleExists(String groupId, String artifactId, RuleType rule)
            throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectArtifactRuleCountByType())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, rule.name())
                    .mapTo(Integer.class).one() > 0;
        });
    }

    @Override
    public boolean isGlobalRuleExists(RuleType rule) throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectGlobalRuleCountByType()).bind(0, rule.name())
                    .mapTo(Integer.class).one() > 0;
        });
    }

    @Override
    public boolean isRoleMappingExists(String principalId) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectRoleMappingCountByPrincipal())
                    .bind(0, principalId).mapTo(Integer.class).one() > 0;
        });
    }

    @Override
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) {
        handles.withHandleNoException(handle -> {
            int rowCount = handle.createUpdate(sqlStatements().updateContentCanonicalHash())
                    .bind(0, newCanonicalHash).bind(1, contentId).bind(2, contentHash).execute();
            if (rowCount == 0) {
                log.warn("update content canonicalHash, no row match contentId {} contentHash {}", contentId,
                        contentHash);
            }
            return null;
        });
    }

    @Override
    public Optional<Long> contentIdFromHash(String contentHash) {
        return handles.withHandleNoException(handle -> {
            return contentIdFromHashRaw(handle, contentHash);
        });
    }

    private Optional<Long> contentIdFromHashRaw(Handle handle, String contentHash) {
        return handle.createQuery(sqlStatements().selectContentIdByHash()).bind(0, contentHash)
                .mapTo(Long.class).findOne();
    }

    @Override
    public BranchMetaDataDto createBranch(GA ga, BranchId branchId, String description,
            List<String> versions) {
        try {
            String user = securityIdentity.getPrincipal().getName();
            Date now = new Date();

            handles.withHandle(handle -> {
                // Insert a row into the branches table
                handle.createUpdate(sqlStatements.insertBranch()).bind(0, ga.getRawGroupId())
                        .bind(1, ga.getRawArtifactId()).bind(2, branchId.getRawBranchId())
                        .bind(3, description).bind(4, false).bind(5, user).bind(6, now).bind(7, user)
                        .bind(8, now).execute();

                // Append each of the versions onto the branch
                if (versions != null) {
                    versions.forEach(version -> {
                        appendVersionToBranchRaw(handle, ga, branchId, new VersionId(version));
                    });
                }

                return null;
            });

            return BranchMetaDataDto.builder().groupId(ga.getRawGroupIdWithNull())
                    .artifactId(ga.getRawArtifactId()).branchId(branchId.getRawBranchId())
                    .description(description).owner(user).createdOn(now.getTime()).modifiedBy(user)
                    .modifiedOn(now.getTime()).build();
        } catch (Exception ex) {
            if (sqlStatements.isPrimaryKeyViolation(ex)) {
                throw new BranchAlreadyExistsException(ga.getRawGroupIdWithDefaultString(),
                        ga.getRawArtifactId(), branchId.getRawBranchId());
            }
            throw ex;
        }
    }

    @Override
    public void updateBranchMetaData(GA ga, BranchId branchId, EditableBranchMetaDataDto dto) {
        BranchMetaDataDto bmd = getBranchMetaData(ga, branchId);
        if (bmd.isSystemDefined()) {
            throw new NotAllowedException("System generated branches cannot be modified.");
        }

        String modifiedBy = securityIdentity.getPrincipal().getName();
        Date modifiedOn = new Date();
        log.debug("Updating metadata for branch {} of {}/{}.", branchId, ga.getRawGroupIdWithNull(),
                ga.getRawArtifactId());

        handles.withHandleNoException(handle -> {
            // Update the row in the groups table
            int rows = handle.createUpdate(sqlStatements.updateBranch()).bind(0, dto.getDescription())
                    .bind(1, modifiedBy).bind(2, modifiedOn).bind(3, ga.getRawGroupId())
                    .bind(4, ga.getRawArtifactId()).bind(5, branchId.getRawBranchId()).execute();
            if (rows == 0) {
                throw new BranchNotFoundException(ga.getRawGroupIdWithDefaultString(), ga.getRawArtifactId(),
                        branchId.getRawBranchId());
            }

            updateBranchModifiedTimeRaw(handle, ga, branchId);

            return null;
        });
    }

    @Override
    public BranchSearchResultsDto getBranches(GA ga, int offset, int limit) {
        return handles.withHandleNoException(handle -> {
            List<SqlStatementVariableBinder> binders = new LinkedList<>();

            StringBuilder selectTemplate = new StringBuilder();
            StringBuilder where = new StringBuilder();
            StringBuilder orderByQuery = new StringBuilder();
            StringBuilder limitOffset = new StringBuilder();

            // Formulate the SELECT clause for the artifacts query
            selectTemplate.append("SELECT {{selectColumns}} FROM branches b ");

            // Formulate the WHERE clause for both queries
            where.append(" WHERE b.groupId = ? AND b.artifactId = ?");
            binders.add((query, idx) -> {
                query.bind(idx, ga.getRawGroupId());
            });
            binders.add((query, idx) -> {
                query.bind(idx, ga.getRawArtifactId());
            });

            // Add order by to artifact query
            orderByQuery.append(" ORDER BY b.branchId ASC");

            // Add limit and offset to query
            if ("mssql".equals(sqlStatements.dbType())) {
                limitOffset.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            } else {
                limitOffset.append(" LIMIT ? OFFSET ?");
            }

            // Query for the branc
            String branchesQuerySql = new StringBuilder(selectTemplate).append(where).append(orderByQuery)
                    .append(limitOffset).toString().replace("{{selectColumns}}", "*");
            Query branchesQuery = handle.createQuery(branchesQuerySql);
            // Query for the total row count
            String countQuerySql = new StringBuilder(selectTemplate).append(where).toString()
                    .replace("{{selectColumns}}", "count(b.branchId)");
            Query countQuery = handle.createQuery(countQuerySql);

            // Bind all query parameters
            int idx = 0;
            for (SqlStatementVariableBinder binder : binders) {
                binder.bind(branchesQuery, idx);
                binder.bind(countQuery, idx);
                idx++;
            }
            // TODO find a better way to swap arguments
            if ("mssql".equals(sqlStatements.dbType())) {
                branchesQuery.bind(idx++, offset);
                branchesQuery.bind(idx++, limit);
            } else {
                branchesQuery.bind(idx++, limit);
                branchesQuery.bind(idx++, offset);
            }

            // Execute query
            List<SearchedBranchDto> branches = branchesQuery.map(SearchedBranchMapper.instance).list();
            // Execute count query
            Integer count = countQuery.mapTo(Integer.class).one();

            // If no branches are found, it might be because the artifact does not exist. We
            // need to check for that here.
            getArtifactMetaDataRaw(handle, ga.getRawGroupIdWithNull(), ga.getRawArtifactId());

            BranchSearchResultsDto results = new BranchSearchResultsDto();
            results.setBranches(branches);
            results.setCount(count);
            return results;
        });
    }

    @Override
    public BranchMetaDataDto getBranchMetaData(GA ga, BranchId branchId) {
        return handles.withHandle(handle -> {
            Optional<BranchMetaDataDto> res = handle.createQuery(sqlStatements.selectBranch())
                    .bind(0, ga.getRawGroupId()).bind(1, ga.getRawArtifactId())
                    .bind(2, branchId.getRawBranchId()).map(BranchMetaDataDtoMapper.instance).findOne();
            return res.orElseThrow(() -> new BranchNotFoundException(ga.getRawGroupIdWithDefaultString(),
                    ga.getRawArtifactId(), branchId.getRawBranchId()));
        });
    }

    private List<String> getBranchVersionNumbersRaw(Handle handle, GA ga, BranchId branchId) {
        return handle.createQuery(sqlStatements.selectBranchVersionNumbers()).bind(0, ga.getRawGroupId())
                .bind(1, ga.getRawArtifactId()).bind(2, branchId.getRawBranchId()).map(StringMapper.instance)
                .list();
    }

    @Override
    public VersionSearchResultsDto getBranchVersions(GA ga, BranchId branchId, int offset, int limit) {
        return handles.withHandleNoException(handle -> {
            List<SqlStatementVariableBinder> binders = new LinkedList<>();

            StringBuilder selectTemplate = new StringBuilder();
            StringBuilder where = new StringBuilder();
            StringBuilder orderByQuery = new StringBuilder();
            StringBuilder limitOffset = new StringBuilder();

            // Formulate the SELECT clause for the artifacts query
            selectTemplate.append("SELECT {{selectColumns}} FROM branch_versions bv "
                    + "JOIN versions v ON bv.groupId = v.groupId AND bv.artifactId = v.artifactId AND bv.version = v.version "
                    + "JOIN artifacts a ON a.groupId = v.groupId AND a.artifactId = v.artifactId ");

            // Formulate the WHERE clause for both queries
            where.append(" WHERE bv.groupId = ? AND bv.artifactId = ? AND bv.branchId = ?");
            binders.add((query, idx) -> {
                query.bind(idx, ga.getRawGroupId());
            });
            binders.add((query, idx) -> {
                query.bind(idx, ga.getRawArtifactId());
            });
            binders.add((query, idx) -> {
                query.bind(idx, branchId.getRawBranchId());
            });

            // Add order by to artifact query
            orderByQuery.append(" ORDER BY bv.branchOrder DESC");

            // Add limit and offset to artifact query
            if ("mssql".equals(sqlStatements.dbType())) {
                limitOffset.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            } else {
                limitOffset.append(" LIMIT ? OFFSET ?");
            }

            // Query for the versions
            String versionsQuerySql = new StringBuilder(selectTemplate).append(where).append(orderByQuery)
                    .append(limitOffset).toString().replace("{{selectColumns}}", "v.*, a.type");
            Query versionsQuery = handle.createQuery(versionsQuerySql);
            // Query for the total row count
            String countQuerySql = new StringBuilder(selectTemplate).append(where).toString()
                    .replace("{{selectColumns}}", "count(v.globalId)");
            Query countQuery = handle.createQuery(countQuerySql);

            // Bind all query parameters
            int idx = 0;
            for (SqlStatementVariableBinder binder : binders) {
                binder.bind(versionsQuery, idx);
                binder.bind(countQuery, idx);
                idx++;
            }

            // Bind the limit and offset
            // TODO find a better way to swap arguments
            if ("mssql".equals(sqlStatements.dbType())) {
                versionsQuery.bind(idx++, offset);
                versionsQuery.bind(idx, limit);
            } else {
                versionsQuery.bind(idx++, limit);
                versionsQuery.bind(idx, offset);
            }

            // Execute query
            List<SearchedVersionDto> versions = versionsQuery.map(SearchedVersionMapper.instance).list();
            limitReturnedLabelsInVersions(versions);
            // Execute count query
            Integer count = countQuery.mapTo(Integer.class).one();

            VersionSearchResultsDto results = new VersionSearchResultsDto();
            results.setVersions(versions);
            results.setCount(count);
            return results;
        });
    }

    @Override
    public void appendVersionToBranch(GA ga, BranchId branchId, VersionId version) {
        BranchMetaDataDto bmd = getBranchMetaData(ga, branchId);
        if (bmd.isSystemDefined()) {
            throw new NotAllowedException("System generated branches cannot be modified.");
        }

        try {
            handles.withHandle(handle -> {
                appendVersionToBranchRaw(handle, ga, branchId, version);
                updateBranchModifiedTimeRaw(handle, ga, branchId);
                return null;
            });
        } catch (Exception ex) {
            if (sqlStatements.isPrimaryKeyViolation(ex)) {
                throw new VersionAlreadyExistsOnBranchException(ga.getRawGroupIdWithDefaultString(),
                        ga.getRawArtifactId(), version.getRawVersionId(), branchId.getRawBranchId());
            }
            throw ex;
        }
    }

    private void appendVersionToBranchRaw(Handle handle, GA ga, BranchId branchId, VersionId version) {
        try {
            // Insert a row into the branch_versions table
            handle.createUpdate(sqlStatements.appendBranchVersion()).bind(0, ga.getRawGroupId())
                    .bind(1, ga.getRawArtifactId()).bind(2, branchId.getRawBranchId())
                    .bind(3, version.getRawVersionId()).bind(4, ga.getRawGroupId())
                    .bind(5, ga.getRawArtifactId()).bind(6, branchId.getRawBranchId()).execute();
        } catch (Exception ex) {
            if (sqlStatements.isPrimaryKeyViolation(ex)) {
                throw new VersionAlreadyExistsOnBranchException(ga.getRawGroupIdWithDefaultString(),
                        ga.getRawArtifactId(), version.getRawVersionId(), branchId.getRawBranchId());
            }
            if (sqlStatements.isForeignKeyViolation(ex)) {
                throw new VersionNotFoundException(ga.getRawGroupIdWithDefaultString(), ga.getRawArtifactId(),
                        version.getRawVersionId());
            }
            throw ex;
        }
    }

    @Override
    public void replaceBranchVersions(GA ga, BranchId branchId, List<VersionId> versions) {
        BranchMetaDataDto bmd = getBranchMetaData(ga, branchId);
        if (bmd.isSystemDefined()) {
            throw new NotAllowedException("System generated branches cannot be modified.");
        }

        handles.withHandle(handle -> {
            // Delete all previous versions.
            handle.createUpdate(sqlStatements.deleteBranchVersions()).bind(0, ga.getRawGroupId())
                    .bind(1, ga.getRawArtifactId()).bind(2, branchId.getRawBranchId()).execute();

            // Insert each version new
            int branchOrder = 0;
            for (VersionId version : versions) {
                handle.createUpdate(sqlStatements.insertBranchVersion()).bind(0, ga.getRawGroupId())
                        .bind(1, ga.getRawArtifactId()).bind(2, branchId.getRawBranchId())
                        .bind(3, branchOrder++).bind(4, version.getRawVersionId()).execute();
            }

            // Update branch modified time
            updateBranchModifiedTimeRaw(handle, ga, branchId);

            return null;
        });
    }

    /**
     * This method ensures that the named branch exists for the version *and* also adds the version to that
     * branch.
     */
    private void createOrUpdateBranchRaw(Handle handle, GAV gav, BranchId branchId, boolean systemDefined) {
        // First make sure the branch exists.
        try {
            String user = securityIdentity.getPrincipal().getName();
            Date now = new Date();

            handle.createUpdate(sqlStatements.upsertBranch()).bind(0, gav.getRawGroupId())
                    .bind(1, gav.getRawArtifactId()).bind(2, branchId.getRawBranchId()).bind(3, (String) null)
                    .bind(4, systemDefined).bind(5, user).bind(6, now).bind(7, user).bind(8, now).execute();
        } catch (Exception ex) {
            // Only needed for H2, which doesn't support upsert (check this, it might now)
            if (!sqlStatements.isPrimaryKeyViolation(ex)) {
                throw ex;
            }
        }

        // Now add the version to it.
        appendVersionToBranchRaw(handle, gav, branchId, gav.getVersionId());
    }

    /**
     * Removes a version from the given branch.
     *
     * @param handle
     * @param gav
     * @param branchId
     */
    private void removeVersionFromBranchRaw(Handle handle, GAV gav, BranchId branchId) {
        handle.createUpdate(sqlStatements.deleteVersionFromBranch()).bind(0, gav.getRawGroupIdWithNull())
                .bind(1, gav.getRawArtifactId()).bind(2, branchId.getRawBranchId())
                .bind(3, gav.getRawVersionId()).execute();
    }

    private void updateBranchModifiedTimeRaw(Handle handle, GA ga, BranchId branchId) {
        String user = securityIdentity.getPrincipal().getName();
        Date now = new Date();

        handle.createUpdate(sqlStatements.updateBranchModifiedTime()).bind(0, user).bind(1, now)
                .bind(2, ga.getRawGroupId()).bind(3, ga.getRawArtifactId()).bind(4, branchId.getRawBranchId())
                .execute();
    }

    @Override
    public GAV getBranchTip(GA ga, BranchId branchId, Set<VersionState> filterBy) {
        return handles.withHandleNoException(handle -> {
            String sql = sqlStatements.selectBranchTip();
            if (filterBy != null && !filterBy.isEmpty()) {
                sql = sqlStatements.selectBranchTipFilteredByState();
                String jclause = filterBy.stream().map(vs -> "'" + vs.name() + "'")
                        .collect(Collectors.joining(",", "(", ")"));
                sql = sql.replace("(?)", jclause);
            }
            return handle.createQuery(sql).bind(0, ga.getRawGroupId()).bind(1, ga.getRawArtifactId())
                    .bind(2, branchId.getRawBranchId()).map(GAVMapper.instance).findOne()
                    .orElseThrow(() -> new VersionNotFoundException(ga.getRawGroupIdWithDefaultString(),
                            ga.getRawArtifactId(),
                            "<tip of the branch '" + branchId.getRawBranchId() + "'>"));
        });
    }

    private GAV getGAVByGlobalIdRaw(Handle handle, long globalId) {
        return handle.createQuery(sqlStatements.selectGAVByGlobalId()).bind(0, globalId)
                .map(GAVMapper.instance).findOne().orElseThrow(() -> new VersionNotFoundException(globalId));
    }

    @Override
    public void deleteBranch(GA ga, BranchId branchId) {
        BranchMetaDataDto bmd = getBranchMetaData(ga, branchId);
        if (bmd.isSystemDefined()) {
            throw new NotAllowedException("System generated branches cannot be deleted.");
        }

        handles.withHandleNoException(handle -> {
            // First delete all branch versions (only needed for "mssql" due to cascade limitations there).
            if (isMssql()) {
                handle.createUpdate(sqlStatements.deleteBranchVersions()).bind(0, ga.getRawGroupId())
                        .bind(1, ga.getRawArtifactId()).bind(2, branchId.getRawBranchId()).execute();
            }

            var affected = handle.createUpdate(sqlStatements.deleteBranch()).bind(0, ga.getRawGroupId())
                    .bind(1, ga.getRawArtifactId()).bind(2, branchId.getRawBranchId()).execute();

            if (affected == 0) {
                throw new BranchNotFoundException(ga.getRawGroupIdWithDefaultString(), ga.getRawArtifactId(),
                        branchId.getRawBranchId());
            }
        });
    }

    @Override
    public void importBranch(BranchEntity entity) {
        var ga = entity.toGA();
        var branchId = entity.toBranchId();
        handles.withHandleNoException(handle -> {
            if (!isArtifactExistsRaw(handle, entity.groupId, entity.artifactId)) {
                throw new ArtifactNotFoundException(ga.getRawGroupIdWithDefaultString(),
                        ga.getRawArtifactId());
            }
            handle.createUpdate(sqlStatements.importBranch()).bind(0, ga.getRawGroupId())
                    .bind(1, ga.getRawArtifactId()).bind(2, branchId.getRawBranchId())
                    .bind(3, entity.description).bind(4, entity.systemDefined).bind(5, entity.owner)
                    .bind(6, new Date(entity.createdOn)).bind(7, entity.modifiedBy)
                    .bind(8, new Date(entity.modifiedOn)).execute();

            // Append each of the versions onto the branch
            if (entity.versions != null) {
                entity.versions.forEach(version -> {
                    appendVersionToBranchRaw(handle, ga, branchId, new VersionId(version));
                });
            }
        });
    }

    @Override
    public String triggerSnapshotCreation() throws RegistryStorageException {
        throw new RegistryStorageException(
                "Directly triggering the snapshot creation is not supported for sql storages.");
    }

    @Override
    public String createSnapshot(String location) throws RegistryStorageException {
        if (!StringUtil.isEmpty(location)) {
            log.debug("Creating internal database snapshot to location {}.", location);
            handles.withHandleNoException(handle -> {
                handle.createQuery(sqlStatements.createDataSnapshot()).bind(0, location).mapTo(String.class)
                        .first();
            });
            return location;
        } else {
            log.warn("Skipping database snapshot because no location has been provided");
        }
        return null;
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
