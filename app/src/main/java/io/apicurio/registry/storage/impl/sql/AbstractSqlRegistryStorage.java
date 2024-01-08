package io.apicurio.registry.storage.impl.sql;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.Info;
import io.apicurio.common.apps.core.System;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.exception.UnreachableCodeException;
import io.apicurio.registry.storage.*;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.storage.error.*;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.Query;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.storage.impl.sql.mappers.*;
import io.apicurio.registry.storage.importing.DataImporter;
import io.apicurio.registry.storage.importing.SqlDataImporter;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.DtoUtil;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.impexp.*;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.apicurio.registry.storage.RegistryStorage.ArtifactRetrievalBehavior.DEFAULT;
import static io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils.notEmpty;
import static io.apicurio.registry.storage.impl.sql.SqlUtil.convert;
import static io.apicurio.registry.storage.impl.sql.SqlUtil.normalizeGroupId;
import static io.apicurio.registry.utils.StringUtil.limitStr;
import static java.util.stream.Collectors.toList;


/**
 * A SQL implementation of the {@link RegistryStorage} interface.  This impl does not
 * use any ORM technology - it simply uses native SQL for all operations.
 *
 */
public abstract class AbstractSqlRegistryStorage implements RegistryStorage {

    private static int DB_VERSION = Integer.valueOf(
            IoUtil.toString(AbstractSqlRegistryStorage.class.getResourceAsStream("db-version"))).intValue();
    private static final Object inmemorySequencesMutex = new Object();

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, true);
    }

    private static final String GLOBAL_ID_SEQUENCE = "globalId";
    private static final String CONTENT_ID_SEQUENCE = "contentId";
    private static final String COMMENT_ID_SEQUENCE = "commentId";

    @Inject
    Logger log;

    @Inject
    System system;

    @Inject
    SqlStatements sqlStatements;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    ArtifactStateExt artifactStateEx;

    HandleFactory handles;

    @Inject
    StorageBehaviorProperties storageBehaviorProps;

    @Inject
    RegistryStorageContentUtils utils;

    protected SqlStatements sqlStatements() {
        return sqlStatements;
    }

    @ConfigProperty(name = "registry.sql.init", defaultValue = "true")
    @Info(category = "storage", description = "SQL init", availableSince = "2.0.0.Final")
    boolean initDB;

    @Inject
    Event<SqlStorageEvent> sqlStorageEvent;

    @Inject
    Event<StorageEvent> storageEvent;

    private volatile boolean isReady = false;
    private volatile Instant isAliveLastCheck = Instant.MIN;
    private volatile boolean isAliveCached = false;

    /**
     * @param emitStorageReadyEvent The concrete implementation needs to tell AbstractSqlRegistryStorage
     *                              whether it should fire {@see io.apicurio.registry.storage.StorageEvent} in addition to
     *                              {@see io.apicurio.registry.storage.impl.sql.SqlStorageEvent}. Multiple storage implementations
     *                              may be present at the same time (in particular when using KafkaSQL persistence),
     *                              but only the single {@see io.apicurio.registry.types.Current} one may fire the former event.
     */
    @Transactional
    protected void initialize(HandleFactory handleFactory, boolean emitStorageReadyEvent) {
        this.handles = handleFactory;

        log.info("SqlRegistryStorage constructed successfully.");

        handles.withHandleNoException((handle) -> {
            if (initDB) {
                if (!isDatabaseInitialized(handle)) {
                    log.info("Database not initialized.");
                    initializeDatabase(handle);
                } else {
                    log.info("Database was already initialized, skipping.");
                }

                if (!isDatabaseCurrent(handle)) {
                    log.info("Old database version detected, upgrading.");
                    upgradeDatabase(handle);
                }
            } else {
                if (!isDatabaseInitialized(handle)) {
                    log.error("Database not initialized.  Please use the DDL scripts to initialize the database before starting the application.");
                    throw new RuntimeException("Database not initialized.");
                }

                if (!isDatabaseCurrent(handle)) {
                    log.error("Detected an old version of the database.  Please use the DDL upgrade scripts to bring your database up to date.");
                    throw new RuntimeException("Database not upgraded.");
                }
            }
            return null;
        });

        isReady = true;
        SqlStorageEvent initializeEvent = new SqlStorageEvent();
        initializeEvent.setType(SqlStorageEventType.READY);
        sqlStorageEvent.fire(initializeEvent);
        if (emitStorageReadyEvent) {
            /* In cases where the observer of the event also injects the source bean,
             * such as the io.apicurio.registry.ImportLifecycleBean,
             * a kind of recursion may happen.
             * This is because the event is fired in the @PostConstruct method,
             * and is being processed in the same thread.
             * We avoid this by processing the event asynchronously.
             * Note that this requires the jakarta.enterprise.event.ObservesAsync
             * annotation on the receiving side. If this becomes cumbersome,
             * try using ManagedExecutor.
             */
            storageEvent.fireAsync(StorageEvent.builder()
                    .type(StorageEventType.READY)
                    .build());
        }
    }

    /**
     * @return true if the database has already been initialized
     */
    private boolean isDatabaseInitialized(Handle handle) {
        log.info("Checking to see if the DB is initialized.");
        int count = handle.createQuery(this.sqlStatements.isDatabaseInitialized()).mapTo(Integer.class).one();
        return count > 0;
    }

    /**
     * @return true if the database has already been initialized
     */
    private boolean isDatabaseCurrent(Handle handle) {
        log.info("Checking to see if the DB is up-to-date.");
        log.info("Build's DB version is {}", DB_VERSION);
        int version = this.getDatabaseVersion(handle);
        return version == DB_VERSION;
    }

    private void initializeDatabase(Handle handle) {
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
     * Upgrades the database by executing a number of DDL statements found in DB-specific
     * DDL upgrade scripts.
     */
    private void upgradeDatabase(Handle handle) {
        log.info("Upgrading the Apicurio Hub API database.");

        int fromVersion = this.getDatabaseVersion(handle);
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
                applyUpgrader(handle, cname);
            } else {
                handle.createUpdate(statement).execute();
            }
        });
        log.debug("---");
    }

    /**
     * Instantiates an instance of the given upgrader class and then invokes it.  Used to perform
     * advanced upgrade logic when upgrading the DB (logic that cannot be handled in simple SQL
     * statements).
     *
     * @param handle
     * @param cname
     */
    private void applyUpgrader(Handle handle, String cname) {
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
    private int getDatabaseVersion(Handle handle) {
        try {
            int version = handle.createQuery(this.sqlStatements.getDatabaseVersion())
                    .bind(0, "db_version")
                    .mapTo(Integer.class)
                    .one();
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
        if (Instant.now().isAfter(isAliveLastCheck.plus(Duration.ofSeconds(2)))) { // Tradeoff between reducing load on the DB and responsiveness: 2s
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
    @Transactional
    public ContentWrapperDto getArtifactByContentId(long contentId) throws ContentNotFoundException, RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            Optional<ContentWrapperDto> res = handle.createQuery(sqlStatements().selectContentById())
                    .bind(0, contentId)
                    .map(ContentMapper.instance)
                    .findFirst();
            return res.orElseThrow(() -> new ContentNotFoundException(contentId));
        });
    }


    @Override
    @Transactional
    public ContentWrapperDto getArtifactByContentHash(String contentHash) throws ContentNotFoundException, RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            Optional<ContentWrapperDto> res = handle.createQuery(sqlStatements().selectContentByContentHash())
                    .bind(0, contentHash)
                    .map(ContentMapper.instance)
                    .findFirst();
            return res.orElseThrow(() -> new ContentNotFoundException(contentHash));
        });
    }


    @Override
    @Transactional
    public List<ArtifactMetaDataDto> getArtifactVersionsByContentId(long contentId) {
        return handles.withHandleNoException(handle -> {
            List<ArtifactMetaDataDto> dtos = handle.createQuery(sqlStatements().selectArtifactVersionMetaDataByContentId())
                    .bind(0, contentId)
                    .map(ArtifactMetaDataDtoMapper.instance)
                    .list();
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

            return handle.createQuery(sql)
                    .bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .mapTo(Long.class)
                    .list();
        });
    }


    @Override
    @Transactional
    public List<Long> getArtifactContentIds(String groupId, String artifactId) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectArtifactContentIds())
                    .bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .mapTo(Long.class)
                    .list();
        });
    }


    @Override
    @Transactional
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state) throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Updating the state of artifact {} {} to {}", groupId, artifactId, state.name());
        // We're not skipping the latest artifact version even if it's disabled, so it can be enabled again
        var metadata = getArtifactMetaData(groupId, artifactId, DEFAULT);
        updateArtifactVersionStateRaw(metadata.getGlobalId(), metadata.getState(), state);
    }


    @Override
    @Transactional
    public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Updating the state of artifact {} {}, version {} to {}", groupId, artifactId, version, state.name());
        var metadata = getArtifactVersionMetaData(groupId, artifactId, version);
        updateArtifactVersionStateRaw(metadata.getGlobalId(), metadata.getState(), state);
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private void updateArtifactVersionStateRaw(long globalId, ArtifactState oldState, ArtifactState newState) {
        handles.withHandleNoException(handle -> {
            artifactStateEx.applyState(s -> {
                handle.createUpdate(sqlStatements.updateArtifactVersionState())
                        .bind(0, s.name())
                        .bind(1, globalId)
                        .execute();
            }, oldState, newState);
            return null;
        });
    }


    @Override
    @Transactional
    public ArtifactMetaDataDto createArtifact(String groupId, String artifactId, String version, String artifactType,
                                              ContentHandle content, List<ArtifactReferenceDto> references) throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException {
        return createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, null, references);
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private ArtifactVersionMetaDataDto createArtifactVersionRaw(boolean firstVersion, String groupId, String artifactId, String version,
                                                                String name, String description, List<String> labels,
                                                                Map<String, String> properties, String createdBy, Date createdOn,
                                                                Long contentId, IdGenerator globalIdGenerator) {

        ArtifactState state = ArtifactState.ENABLED;
        String labelsStr = SqlUtil.serializeLabels(labels);
        String propertiesStr = SqlUtil.serializeProperties(properties);

        if (globalIdGenerator == null) {
            globalIdGenerator = this::nextGlobalId;
        }

        Long globalId = globalIdGenerator.generate();

        // Create a row in the "versions" table

        if (firstVersion) {
            if (version == null) {
                version = "1";
            }
            final String finalVersion1 = version; // Lambda requirement
            handles.withHandleNoException(handle -> {

                handle.createUpdate(sqlStatements.insertVersion(true))
                        .bind(0, globalId)
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .bind(3, finalVersion1)
                        .bind(4, state)
                        .bind(5, limitStr(name, 512))
                        .bind(6, limitStr(description, 1024, true))
                        .bind(7, createdBy)
                        .bind(8, createdOn)
                        .bind(9, labelsStr)
                        .bind(10, propertiesStr)
                        .bind(11, contentId)
                        .execute();

                createOrUpdateArtifactBranch(new GAV(groupId, artifactId, finalVersion1), BranchId.LATEST);

                return null;
            });
        } else {
            final String finalVersion2 = version; // Lambda requirement
            handles.withHandleNoException(handle -> {

                handle.createUpdate(sqlStatements.insertVersion(false))
                        .bind(0, globalId)
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .bind(3, finalVersion2)
                        .bind(4, normalizeGroupId(groupId))
                        .bind(5, artifactId)
                        .bind(6, state)
                        .bind(7, limitStr(name, 512))
                        .bind(8, limitStr(description, 1024, true))
                        .bind(9, createdBy)
                        .bind(10, createdOn)
                        .bind(11, labelsStr)
                        .bind(12, propertiesStr)
                        .bind(13, contentId)
                        .execute();

                // If version is null, update the row we just inserted to set the version to the generated versionOrder
                if (finalVersion2 == null) {

                    handle.createUpdate(sqlStatements.autoUpdateVersionForGlobalId())
                            .bind(0, globalId)
                            .execute();
                }

                var gav = getGAVByGlobalId(globalId);
                createOrUpdateArtifactBranch(gav, BranchId.LATEST);

                return null;
            });
        }

        return handles.withHandleNoException(handle -> {

            // Insert labels into the "labels" table
            if (labels != null && !labels.isEmpty()) {
                labels.forEach(label -> {

                    handle.createUpdate(sqlStatements.insertLabel())
                            .bind(0, globalId)
                            .bind(1, limitStr(label.toLowerCase(), 256))
                            .execute();
                });
            }

            // Insert properties into the "properties" table
            if (properties != null && !properties.isEmpty()) {
                properties.forEach((k, v) -> {

                    handle.createUpdate(sqlStatements.insertProperty())
                            .bind(0, globalId)
                            .bind(1, limitStr(k.toLowerCase(), 256))
                            .bind(2, limitStr(v.toLowerCase(), 1024))
                            .execute();
                });
            }

            return handle.createQuery(sqlStatements.selectArtifactVersionMetaDataByGlobalId())
                    .bind(0, globalId)
                    .map(ArtifactVersionMetaDataDtoMapper.instance)
                    .one();

        });
    }


    /**
     * Store the content in the database and return the content ID of the new row.
     * If the content already exists, just return the content ID of the existing row.
     * <p>
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     *
     * @param references may be null
     */
    private Long getOrCreateContent(String artifactType, ContentHandle content, List<ArtifactReferenceDto> references) {
        if (notEmpty(references)) {
            return getOrCreateContentRaw(content,
                    utils.getContentHash(content, references),
                    utils.getCanonicalContentHash(content, artifactType, references, this::resolveReferences),
                    references, SqlUtil.serializeReferences(references));
        } else {
            return getOrCreateContentRaw(content,
                    utils.getContentHash(content, null),
                    utils.getCanonicalContentHash(content, artifactType, null, null),
                    null, null);
        }
    }


    /**
     * Store the content in the database and return the content ID of the new row.
     * If the content already exists, just return the content ID of the existing row.
     * <p>
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private Long getOrCreateContentRaw(ContentHandle content, String contentHash, String canonicalContentHash, List<ArtifactReferenceDto> references, String referencesSerialized) {
        return handles.withHandleNoException(handle -> {
            byte[] contentBytes = content.bytes();

            // Upsert a row in the "content" table.  This will insert a row for the content
            // if a row doesn't already exist.  We use the content hash to determine whether
            // a row for this content already exists.  If we find a row we return its content ID.
            // If we don't find a row, we insert one and then return its content ID.
            Long contentId;
            boolean insertReferences = true;
            if (Set.of("mssql", "postgresql").contains(sqlStatements.dbType())) {

                handle.createUpdate(sqlStatements.upsertContent())
                        .bind(0, nextContentId())
                        .bind(1, canonicalContentHash)
                        .bind(2, contentHash)
                        .bind(3, contentBytes)
                        .bind(4, referencesSerialized)
                        .execute();

                contentId = contentIdFromHash(contentHash)
                        .orElseThrow(() -> new RegistryStorageException("Content hash not found."));

            } else if ("h2".equals(sqlStatements.dbType())) {

                Optional<Long> contentIdOptional = contentIdFromHash(contentHash);

                if (contentIdOptional.isPresent()) {
                    contentId = contentIdOptional.get();
                    //If the content is already present there's no need to create the references.
                    insertReferences = false;
                } else {

                    handle.createUpdate(sqlStatements.upsertContent())
                            .bind(0, nextContentId())
                            .bind(1, canonicalContentHash)
                            .bind(2, contentHash)
                            .bind(3, contentBytes)
                            .bind(4, referencesSerialized)
                            .execute();

                    contentId = contentIdFromHash(contentHash)
                            .orElseThrow(() -> new RegistryStorageException("Content hash not found."));
                }
            } else {
                throw new UnsupportedOperationException("Unsupported database type: " + sqlStatements.dbType());
            }

            if (insertReferences) {
                //Finally, insert references into the "artifactreferences" table if the content wasn't present yet.
                insertReferences(contentId, references);
            }
            return contentId;
        });
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private void insertReferences(Long contentId, List<ArtifactReferenceDto> references) {
        if (references != null && !references.isEmpty()) {
            references.forEach(reference -> {
                handles.withHandleNoException(handle -> {
                    try {
                        handle.createUpdate(sqlStatements.upsertReference())
                                .bind(0, contentId)
                                .bind(1, normalizeGroupId(reference.getGroupId()))
                                .bind(2, reference.getArtifactId())
                                .bind(3, reference.getVersion())
                                .bind(4, reference.getName())
                                .execute();
                    } catch (Exception e) {
                        if (sqlStatements.isPrimaryKeyViolation(e)) {
                            //Do nothing, the reference already exist, only needed for H2
                        } else {
                            throw e;
                        }
                    }
                    return null;
                });
            });
        }
    }


    @Override
    @Transactional
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData,
                                                          List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, ArtifactAlreadyExistsException, RegistryStorageException {

        String createdBy = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();

        if (groupId != null && !isGroupExists(groupId)) {
            //Only create group metadata for non-default groups.
            createGroup(GroupMetaDataDto.builder()
                    .groupId(groupId)
                    .createdOn(createdOn.getTime())
                    .modifiedOn(createdOn.getTime())
                    .createdBy(createdBy)
                    .modifiedBy(createdBy)
                    .build());
        }

        // Put the content in the DB and get the unique content ID back.
        long contentId = getOrCreateContent(artifactType, content, references);

        // If the metaData provided is null, try to figure it out from the content.
        EditableArtifactMetaDataDto md = metaData;
        if (md == null) {
            md = utils.extractEditableArtifactMetadata(artifactType, content);
        }
        // This current method is skipped in KafkaSQL, and the one below is called directly,
        // so references must be added to the metadata there.
        return createArtifactWithMetadataRaw(groupId, artifactId, version, artifactType, contentId, createdBy, createdOn, md, null);
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private ArtifactMetaDataDto createArtifactWithMetadataRaw(String groupId, String artifactId, String version,
                                                              String artifactType, long contentId, String createdBy,
                                                              Date createdOn, EditableArtifactMetaDataDto metaData,
                                                              IdGenerator globalIdGenerator) {
        log.debug("Inserting an artifact row for: {} {}", groupId, artifactId);
        try {
            return handles.withHandle(handle -> {
                // Create a row in the artifacts table.
                handle.createUpdate(sqlStatements.insertArtifact())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, artifactType)
                        .bind(3, createdBy)
                        .bind(4, createdOn)
                        .execute();

                // Then create a row in the content and versions tables (for the content and version meta-data)
                ArtifactVersionMetaDataDto vmdd = createArtifactVersionRaw(true, groupId, artifactId, version,
                        metaData.getName(), metaData.getDescription(), metaData.getLabels(), metaData.getProperties(), createdBy, createdOn,
                        contentId, globalIdGenerator);

                // Get the content, so we can return references in the metadata
                ContentWrapperDto contentDto = getArtifactByContentId(contentId);

                // Return the new artifact meta-data
                ArtifactMetaDataDto amdd = convert(groupId, artifactId, vmdd);
                amdd.setCreatedBy(createdBy);
                amdd.setCreatedOn(createdOn.getTime());
                amdd.setLabels(metaData.getLabels());
                amdd.setProperties(metaData.getProperties());
                amdd.setReferences(contentDto.getReferences());
                return amdd;
            });
        } catch (Exception e) {
            if (sqlStatements.isPrimaryKeyViolation(e)) {
                throw new ArtifactAlreadyExistsException(groupId, artifactId);
            }
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public List<String> deleteArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Deleting an artifact: {} {}", groupId, artifactId);
        try {
            List<String> res = handles.withHandle(handle -> {
                // Get the list of versions of the artifact (will be deleted)

                List<String> versions = handle.createQuery(sqlStatements.selectArtifactVersions())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .mapTo(String.class)
                        .list();

                // TODO use CASCADE when deleting rows from the "versions" table

                // Delete labels
                handle.createUpdate(sqlStatements.deleteLabels())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .execute();

                // Delete properties
                handle.createUpdate(sqlStatements.deleteProperties())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .execute();

                deleteAllBranchesInArtifact(new GA(groupId, artifactId));

                // Delete versions
                handle.createUpdate(sqlStatements.deleteVersions())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .execute();

                // Delete artifact rules
                handle.createUpdate(sqlStatements.deleteArtifactRules())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .execute();

                // Delete artifact row (should be just one)
                int rowCount = handle.createUpdate(sqlStatements.deleteArtifact())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .execute();

                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
                return versions;
            });
            deleteAllOrphanedContent();
            return res;
        } catch (ArtifactNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        log.debug("Deleting all artifacts in group: {}", groupId);
        try {
            handles.withHandle(handle -> {

                // TODO use CASCADE when deleting rows from the "versions" table

                // Delete labels
                handle.createUpdate(sqlStatements.deleteLabelsByGroupId())
                        .bind(0, normalizeGroupId(groupId))
                        .execute();

                // Delete properties
                handle.createUpdate(sqlStatements.deletePropertiesByGroupId())
                        .bind(0, normalizeGroupId(groupId))
                        .execute();

                deleteAllBranchesInGroup(new GroupId(groupId));

                // Delete versions
                handle.createUpdate(sqlStatements.deleteVersionsByGroupId())
                        .bind(0, normalizeGroupId(groupId))
                        .execute();

                // Delete artifact rules
                handle.createUpdate(sqlStatements.deleteArtifactRulesByGroupId())
                        .bind(0, normalizeGroupId(groupId))
                        .execute();

                // Delete artifact row (should be just one)
                int rowCount = handle.createUpdate(sqlStatements.deleteArtifactsByGroupId())
                        .bind(0, normalizeGroupId(groupId))
                        .execute();

                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(groupId, null);
                }
                return null;
            });
            deleteAllOrphanedContent();
        } catch (ArtifactNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    public StoredArtifactDto getArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return getArtifact(groupId, artifactId, storageBehaviorProps.getDefaultArtifactRetrievalBehavior());
    }


    @Override
    @Transactional
    public StoredArtifactDto getArtifact(String groupId, String artifactId, ArtifactRetrievalBehavior behavior)
            throws ArtifactNotFoundException, RegistryStorageException {

        log.debug("Selecting a single artifact (latest version) by artifactId: {} {} (behavior = {})", groupId, artifactId, behavior);

        return handles.withHandle(handle -> {
            var ga = new GA(groupId, artifactId);
            try {
                var gav = getArtifactBranchLeaf(ga, BranchId.LATEST, behavior);

                return handle.createQuery(sqlStatements.selectArtifactVersionContent())
                        .bind(0, gav.getRawGroupId())
                        .bind(1, gav.getRawArtifactId())
                        .bind(2, gav.getRawVersionId())
                        .map(StoredArtifactMapper.instance)
                        .findOne()
                        .orElseThrow(() -> new ArtifactNotFoundException(ga.getRawGroupIdWithDefaultString(), ga.getRawArtifactId()));

            } catch (VersionNotFoundException ex) {
                throw new ArtifactNotFoundException(ga.getRawGroupIdWithDefaultString(), ga.getRawArtifactId(), ex);
            }
        });
    }


    @Override
    public ArtifactMetaDataDto updateArtifact(String groupId, String artifactId, String version, String artifactType,
                                              ContentHandle content, List<ArtifactReferenceDto> references) throws ArtifactNotFoundException, RegistryStorageException {
        return updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, null, references);
    }


    @Override
    @Transactional
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, ContentHandle content,
                                                          EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, RegistryStorageException {


        return updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData, references, null);
    }

    protected ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
                                                             String artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references,
                                                             IdGenerator globalIdGenerator) throws ArtifactNotFoundException, RegistryStorageException {

        String createdBy = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();

        // Put the content in the DB and get the unique content ID back.
        long contentId = handles.withHandleNoException(handle -> {
            return getOrCreateContent(artifactType, content, references);
        });

        // Extract meta-data from the content if no metadata is provided
        if (metaData == null) {
            metaData = utils.extractEditableArtifactMetadata(artifactType, content);
        }

        return updateArtifactWithMetadataRaw(groupId, artifactId, version, contentId, createdBy, createdOn,
                metaData, null);
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private ArtifactMetaDataDto updateArtifactWithMetadataRaw(String groupId, String artifactId, String version,
                                                              long contentId, String createdBy, Date createdOn,
                                                              EditableArtifactMetaDataDto metaData, IdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Updating artifact {} {} with a new version (content).", groupId, artifactId);

        //For the update we want to get meta-data from previous (latest) existing version, no matter the state.
        ArtifactMetaDataDto latest = this.getArtifactMetaData(groupId, artifactId, DEFAULT);

        try {
            // Create version and return
            return handles.withHandle(handle -> {
                // Metadata comes from the latest version
                String name = latest.getName();
                String description = latest.getDescription();
                List<String> labels = latest.getLabels();
                Map<String, String> properties = latest.getProperties();

                // Provided metadata will override inherited values from latest version
                if (metaData.getName() != null) {
                    name = metaData.getName();
                }
                if (metaData.getDescription() != null) {
                    description = metaData.getDescription();
                }
                if (metaData.getLabels() != null) {
                    labels = metaData.getLabels();
                }
                if (metaData.getProperties() != null) {
                    properties = metaData.getProperties();
                }

                // Now create the version and return the new version metadata.
                ArtifactVersionMetaDataDto versionDto = createArtifactVersionRaw(false, groupId, artifactId, version,
                        name, description, labels, properties, createdBy, createdOn, contentId, globalIdGenerator);
                ArtifactMetaDataDto dto = convert(groupId, artifactId, versionDto);
                dto.setCreatedOn(latest.getCreatedOn());
                dto.setCreatedBy(latest.getCreatedBy());
                dto.setLabels(labels);
                dto.setProperties(properties);
                return dto;
            });
        } catch (Exception e) {
            if (sqlStatements.isPrimaryKeyViolation(e)) {
                throw new VersionAlreadyExistsException(groupId, artifactId, version);
            }
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public Set<String> getArtifactIds(Integer limit) { // TODO Paging and order by
        //Set limit to max integer in case limit is null (not allowed)
        final Integer adjustedLimit = limit == null ? Integer.MAX_VALUE : limit;
        log.debug("Getting the set of all artifact IDs");
        return handles.withHandleNoException(handle -> {
            Query query = handle.createQuery(sqlStatements.selectArtifactIds());
            query.bind(0, adjustedLimit);
            return new HashSet<>(query.mapTo(String.class).list());
        });
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     *
     * @param groupId may be null to indicate the default group
     */
    private Set<String> getArtifactIds(String groupId, Integer limit) { // TODO Paging and order by
        //Set limit to max integer in case limit is null (not allowed)
        final Integer adjustedLimit = limit == null ? Integer.MAX_VALUE : limit;
        return handles.withHandleNoException(handle -> {
            Query query = handle.createQuery(sqlStatements.selectArtifactIdsInGroup());
            query.bind(1, adjustedLimit).bind(0, normalizeGroupId(groupId));
            return new HashSet<>(query.mapTo(String.class).list());
        });
    }


    @Override
    @Transactional
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy, OrderDirection orderDirection,
                                                    int offset, int limit) {
        return handles.withHandleNoException(handle -> {
            List<SqlStatementVariableBinder> binders = new LinkedList<>();

            StringBuilder selectTemplate = new StringBuilder();
            StringBuilder where = new StringBuilder();
            StringBuilder orderByQuery = new StringBuilder();
            StringBuilder limitOffset = new StringBuilder();

            // Formulate the SELECT clause for the artifacts query
            selectTemplate.append("SELECT {{selectColumns}} ")
                    .append("FROM artifacts a ")
                    .append("JOIN versions v ON v.groupId = a.groupId AND v.artifactId = a.artifactId ")
                    .append("JOIN artifact_version_branches avb ON avb.groupId = v.groupId AND avb.artifactId = v.artifactId AND avb.branch = '")
                    .append(BranchId.LATEST.getRawBranchId())
                    .append("' AND avb.version = v.version ");

            if (hasContentFilter(filters)) {
                selectTemplate.append("JOIN content c ON v.contentId = c.contentId ");
            }

            // Formulate the WHERE clause for both queries
            where.append("WHERE (avb.branchOrder = (SELECT MAX(branchOrder) FROM artifact_version_branches avb2 WHERE avb2.groupId = avb.groupId AND avb2.artifactId = avb.artifactId AND avb2.branch = avb.branch))");
            for (SearchFilter filter : filters) {
                where.append(" AND (");
                switch (filter.getType()) {
                    case description:
                        where.append("v.description LIKE ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        break;
                    case everything:
                        where.append("v.name LIKE ? OR "
                                + "v.groupId LIKE ? OR "
                                + "a.artifactId LIKE ? OR "
                                + "v.description LIKE ? OR "
                                + "EXISTS(SELECT l.globalId FROM labels l WHERE l.label = ? AND l.globalId = v.globalId) OR "
                                + "EXISTS(SELECT p.globalId FROM properties p WHERE p.pkey = ? AND p.globalId = v.globalId)");
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        binders.add((query, idx) -> {
                            //    Note: convert search to lowercase when searching for labels (case-insensitivity support).
                            query.bind(idx, filter.getStringValue().toLowerCase());
                        });
                        binders.add((query, idx) -> {
                            //    Note: convert search to lowercase when searching for properties (case-insensitivity support).
                            query.bind(idx, filter.getStringValue().toLowerCase());
                        });
                        break;
                    case labels:
                        where.append("EXISTS(SELECT l.globalId FROM labels l WHERE l.label = ? AND l.globalId = v.globalId)");
                        binders.add((query, idx) -> {
                            //    Note: convert search to lowercase when searching for labels (case-insensitivity support).
                            query.bind(idx, filter.getStringValue().toLowerCase());
                        });
                        break;
                    case name:
                        where.append("v.name LIKE ? OR a.artifactId LIKE ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        break;
                    case group:
                        where.append("v.groupId = ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, normalizeGroupId(filter.getStringValue()));
                        });
                        break;
                    case contentHash:
                        where.append("c.contentHash = ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getStringValue());
                        });
                        break;
                    case canonicalHash:
                        where.append("c.canonicalHash = ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getStringValue());
                        });
                        break;
                    case properties:
                        Pair<String, String> property = filter.getPropertyFilterValue();
                        //    Note: convert search to lowercase when searching for properties (case-insensitivity support).
                        String propKey = property.getKey().toLowerCase();
                        where.append("EXISTS(SELECT p.globalId FROM properties p WHERE p.pkey = ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, propKey);
                        });
                        if (property.getValue() != null) {
                            String propValue = property.getValue().toLowerCase();
                            where.append(" AND p.pvalue = ?");
                            binders.add((query, idx) -> {
                                query.bind(idx, propValue);
                            });
                        }
                        where.append(" AND p.globalId = v.globalId)");
                        break;
                    case globalId:
                        where.append("v.globalId = ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getNumberValue().longValue());
                        });
                        break;
                    case contentId:
                        where.append("v.contentId = ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getNumberValue().longValue());
                        });
                        break;
                }
                where.append(")");
            }

            // Add order by to artifact query
            switch (orderBy) {
                case name:
                    orderByQuery.append(" ORDER BY coalesce(v.name, a.artifactId)");
                    break;
                case createdOn:
                    orderByQuery.append(" ORDER BY v.createdOn");
                    break;
                case globalId:
                    orderByQuery.append(" ORDER BY v.globalId");
                    break;
            }
            orderByQuery.append(" ").append(orderDirection.name());

            // Add limit and offset to artifact query
            if ("mssql".equals(sqlStatements.dbType())) {
                limitOffset.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            } else {
                limitOffset.append(" LIMIT ? OFFSET ?");
            }

            // Query for the artifacts
            String artifactsQuerySql = new StringBuilder(selectTemplate)
                    .append(where)
                    .append(orderByQuery)
                    .append(limitOffset)
                    .toString()
                    .replace("{{selectColumns}}", "a.*, v.globalId, v.version, v.state, v.name, v.description, v.labels, v.properties, v.createdBy AS modifiedBy, v.createdOn AS modifiedOn");
            Query artifactsQuery = handle.createQuery(artifactsQuerySql);
            String countQuerySql = new StringBuilder(selectTemplate)
                    .append(where)
                    .toString()
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
        return getArtifactMetaData(groupId, artifactId, storageBehaviorProps.getDefaultArtifactRetrievalBehavior());
    }


    @Override
    @Transactional
    public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId, ArtifactRetrievalBehavior behavior) {

        log.debug("Selecting artifact (latest version) meta-data: {} {} (behavior = {})", groupId, artifactId, behavior);

        return handles.withHandle(handle -> {
            var ga = new GA(groupId, artifactId);
            try {
                var gav = getArtifactBranchLeaf(ga, BranchId.LATEST, behavior);

                return handle.createQuery(sqlStatements.selectArtifactMetaData())
                        .bind(0, gav.getRawGroupId())
                        .bind(1, gav.getRawArtifactId())
                        .bind(2, gav.getRawVersionId())
                        .map(ArtifactMetaDataDtoMapper.instance)
                        .findOne()
                        .orElseThrow(() -> new ArtifactNotFoundException(ga.getRawGroupIdWithDefaultString(), ga.getRawArtifactId()));

            } catch (VersionNotFoundException ex) {
                throw new ArtifactNotFoundException(ga.getRawGroupIdWithDefaultString(), ga.getRawArtifactId(), ex);
            } catch (ArtifactNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new RegistryStorageException(e);
            }
        });
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     *
     * @param references may be null
     */
    private String getContentHash(String groupId, String artifactId, boolean canonical,
                                  ContentHandle content, List<ArtifactReferenceDto> references) {
        if (canonical) {
            var artifactMetaData = getArtifactMetaData(groupId, artifactId);
            return utils.getCanonicalContentHash(content, artifactMetaData.getType(),
                    references, this::resolveReferences);
        } else {
            return utils.getContentHash(content, references);
        }
    }


    /**
     * @param references may be null
     */
    @Override
    @Transactional
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, boolean canonical,
                                                                 ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, RegistryStorageException {

        String hash = getContentHash(groupId, artifactId, canonical, content, references);

        try {
            return handles.withHandle(handle -> {
                String sql;
                if (canonical) {
                    sql = sqlStatements.selectArtifactVersionMetaDataByCanonicalHash();
                } else {
                    sql = sqlStatements.selectArtifactVersionMetaDataByContentHash();
                }
                Optional<ArtifactVersionMetaDataDto> res = handle.createQuery(sql)
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, hash)
                        .map(ArtifactVersionMetaDataDtoMapper.instance)
                        .findFirst();
                return res.orElseThrow(() -> new ArtifactNotFoundException(groupId, artifactId));
            });
        } catch (ArtifactNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public ArtifactMetaDataDto getArtifactMetaData(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting meta-data for globalId: {}", globalId);
        try {
            return handles.withHandle(handle -> {
                Optional<ArtifactMetaDataDto> res = handle.createQuery(sqlStatements.selectArtifactMetaDataByGlobalId())
                        .bind(0, globalId)
                        .map(ArtifactMetaDataDtoMapper.instance)
                        .findOne();
                return res.orElseThrow(() -> new ArtifactNotFoundException(null, String.valueOf(globalId)));
            });
        } catch (ArtifactNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto editableMetadata)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Updating meta-data for an artifact: {} {}", groupId, artifactId);

        var metadata = getArtifactMetaData(groupId, artifactId, storageBehaviorProps.getDefaultArtifactRetrievalBehavior());
        updateArtifactVersionMetadataRaw(metadata.getGlobalId(), groupId, artifactId, metadata.getVersion(), editableMetadata);
    }


    @Override
    @Transactional
    public void updateArtifactOwner(String groupId, String artifactId, ArtifactOwnerDto owner)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Updating ownership of an artifact: {} {}", groupId, artifactId);

        try {
            handles.withHandle(handle -> {
                int rowCount = handle.createUpdate(sqlStatements.updateArtifactOwner())
                        .bind(0, owner.getOwner())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .execute();
                if (rowCount == 0) {
                    if (!isArtifactExists(groupId, artifactId)) {
                        throw new ArtifactNotFoundException(groupId, artifactId);
                    }
                    // Likely someone tried to set the owner to the same value.  That is
                    // not an error.  No need to throw.
                }
                return null;
            });
        } catch (RegistryStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public List<RuleType> getArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting a list of all artifact rules for: {} {}", groupId, artifactId);
        try {
            return handles.withHandle(handle -> {
                List<RuleType> rules = handle.createQuery(sqlStatements.selectArtifactRules())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .map(new RowMapper<RuleType>() {
                            @Override
                            public RuleType map(ResultSet rs) throws SQLException {
                                return RuleType.fromValue(rs.getString("type"));
                            }
                        })
                        .list();
                if (rules.isEmpty()) {
                    if (!isArtifactExists(groupId, artifactId)) {
                        throw new ArtifactNotFoundException(groupId, artifactId);
                    }
                }
                return rules;
            });
        } catch (ArtifactNotFoundException anfe) {
            throw anfe;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void createArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        log.debug("Inserting an artifact rule row for artifact: {} {} rule: {}", groupId, artifactId, rule.name());
        try {
            handles.withHandle(handle -> {
                handle.createUpdate(sqlStatements.insertArtifactRule())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, rule.name())
                        .bind(3, config.getConfiguration())
                        .execute();
                return null;
            });
        } catch (Exception e) {
            if (sqlStatements.isPrimaryKeyViolation(e)) {
                throw new RuleAlreadyExistsException(rule);
            }
            if (sqlStatements.isForeignKeyViolation(e)) {
                throw new ArtifactNotFoundException(groupId, artifactId, e);
            }
            throw new RegistryStorageException(e);
        }
        log.debug("Artifact rule row successfully inserted.");
    }


    @Override
    @Transactional
    public void deleteArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Deleting all artifact rules for artifact: {} {}", groupId, artifactId);
        try {
            handles.withHandle(handle -> {
                int count = handle.createUpdate(sqlStatements.deleteArtifactRules())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .execute();
                if (count == 0) {
                    if (!isArtifactExists(groupId, artifactId)) {
                        throw new ArtifactNotFoundException(groupId, artifactId);
                    }
                }
                return null;
            });
        } catch (RegistryStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact rule for artifact: {} {} and rule: {}", groupId, artifactId, rule.name());
        try {
            return handles.withHandle(handle -> {
                Optional<RuleConfigurationDto> res = handle.createQuery(sqlStatements.selectArtifactRuleByType())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, rule.name())
                        .map(RuleConfigurationDtoMapper.instance)
                        .findOne();
                return res.orElseThrow(() -> {
                    if (!isArtifactExists(groupId, artifactId)) {
                        return new ArtifactNotFoundException(groupId, artifactId);
                    }
                    return new RuleNotFoundException(rule);
                });
            });
        } catch (ArtifactNotFoundException | RuleNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Updating an artifact rule for artifact: {} {} and rule: {}::{}", groupId, artifactId, rule.name(), config.getConfiguration());
        try {
            handles.withHandle(handle -> {
                int rowCount = handle.createUpdate(sqlStatements.updateArtifactRule())
                        .bind(0, config.getConfiguration())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .bind(3, rule.name())
                        .execute();
                if (rowCount == 0) {
                    if (!isArtifactExists(groupId, artifactId)) {
                        throw new ArtifactNotFoundException(groupId, artifactId);
                    }
                    throw new RuleNotFoundException(rule);
                }
                return null;
            });
        } catch (RegistryStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Deleting an artifact rule for artifact: {} {} and rule: {}", groupId, artifactId, rule.name());
        try {
            handles.withHandle(handle -> {
                int rowCount = handle.createUpdate(sqlStatements.deleteArtifactRule())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, rule.name())
                        .execute();
                if (rowCount == 0) {
                    if (!isArtifactExists(groupId, artifactId)) {
                        throw new ArtifactNotFoundException(groupId, artifactId);
                    }
                    throw new RuleNotFoundException(rule);
                }
                return null;
            });
        } catch (RegistryStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public List<String> getArtifactVersions(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return getArtifactVersions(groupId, artifactId, storageBehaviorProps.getDefaultArtifactRetrievalBehavior());
    }


    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId, ArtifactRetrievalBehavior behavior)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting a list of versions for artifact: {} {}", groupId, artifactId);

        try {
            return getArtifactBranch(new GA(groupId, artifactId), BranchId.LATEST, behavior)
                    .stream()
                    .map(GAV::getRawVersionId)
                    .collect(toList());
        } catch (BranchNotFoundException ex) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
    }


    @Override
    @Transactional
    public VersionSearchResultsDto searchVersions(String groupId, String artifactId, int offset, int limit) { // TODO: Rename to differentiate from other search* methods.
        log.debug("Searching for versions of artifact {} {}", groupId, artifactId);
        return handles.withHandleNoException(handle -> {
            VersionSearchResultsDto rval = new VersionSearchResultsDto();

            Integer count = handle.createQuery(sqlStatements.selectAllArtifactVersionsCount())
                    .bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .mapTo(Integer.class)
                    .one();
            rval.setCount(count);

            if (!isArtifactExists(groupId, artifactId)) {
                throw new ArtifactNotFoundException(groupId, artifactId);
            }

            Query query = handle.createQuery(sqlStatements.selectAllArtifactVersions())
                    .bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId);
            if ("mssql".equals(sqlStatements.dbType())) {
                query
                        .bind(2, offset)
                        .bind(3, limit);
            } else {
                query
                        .bind(2, limit)
                        .bind(3, offset);
            }
            List<SearchedVersionDto> versions = query
                    .map(SearchedVersionMapper.instance)
                    .list();
            rval.setVersions(versions);

            return rval;
        });
    }


    @Override
    @Transactional
    public StoredArtifactDto getArtifactVersion(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact version by globalId: {}", globalId);
        try {
            return handles.withHandle(handle -> {
                Optional<StoredArtifactDto> res = handle.createQuery(sqlStatements.selectArtifactVersionContentByGlobalId())
                        .bind(0, globalId)
                        .map(StoredArtifactMapper.instance)
                        .findOne();
                return res.orElseThrow(() -> new ArtifactNotFoundException(null, "gid-" + globalId));
            });
        } catch (ArtifactNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public StoredArtifactDto getArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact version by artifactId: {} {} and version {}", groupId, artifactId, version);
        try {
            return handles.withHandle(handle -> {
                Optional<StoredArtifactDto> res = handle.createQuery(sqlStatements.selectArtifactVersionContent())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, version)
                        .map(StoredArtifactMapper.instance)
                        .findOne();
                return res.orElseThrow(() -> new ArtifactNotFoundException(groupId, artifactId));
            });
        } catch (ArtifactNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void deleteArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Deleting version {} of artifact {} {}", version, groupId, artifactId);

        //For deleting artifact versions we need to list always every single version, including disabled ones.
        List<String> versions = getArtifactVersions(groupId, artifactId, DEFAULT);

        // If the version we're deleting is the *only* version, then just delete the
        // entire artifact.
        if (versions.size() == 1 && versions.iterator().next().equals(version)) {
            deleteArtifact(groupId, artifactId);
            return;
        }

        // If there is only one version, but it's not the version being deleted, then
        // we can't find the version to delete!  This is an optimization.
        if (versions.size() == 1 && !versions.iterator().next().equals(version)) {
            throw new VersionNotFoundException(groupId, artifactId, version);
        }

        try {
            handles.withHandle(handle -> {

                // TODO use CASCADE when deleting rows from the "versions" table

                // Delete labels
                handle.createUpdate(sqlStatements.deleteVersionLabels())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, version)
                        .execute();

                // Delete properties
                handle.createUpdate(sqlStatements.deleteVersionProperties())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, version)
                        .execute();

                // Delete comments
                handle.createUpdate(sqlStatements.deleteVersionComments())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, version)
                        .execute();

                // Delete version in branches
                handle.createUpdate(sqlStatements.deleteVersionInBranches())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, version)
                        .execute();

                // Delete version
                int rows = handle.createUpdate(sqlStatements.deleteVersion())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, version)
                        .execute();

                if (rows == 0) {
                    throw new VersionNotFoundException(groupId, artifactId, version);
                }

                if (rows > 1) {
                    // How would this even happen?
                    throw new UnreachableCodeException();
                }

                return null;
            });
            deleteAllOrphanedContent();
        } catch (VersionNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, String version) {
        try {
            return handles.withHandle(handle -> {
                Optional<ArtifactVersionMetaDataDto> res = handle.createQuery(sqlStatements.selectArtifactVersionMetaData())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, version)
                        .map(ArtifactVersionMetaDataDtoMapper.instance)
                        .findOne();
                return res.orElseThrow(() -> new VersionNotFoundException(groupId, artifactId, version));
            });
        } catch (VersionNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableArtifactMetaDataDto editableMetadata)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Updating meta-data for an artifact version: {} {}", groupId, artifactId);

        var metadata = getArtifactVersionMetaData(groupId, artifactId, version);
        updateArtifactVersionMetadataRaw(metadata.getGlobalId(), groupId, artifactId, version, editableMetadata);
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private void updateArtifactVersionMetadataRaw(long globalId, String groupId, String artifactId, String version, EditableArtifactMetaDataDto metaData) {
        try {
            handles.withHandle(handle -> {
                int rowCount = handle.createUpdate(sqlStatements.updateArtifactVersionMetaData())
                        .bind(0, limitStr(metaData.getName(), 512))
                        .bind(1, limitStr(metaData.getDescription(), 1024, true))
                        .bind(2, SqlUtil.serializeLabels(metaData.getLabels()))
                        .bind(3, SqlUtil.serializeProperties(metaData.getProperties()))
                        .bind(4, normalizeGroupId(groupId))
                        .bind(5, artifactId)
                        .bind(6, version)
                        .execute();
                if (rowCount == 0) {
                    throw new VersionNotFoundException(groupId, artifactId, version);
                }


                // Delete all appropriate rows in the "labels" table
                handle.createUpdate(sqlStatements.deleteLabelsByGlobalId())
                        .bind(0, globalId)
                        .execute();

                // Delete all appropriate rows in the "properties" table
                handle.createUpdate(sqlStatements.deletePropertiesByGlobalId())
                        .bind(0, globalId)
                        .execute();

                // Insert new labels into the "labels" table
                List<String> labels = metaData.getLabels();
                if (labels != null && !labels.isEmpty()) {
                    labels.forEach(label -> {
                        String sqli = sqlStatements.insertLabel();
                        handle.createUpdate(sqli)
                                .bind(0, globalId)
                                .bind(1, limitStr(label.toLowerCase(), 256))
                                .execute();
                    });
                }

                // Insert new properties into the "properties" table
                Map<String, String> properties = metaData.getProperties();
                if (properties != null && !properties.isEmpty()) {
                    properties.forEach((k, v) -> {
                        String sqli = sqlStatements.insertProperty();
                        handle.createUpdate(sqli)
                                .bind(0, globalId)
                                .bind(1, limitStr(k.toLowerCase(), 256))
                                .bind(2, limitStr(v.toLowerCase(), 1024))
                                .execute();
                    });
                }

                return null;
            });
        } catch (ArtifactNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Deleting user-defined meta-data for artifact {} {} version {}", groupId, artifactId, version);
        try {
            handles.withHandle(handle -> {
                // NULL out the name, description, labels, and properties columns of the "versions" table.
                int rowCount = handle.createUpdate(sqlStatements.updateArtifactVersionMetaData())
                        .bind(0, (String) null)
                        .bind(1, (String) null)
                        .bind(2, (String) null)
                        .bind(3, (String) null)
                        .bind(4, normalizeGroupId(groupId))
                        .bind(5, artifactId)
                        .bind(6, version)
                        .execute();

                // Delete labels
                handle.createUpdate(sqlStatements.deleteVersionLabels())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, version)
                        .execute();

                // Delete properties
                handle.createUpdate(sqlStatements.deleteVersionProperties())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, version)
                        .execute();

                if (rowCount == 0) {
                    throw new VersionNotFoundException(groupId, artifactId, version);
                }
                return null;
            });
        } catch (VersionNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public CommentDto createArtifactVersionComment(String groupId, String artifactId, String version, String value) {
        log.debug("Inserting an artifact comment row for artifact: {} {} version: {}", groupId, artifactId, version);

        String createdBy = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();

        return createArtifactVersionCommentRaw(groupId, artifactId, version, this::nextCommentId, createdBy, createdOn, value);
    }


    @Override
    @Transactional
    public CommentDto createArtifactVersionCommentRaw(String groupId, String artifactId, String version, IdGenerator commentId,
                                                      String createdBy, Date createdOn, String value) {
        try {
            return handles.withHandle(handle -> {

                var metadata = getArtifactVersionMetaData(groupId, artifactId, version);

                var entity = CommentEntity.builder()
                        .commentId(String.valueOf(commentId.generate()))
                        .globalId(metadata.getGlobalId())
                        .createdBy(createdBy)
                        .createdOn(createdOn.getTime())
                        .value(value)
                        .build();

                importComment(entity);

                log.debug("Comment row successfully inserted.");

                return CommentDto.builder()
                        .commentId(entity.commentId)
                        .createdBy(createdBy)
                        .createdOn(createdOn.getTime())
                        .value(value)
                        .build();
            });
        } catch (VersionNotFoundException e) {
            throw e;
        } catch (Exception e) {
            if (sqlStatements.isForeignKeyViolation(e)) {
                throw new ArtifactNotFoundException(groupId, artifactId, e);
            }
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public List<CommentDto> getArtifactVersionComments(String groupId, String artifactId, String version) {
        log.debug("Getting a list of all artifact version comments for: {} {} @ {}", groupId, artifactId, version);

        try {
            return handles.withHandle(handle -> {
                return handle.createQuery(sqlStatements.selectComments())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, version)
                        .map(CommentDtoMapper.instance)
                        .list();
            });
        } catch (ArtifactNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RegistryStorageException(ex);
        }
    }


    @Override
    @Transactional
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version, String commentId) {
        log.debug("Deleting an artifact rule for artifact: {} {} @ {}", groupId, artifactId, version);
        String deletedBy = securityIdentity.getPrincipal().getName();

        try {
            handles.withHandle(handle -> {
                Optional<ArtifactVersionMetaDataDto> res = handle.createQuery(sqlStatements.selectArtifactVersionMetaData())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, version)
                        .map(ArtifactVersionMetaDataDtoMapper.instance)
                        .findOne();
                ArtifactVersionMetaDataDto avmdd = res.orElseThrow(() -> new VersionNotFoundException(groupId, artifactId, version));

                int rowCount = handle.createUpdate(sqlStatements.deleteComment())
                        .bind(0, avmdd.getGlobalId())
                        .bind(1, commentId)
                        .bind(2, deletedBy)
                        .execute();
                if (rowCount == 0) {
                    throw new CommentNotFoundException(commentId);
                }
                return null;
            });
        } catch (RegistryStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void updateArtifactVersionComment(String groupId, String artifactId, String version, String commentId, String value) {
        log.debug("Updating a comment for artifact: {} {} @ {}", groupId, artifactId, version);
        String modifiedBy = securityIdentity.getPrincipal().getName();

        try {
            handles.withHandle(handle -> {
                Optional<ArtifactVersionMetaDataDto> res = handle.createQuery(sqlStatements.selectArtifactVersionMetaData())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, version)
                        .map(ArtifactVersionMetaDataDtoMapper.instance)
                        .findOne();
                ArtifactVersionMetaDataDto avmdd = res.orElseThrow(() -> new VersionNotFoundException(groupId, artifactId, version));

                int rowCount = handle.createUpdate(sqlStatements.updateComment())
                        .bind(0, value)
                        .bind(1, avmdd.getGlobalId())
                        .bind(2, commentId)
                        .bind(3, modifiedBy)
                        .execute();
                if (rowCount == 0) {
                    throw new CommentNotFoundException(commentId);
                }
                return null;
            });
        } catch (RegistryStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectGlobalRules())
                    .map(rs -> RuleType.fromValue(rs.getString("type")))
                    .list();
        });
    }


    @Override
    @Transactional
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        log.debug("Inserting a global rule row for: {}", rule.name());
        try {
            handles.withHandle(handle -> {
                handle.createUpdate(sqlStatements.insertGlobalRule())
                        .bind(0, rule.name())
                        .bind(1, config.getConfiguration())
                        .execute();
                return null;
            });
        } catch (Exception e) {
            if (sqlStatements.isPrimaryKeyViolation(e)) {
                throw new RuleAlreadyExistsException(rule);
            }
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void deleteGlobalRules() throws RegistryStorageException {
        log.debug("Deleting all Global Rules");
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.deleteGlobalRules())
                    .execute();
            return null;
        });
    }


    @Override
    @Transactional
    public RuleConfigurationDto getGlobalRule(RuleType rule)
            throws RuleNotFoundException, RegistryStorageException {
        log.debug("Selecting a single global rule: {}", rule.name());
        try {
            return handles.withHandle(handle -> {
                Optional<RuleConfigurationDto> res = handle.createQuery(sqlStatements.selectGlobalRuleByType())
                        .bind(0, rule.name())
                        .map(RuleConfigurationDtoMapper.instance)
                        .findOne();
                return res.orElseThrow(() -> new RuleNotFoundException(rule));
            });
        } catch (RuleNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleNotFoundException, RegistryStorageException {
        log.debug("Updating a global rule: {}::{}", rule.name(), config.getConfiguration());
        try {
            handles.withHandle(handle -> {
                int rowCount = handle.createUpdate(sqlStatements.updateGlobalRule())
                        .bind(0, config.getConfiguration())
                        .bind(1, rule.name())
                        .execute();
                if (rowCount == 0) {
                    throw new RuleNotFoundException(rule);
                }
                return null;
            });
        } catch (RuleNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        log.debug("Deleting a global rule: {}", rule.name());
        try {
            handles.withHandle(handle -> {
                int rowCount = handle.createUpdate(sqlStatements.deleteGlobalRule())
                        .bind(0, rule.name())
                        .execute();
                if (rowCount == 0) {
                    throw new RuleNotFoundException(rule);
                }
                return null;
            });
        } catch (RuleNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public List<DynamicConfigPropertyDto> getConfigProperties() throws RegistryStorageException {
        log.debug("Getting all config properties.");
        return handles.withHandleNoException(handle -> {
            String sql = sqlStatements.selectConfigProperties();
            return handle.createQuery(sql)
                    .map(DynamicConfigPropertyDtoMapper.instance)
                    .list()
                    .stream()
                    // Filter out possible null values.
                    .filter(Objects::nonNull)
                    .collect(toList());
        });
    }


    @Override
    public DynamicConfigPropertyDto getConfigProperty(String propertyName) throws RegistryStorageException {
        return getRawConfigProperty(propertyName); // TODO Replace this?
    }


    @Override
    @Transactional
    public DynamicConfigPropertyDto getRawConfigProperty(String propertyName) {
        log.debug("Selecting a single config property: {}", propertyName);
        try {
            return handles.withHandle(handle -> {
                final String normalizedPropertyName = DtoUtil.appAuthPropertyToRegistry(propertyName);
                Optional<DynamicConfigPropertyDto> res = handle.createQuery(sqlStatements.selectConfigPropertyByName())
                        .bind(0, normalizedPropertyName)
                        .map(DynamicConfigPropertyDtoMapper.instance)
                        .findOne();
                return res.orElse(null);
            });
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void setConfigProperty(DynamicConfigPropertyDto propertyDto) throws RegistryStorageException {
        log.debug("Setting a config property with name: {}  and value: {}", propertyDto.getName(), propertyDto.getValue());
        handles.withHandleNoException(handle -> {
            String propertyName = propertyDto.getName();
            String propertyValue = propertyDto.getValue();

            // First delete the property row from the table
            // TODO Use deleteConfigProperty
            handle.createUpdate(sqlStatements.deleteConfigProperty())
                    .bind(0, propertyName)
                    .execute();

            // Then create the row again with the new value
            handle.createUpdate(sqlStatements.insertConfigProperty())
                    .bind(0, propertyName)
                    .bind(1, propertyValue)
                    .bind(2, java.lang.System.currentTimeMillis())
                    .execute();

            return null;
        });
    }


    @Override
    @Transactional
    public void deleteConfigProperty(String propertyName) throws RegistryStorageException {
        handles.withHandle(handle -> {
            handle.createUpdate(sqlStatements.deleteConfigProperty())
                    .bind(0, propertyName)
                    .execute();
            return null;
        });
    }


    @Override
    @Transactional
    public List<DynamicConfigPropertyDto> getStaleConfigProperties(Instant lastRefresh) throws RegistryStorageException {
        log.debug("Getting all stale config properties.");
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectStaleConfigProperties())
                    .bind(0, lastRefresh.toEpochMilli())
                    .map(DynamicConfigPropertyDtoMapper.instance)
                    .list()
                    .stream()
                    // Filter out possible null values.
                    .filter(Objects::nonNull)
                    .collect(toList());
        });
    }

    /**
     * @see RegistryStorage#createGroup(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override
    @Transactional
    public void createGroup(GroupMetaDataDto group) throws GroupAlreadyExistsException, RegistryStorageException {
        try {
            handles.withHandle(handle -> {
                handle.createUpdate(sqlStatements.insertGroup())
                        .bind(0, group.getGroupId())
                        .bind(1, group.getDescription())
                        .bind(2, group.getArtifactsType())
                        .bind(3, group.getCreatedBy())
                        // TODO io.apicurio.registry.storage.dto.GroupMetaDataDto should not use raw numeric timestamps
                        .bind(4, group.getCreatedOn() == 0 ? new Date() : new Date(group.getCreatedOn()))
                        .bind(5, group.getModifiedBy())
                        .bind(6, group.getModifiedOn() == 0 ? null : new Date(group.getModifiedOn()))
                        .bind(7, SqlUtil.serializeProperties(group.getProperties()))
                        .execute();
                return null;
            });
        } catch (Exception e) {
            if (sqlStatements.isPrimaryKeyViolation(e)) {
                throw new GroupAlreadyExistsException(group.getGroupId());
            }
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void updateGroupMetaData(GroupMetaDataDto group) throws GroupNotFoundException, RegistryStorageException {
        handles.withHandleNoException(handle -> {
            int rows = handle.createUpdate(sqlStatements.updateGroup())
                    .bind(0, group.getDescription())
                    .bind(1, group.getArtifactsType())
                    .bind(2, group.getModifiedBy())
                    .bind(3, group.getModifiedOn())
                    .bind(4, SqlUtil.serializeProperties(group.getProperties()))
                    .bind(5, group.getGroupId())
                    .execute();
            if (rows == 0) {
                throw new GroupNotFoundException(group.getGroupId());
            }
            return null;
        });
    }


    @Override
    @Transactional
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        handles.withHandleNoException(handle -> {
            int rows = handle.createUpdate(sqlStatements.deleteGroup())
                    .bind(0, groupId)
                    .execute();
            if (rows == 0) {
                throw new GroupNotFoundException(groupId);
            }
            // We have to perform an explicit check, otherwise an unchecked exception
            // would roll the transaction back.
            if (!getArtifactIds(groupId, 1).isEmpty()) {
                deleteArtifacts(groupId);
            }
            return null;
        });
    }


    @Override
    @Transactional
    public List<String> getGroupIds(Integer limit) throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            Query query = handle.createQuery(sqlStatements.selectGroups());
            query.bind(0, limit);
            return query
                    .map(rs -> rs.getString("groupId"))
                    .list();
        });
    }


    @Override
    @Transactional
    public GroupMetaDataDto getGroupMetaData(String groupId) throws GroupNotFoundException, RegistryStorageException {
        try {
            return handles.withHandle(handle -> {
                Optional<GroupMetaDataDto> res = handle.createQuery(sqlStatements.selectGroupByGroupId())
                        .bind(0, groupId)
                        .map(GroupMetaDataDtoMapper.instance)
                        .findOne();
                return res.orElseThrow(() -> new GroupNotFoundException(groupId));
            });
        } catch (GroupNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    /**
     * NOTE: Does not export the manifest file TODO
     */
    @Override
    @Transactional
    public void exportData(Function<Entity, Void> handler) throws RegistryStorageException {
        try {
            // Export a simple manifest file
            /////////////////////////////////
            ManifestEntity manifest = new ManifestEntity();
            if (securityIdentity != null && securityIdentity.getPrincipal() != null) {
                manifest.exportedBy = securityIdentity.getPrincipal().getName();
            }
            manifest.systemName = system.getName();
            manifest.systemDescription = system.getDescription();
            manifest.systemVersion = system.getVersion();
            handler.apply(manifest);

            // Export all content
            /////////////////////////////////
            handles.withHandle(handle -> {
                Stream<ContentEntity> stream = handle.createQuery(sqlStatements.exportContent())
                        .setFetchSize(50)
                        .map(ContentEntityMapper.instance)
                        .stream();
                // Process and then close the stream.
                try (stream) {
                    stream.forEach(handler::apply);
                }
                return null;
            });

            // Export all groups
            /////////////////////////////////
            handles.withHandle(handle -> {
                Stream<GroupEntity> stream = handle.createQuery(sqlStatements.exportGroups())
                        .setFetchSize(50)
                        .map(GroupEntityMapper.instance)
                        .stream();
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
                        .setFetchSize(50)
                        .map(ArtifactVersionEntityMapper.instance)
                        .stream();
                // Process and then close the stream.
                try (stream) {
                    stream.forEach(handler::apply);
                }
                return null;
            });

            // Export all artifact comments
            /////////////////////////////////
            handles.withHandle(handle -> {
                Stream<CommentEntity> stream = handle.createQuery(sqlStatements.exportComments())
                        .setFetchSize(50)
                        .map(CommentEntityMapper.instance)
                        .stream();
                // Process and then close the stream.
                try (stream) {
                    stream.forEach(handler::apply);
                }
                return null;
            });

            // Export all artifact rules
            /////////////////////////////////
            handles.withHandle(handle -> {
                Stream<ArtifactRuleEntity> stream = handle.createQuery(sqlStatements.exportArtifactRules())
                        .setFetchSize(50)
                        .map(ArtifactRuleEntityMapper.instance)
                        .stream();
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
                        .setFetchSize(50)
                        .map(GlobalRuleEntityMapper.instance)
                        .stream();
                // Process and then close the stream.
                try (stream) {
                    stream.forEach(handler::apply);
                }
                return null;
            });


        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    public void importData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId) {
        DataImporter dataImporter = new SqlDataImporter(log, utils, this, preserveGlobalId, preserveContentId);
        dataImporter.importData(entities, () -> {
        });
    }


    @Override
    @Transactional
    public long countArtifacts() throws RegistryStorageException {
        return handles.withHandle(handle -> {
            return handle.createQuery(sqlStatements.selectAllArtifactCount())
                    .mapTo(Long.class)
                    .one();
        });
    }


    @Override
    @Transactional
    public long countArtifactVersions(String groupId, String artifactId) throws RegistryStorageException {
        if (!isArtifactExists(groupId, artifactId)) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }

        return handles.withHandle(handle -> {
            return handle.createQuery(sqlStatements.selectAllArtifactVersionsCount())
                    .bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .mapTo(Long.class)
                    .one();
        });
    }


    @Override
    @Transactional
    public long countTotalArtifactVersions() throws RegistryStorageException {
        return handles.withHandle(handle -> {
            return handle.createQuery(sqlStatements.selectTotalArtifactVersionsCount())
                    .mapTo(Long.class)
                    .one();
        });
    }


    @Override
    @Transactional
    public void createRoleMapping(String principalId, String role, String principalName) throws RegistryStorageException {
        log.debug("Inserting a role mapping row for: {}", principalId);
        try {
            handles.withHandle(handle -> {
                handle.createUpdate(sqlStatements.insertRoleMapping())
                        .bind(0, principalId)
                        .bind(1, role)
                        .bind(2, principalName)
                        .execute();
                return null;
            });
        } catch (Exception e) {
            if (sqlStatements.isPrimaryKeyViolation(e)) {
                throw new RoleMappingAlreadyExistsException(principalId, role);
            }
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void deleteRoleMapping(String principalId) throws RegistryStorageException {
        log.debug("Deleting a role mapping row for: {}", principalId);
        try {
            handles.withHandle(handle -> {
                int rowCount = handle.createUpdate(sqlStatements.deleteRoleMapping())
                        .bind(0, principalId)
                        .execute();
                if (rowCount == 0) {
                    throw new RoleMappingNotFoundException(principalId);
                }
                return null;
            });
        } catch (RoleMappingNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public RoleMappingDto getRoleMapping(String principalId) throws RegistryStorageException {
        log.debug("Selecting a single role mapping for: {}", principalId);
        try {
            return handles.withHandle(handle -> {
                Optional<RoleMappingDto> res = handle.createQuery(sqlStatements.selectRoleMappingByPrincipalId())
                        .bind(0, principalId)
                        .map(RoleMappingDtoMapper.instance)
                        .findOne();
                return res.orElseThrow(() -> new RoleMappingNotFoundException(principalId));
            });
        } catch (RoleMappingNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public String getRoleForPrincipal(String principalId) throws RegistryStorageException {
        log.debug("Selecting the role for: {}", principalId);
        try {
            return handles.withHandle(handle -> {
                Optional<String> res = handle.createQuery(sqlStatements.selectRoleByPrincipalId())
                        .bind(0, principalId)
                        .mapTo(String.class)
                        .findOne();
                return res.orElse(null);
            });
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public List<RoleMappingDto> getRoleMappings() throws RegistryStorageException {
        log.debug("Getting a list of all role mappings.");
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectRoleMappings())
                    .map(RoleMappingDtoMapper.instance)
                    .list();
        });
    }


    @Override
    @Transactional
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException {
        log.debug("Updating a role mapping: {}::{}", principalId, role);
        try {
            handles.withHandle(handle -> {
                int rowCount = handle.createUpdate(sqlStatements.updateRoleMapping())
                        .bind(0, role)
                        .bind(1, principalId)
                        .execute();
                if (rowCount == 0) {
                    throw new RoleMappingNotFoundException(principalId, role);
                }
                return null;
            });
        } catch (RoleMappingNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public String createDownload(DownloadContextDto context) throws RegistryStorageException {
        log.debug("Inserting a download.");
        try {
            String downloadId = UUID.randomUUID().toString();
            return handles.withHandle(handle -> {
                handle.createUpdate(sqlStatements.insertDownload())
                        .bind(0, downloadId)
                        .bind(1, context.getExpires())
                        .bind(2, mapper.writeValueAsString(context))
                        .execute();
                return downloadId;
            });
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public DownloadContextDto consumeDownload(String downloadId) throws RegistryStorageException {
        log.debug("Consuming a download ID: {}", downloadId);

        try {
            return handles.withHandle(handle -> {
                long now = java.lang.System.currentTimeMillis();

                // Select the download context.
                Optional<String> res = handle.createQuery(sqlStatements.selectDownloadContext())
                        .bind(0, downloadId)
                        .bind(1, now)
                        .mapTo(String.class)
                        .findOne();
                String downloadContext = res.orElseThrow(DownloadNotFoundException::new);

                // Attempt to delete the row.
                int rowCount = handle.createUpdate(sqlStatements.deleteDownload())
                        .bind(0, downloadId)
                        .execute();
                if (rowCount == 0) {
                    throw new DownloadNotFoundException();
                }

                // Return what we consumed
                return mapper.readValue(downloadContext, DownloadContextDto.class);
            });
        } catch (DownloadNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    @Override
    @Transactional
    public void deleteAllExpiredDownloads() throws RegistryStorageException {
        log.debug("Deleting all expired downloads");
        long now = java.lang.System.currentTimeMillis();
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.deleteExpiredDownloads())
                    .bind(0, now)
                    .execute();
            return null;
        });
    }


    @Override
    @Transactional
    public void deleteAllUserData() {
        log.debug("Deleting all user data");

        deleteGlobalRules();

        handles.withHandleNoException(handle -> {
            // Delete all artifacts and related data

            handle.createUpdate(sqlStatements.deleteAllReferences())
                    .execute();

            handle.createUpdate(sqlStatements.deleteAllLabels())
                    .execute();

            handle.createUpdate(sqlStatements.deleteAllProperties())
                    .execute();

            handle.createUpdate(sqlStatements.deleteAllComments())
                    .execute();

            handle.createUpdate(sqlStatements.deleteAllBranches())
                    .execute();

            handle.createUpdate(sqlStatements.deleteAllVersions())
                    .execute();

            handle.createUpdate(sqlStatements.deleteAllArtifactRules())
                    .execute();

            handle.createUpdate(sqlStatements.deleteAllArtifacts())
                    .execute();

            // Delete all groups
            handle.createUpdate(sqlStatements.deleteAllGroups())
                    .execute();

            // Delete all role mappings
            handle.createUpdate(sqlStatements.deleteAllRoleMappings())
                    .execute();

            // Delete all content
            handle.createUpdate(sqlStatements.deleteAllContent())
                    .execute();

            // Delete all config properties
            handle.createUpdate(sqlStatements.deleteAllConfigProperties())
                    .execute();

            // TODO Do we need to delete comments?

            return null;
        });

    }


    @Override
    @Transactional
    public Map<String, ContentHandle> resolveReferences(List<ArtifactReferenceDto> references) {
        if (references == null || references.isEmpty()) {
            return Collections.emptyMap();
        } else {
            Map<String, ContentHandle> result = new LinkedHashMap<>();
            resolveReferences(result, references);
            return result;
        }
    }


    @Override
    @Transactional
    public boolean isArtifactExists(String groupId, String artifactId) throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectArtifactCountById())
                    .bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }


    @Override
    @Transactional
    public boolean isGroupExists(String groupId) throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectGroupCountById())
                    .bind(0, normalizeGroupId(groupId))
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }


    @Override
    @Transactional
    public List<Long> getContentIdsReferencingArtifact(String groupId, String artifactId, String version) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectContentIdsReferencingArtifactBy())
                    .bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .bind(2, version)
                    .mapTo(Long.class)
                    .list();
        });
    }


    @Override
    @Transactional
    public List<Long> getGlobalIdsReferencingArtifact(String groupId, String artifactId, String version) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectGlobalIdsReferencingArtifactBy())
                    .bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .bind(2, version)
                    .mapTo(Long.class)
                    .list();
        });
    }


    @Override
    @Transactional
    public List<ArtifactReferenceDto> getInboundArtifactReferences(String groupId, String artifactId, String version) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectInboundReferencesByGAV())
                    .bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .bind(2, version)
                    .map(ArtifactReferenceDtoMapper.instance)
                    .list();
        });
    }


    @Override
    public boolean isArtifactVersionExists(String groupId, String artifactId, String version) throws RegistryStorageException {
        try {
            getArtifactVersionMetaData(groupId, artifactId, version);
            return true;
        } catch (VersionNotFoundException ignored) {
            return false; // TODO Similar exception is thrown in some method callers, do we need this? Or use a different query.
        }
    }


    @Override
    @Transactional
    public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy, OrderDirection orderDirection, Integer offset, Integer limit) {

        return handles.withHandleNoException(handle -> {
            List<SqlStatementVariableBinder> binders = new LinkedList<>();

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
                        where.append("g.description LIKE ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        break;
                    case everything:
                        where.append("g.groupId LIKE ? OR g.description LIKE ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        break;
                    case group:
                        where.append("g.groupId = ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, normalizeGroupId(filter.getStringValue()));
                        });
                        break;
                    default:
                        break;
                }
                where.append(")");
            }

            // Add order by to artifact query
            switch (orderBy) {
                case name:
                    orderByQuery.append(" ORDER BY g.groupId");
                    break;
                case createdOn:
                    orderByQuery.append(" ORDER BY g.").append(orderBy.name());
                    break;
                default:
                    break;
            }
            orderByQuery.append(" ").append(orderDirection.name());

            // Add limit and offset to artifact query
            limitOffset.append(" LIMIT ? OFFSET ?");

            // Query for the group
            String groupsQuerySql = new StringBuilder(selectTemplate)
                    .append(where)
                    .append(orderByQuery)
                    .append(limitOffset)
                    .toString()
                    .replace("{{selectColumns}}", "*");
            Query groupsQuery = handle.createQuery(groupsQuerySql);
            // Query for the total row count
            String countQuerySql = new StringBuilder(selectTemplate)
                    .append(where)
                    .toString()
                    .replace("{{selectColumns}}", "count(g.groupId)");
            Query countQuery = handle.createQuery(countQuerySql);

            // Bind all query parameters
            int idx = 0;
            for (SqlStatementVariableBinder binder : binders) {
                binder.bind(groupsQuery, idx);
                binder.bind(countQuery, idx);
                idx++;
            }
            groupsQuery.bind(idx++, limit);
            groupsQuery.bind(idx++, offset);

            // Execute artifact query
            List<SearchedGroupDto> groups = groupsQuery.map(SearchedGroupMapper.instance).list();
            // Execute count query
            Integer count = countQuery.mapTo(Integer.class).one();

            GroupSearchResultsDto results = new GroupSearchResultsDto();
            results.setGroups(groups);
            results.setCount(count);
            return results;
        });
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private void resolveReferences(Map<String, ContentHandle> resolvedReferences, List<ArtifactReferenceDto> references) {
        if (references != null && !references.isEmpty()) {
            for (ArtifactReferenceDto reference : references) {
                if (reference.getArtifactId() == null || reference.getName() == null || reference.getVersion() == null) {
                    throw new IllegalStateException("Invalid reference: " + reference);
                } else {
                    if (!resolvedReferences.containsKey(reference.getName())) {
                        //TODO improve exception handling
                        try {
                            final ArtifactVersionMetaDataDto referencedArtifactMetaData = getArtifactVersionMetaData(reference.getGroupId(), reference.getArtifactId(), reference.getVersion());
                            final ContentWrapperDto referencedContent = getArtifactByContentId(referencedArtifactMetaData.getContentId());
                            resolveReferences(resolvedReferences, referencedContent.getReferences());
                            resolvedReferences.put(reference.getName(), referencedContent.getContent());
                        } catch (VersionNotFoundException ex) {
                            // Ignored
                        }
                    }
                }
            }
        }
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private void deleteAllOrphanedContent() {
        log.debug("Deleting all orphaned content");
        handles.withHandleNoException(handle -> {

            // Delete orphaned references
            handle.createUpdate(sqlStatements.deleteOrphanedReferences())
                    .execute();

            // Delete orphaned content
            handle.createUpdate(sqlStatements.deleteAllOrphanedContent())
                    .execute();

            return null;
        });
    }


    @Override
    @Transactional
    public void resetGlobalId() {
        resetSequence(GLOBAL_ID_SEQUENCE, sqlStatements.selectMaxGlobalId());
    }


    @Override
    @Transactional
    public void resetContentId() {
        resetSequence(CONTENT_ID_SEQUENCE, sqlStatements.selectMaxContentId());
    }


    @Override
    @Transactional
    public void resetCommentId() {
        resetSequence(COMMENT_ID_SEQUENCE, sqlStatements.selectMaxCommentId());
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private void resetSequence(String sequenceName, String sqlMaxIdFromTable) {
        handles.withHandleNoException(handle -> {
            Optional<Long> maxIdTable = handle.createQuery(sqlMaxIdFromTable)
                    .mapTo(Long.class)
                    .findOne();

            Optional<Long> currentIdSeq = handle.createQuery(sqlStatements.selectCurrentSequenceValue())
                    .bind(0, sequenceName)
                    .mapTo(Long.class)
                    .findOne();

            //TODO maybe do this in one query
            Optional<Long> maxId = maxIdTable
                    .map(maxIdTableValue -> {
                        if (currentIdSeq.isPresent()) {
                            if (currentIdSeq.get() > maxIdTableValue) {
                                //id in sequence is bigger than max value in table
                                return currentIdSeq.get();
                            }
                        }
                        //max value in table is bigger that id in sequence
                        return maxIdTableValue;
                    });


            if (maxId.isPresent()) {
                log.info("Resetting {} sequence", sequenceName);
                long id = maxId.get();

                if ("postgresql".equals(sqlStatements.dbType())) {
                    handle.createUpdate(sqlStatements.resetSequenceValue())
                            .bind(0, sequenceName)
                            .bind(1, id)
                            .bind(2, id)
                            .execute();
                } else {
                    handle.createUpdate(sqlStatements.resetSequenceValue())
                            .bind(0, sequenceName)
                            .bind(1, id)
                            .execute();
                }

                log.info("Successfully reset {} to {}", sequenceName, id);
            }
            return null;
        });
    }


    @Override
    @Transactional
    public void importArtifactRule(ArtifactRuleEntity entity) {

        handles.withHandleNoException(handle -> {
            if (isArtifactExists(entity.groupId, entity.artifactId)) {

                handle.createUpdate(sqlStatements.importArtifactRule())
                        .bind(0, normalizeGroupId(entity.groupId))
                        .bind(1, entity.artifactId)
                        .bind(2, entity.type.name())
                        .bind(3, entity.configuration)
                        .execute();
            } else {
                throw new ArtifactNotFoundException(entity.groupId, entity.artifactId);
            }
            return null;
        });
    }


    @Override
    @Transactional
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        handles.withHandleNoException(handle -> {

            if (!isArtifactExists(entity.groupId, entity.artifactId)) {

                handle.createUpdate(sqlStatements.insertArtifact())
                        .bind(0, normalizeGroupId(entity.groupId))
                        .bind(1, entity.artifactId)
                        .bind(2, entity.artifactType)
                        .bind(3, entity.createdBy)
                        .bind(4, new Date(entity.createdOn))
                        .execute();
            }

            if (!isGlobalIdExists(entity.globalId)) {

                handle.createUpdate(sqlStatements.importArtifactVersion())
                        .bind(0, entity.globalId)
                        .bind(1, normalizeGroupId(entity.groupId))
                        .bind(2, entity.artifactId)
                        .bind(3, entity.version)
                        .bind(4, entity.versionOrder)
                        .bind(5, entity.state)
                        .bind(6, entity.name)
                        .bind(7, entity.description)
                        .bind(8, entity.createdBy)
                        .bind(9, new Date(entity.createdOn))
                        .bind(10, SqlUtil.serializeLabels(entity.labels))
                        .bind(11, SqlUtil.serializeProperties(entity.properties))
                        .bind(12, entity.contentId)
                        .execute();

                // Insert labels into the "labels" table
                if (entity.labels != null && !entity.labels.isEmpty()) {
                    entity.labels.forEach(label -> {
                        handle.createUpdate(sqlStatements.insertLabel())
                                .bind(0, entity.globalId)
                                .bind(1, label.toLowerCase())
                                .execute();
                    });
                }

                // Insert properties into the "properties" table
                if (entity.properties != null && !entity.properties.isEmpty()) {
                    entity.properties.forEach((k, v) -> {
                        handle.createUpdate(sqlStatements.insertProperty())
                                .bind(0, entity.globalId)
                                .bind(1, k.toLowerCase())
                                .bind(2, v.toLowerCase())
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
    @Transactional
    public void importContent(ContentEntity entity) {

        handles.withHandleNoException(handle -> {

            if (!isContentExists(entity.contentId)) {

                handle.createUpdate(sqlStatements.importContent())
                        .bind(0, entity.contentId)
                        .bind(1, entity.canonicalHash)
                        .bind(2, entity.contentHash)
                        .bind(3, entity.contentBytes)
                        .bind(4, entity.serializedReferences)
                        .execute();

                insertReferences(entity.contentId, SqlUtil.deserializeReferences(entity.serializedReferences));
            } else {
                throw new ContentAlreadyExistsException(entity.contentId);
            }
            return null;
        });
    }


    @Override
    @Transactional
    public void importGlobalRule(GlobalRuleEntity entity) {
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.importGlobalRule()) // TODO Duplicated SQL query
                    .bind(0, entity.ruleType.name())
                    .bind(1, entity.configuration)
                    .execute();
            return null;
        });
    }


    @Override
    @Transactional
    public void importGroup(GroupEntity entity) {
        if (!isGroupExists(entity.groupId)) {
            handles.withHandleNoException(handle -> {
                handle.createUpdate(sqlStatements.importGroup())
                        .bind(0, SqlUtil.normalizeGroupId(entity.groupId))
                        .bind(1, entity.description)
                        .bind(2, entity.artifactsType)
                        .bind(3, entity.createdBy)
                        .bind(4, new Date(entity.createdOn))
                        .bind(5, entity.modifiedBy)
                        .bind(6, new Date(entity.modifiedOn))
                        .bind(7, SqlUtil.serializeProperties(entity.properties))
                        .execute();
                return null;
            });
        } else {
            throw new GroupAlreadyExistsException(entity.groupId);
        }
    }


    @Override
    @Transactional
    public void importComment(CommentEntity entity) {
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.insertComment())
                    .bind(0, entity.commentId)
                    .bind(1, entity.globalId)
                    .bind(2, entity.createdBy)
                    .bind(3, new Date(entity.createdOn))
                    .bind(4, entity.value)
                    .execute();
            return null;
        });
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private boolean isContentExists(long contentId) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectContentExists())
                    .bind(0, contentId)
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private boolean isGlobalIdExists(long globalId) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectGlobalIdExists())
                    .bind(0, globalId)
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }


    @Override
    @Transactional
    public long nextContentId() {
        return nextSequenceValue(CONTENT_ID_SEQUENCE);
    }


    @Override
    @Transactional
    public long nextGlobalId() {
        return nextSequenceValue(GLOBAL_ID_SEQUENCE);
    }


    @Override
    @Transactional
    public long nextCommentId() {
        return nextSequenceValue(COMMENT_ID_SEQUENCE);
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private long nextSequenceValue(String sequenceName) {
        return handles.withHandleNoException(handle -> {
            if (Set.of("mssql", "postgresql").contains(sqlStatements.dbType())) {
                return handle.createQuery(sqlStatements.getNextSequenceValue())
                        .bind(0, sequenceName)
                        .mapTo(Long.class)
                        .one(); // TODO Handle non-existing sequence (see resetSequence)
            } else {
                // no way to automatically increment the sequence in h2 with just one query
                // we are increasing the sequence value in a way that it's not safe for concurrent executions
                // for kafkasql storage this method is not supposed to be executed concurrently
                // but for inmemory storage that's not guaranteed
                // that forces us to use an inmemory lock, should not cause any harm
                // caveat emptor , consider yourself as warned
                synchronized (inmemorySequencesMutex) { // TODO Use implementation from common app components
                    Optional<Long> seqExists = handle.createQuery(sqlStatements.selectCurrentSequenceValue())
                            .bind(0, sequenceName)
                            .mapTo(Long.class)
                            .findOne();

                    if (seqExists.isPresent()) {
                        //
                        Long newValue = seqExists.get() + 1;
                        handle.createUpdate(sqlStatements.resetSequenceValue())
                                .bind(0, sequenceName)
                                .bind(1, newValue)
                                .execute();
                        return newValue;
                    } else {
                        handle.createUpdate(sqlStatements.insertSequenceValue())
                                .bind(0, sequenceName)
                                .bind(1, 1)
                                .execute();
                        return 1L;
                    }
                }
            }
        });
    }


    @Override
    @Transactional
    public boolean isContentExists(String contentHash) throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectContentCountByHash())
                    .bind(0, contentHash)
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }


    @Override
    @Transactional
    public boolean isArtifactRuleExists(String groupId, String artifactId, RuleType rule) throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectArtifactRuleCountByType())
                    .bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .bind(2, rule.name())
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }


    @Override
    @Transactional
    public boolean isGlobalRuleExists(RuleType rule) throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectGlobalRuleCountByType())
                    .bind(0, rule.name())
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }


    @Override
    @Transactional
    public boolean isRoleMappingExists(String principalId) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectRoleMappingCountByPrincipal())
                    .bind(0, principalId)
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }


    @Override
    @Transactional
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) {
        handles.withHandleNoException(handle -> {
            int rowCount = handle.createUpdate(sqlStatements().updateContentCanonicalHash())
                    .bind(0, newCanonicalHash)
                    .bind(1, contentId)
                    .bind(2, contentHash)
                    .execute();
            if (rowCount == 0) {
                log.warn("update content canonicalHash, no row match contentId {} contentHash {}", contentId, contentHash);
            }
            return null;
        });
    }


    @Override
    @Transactional
    public Optional<Long> contentIdFromHash(String contentHash) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements().selectContentIdByHash())
                    .bind(0, contentHash)
                    .mapTo(Long.class)
                    .findOne();
        });
    }


    @Override
    @Transactional
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, String contentHash, String createdBy, Date createdOn,
                                                          EditableArtifactMetaDataDto metaData,
                                                          IdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException {

        long contentId = contentIdFromHash(contentHash)
                .orElseThrow(() -> new RegistryStorageException("Content hash not found."));

        if (metaData == null) {
            metaData = new EditableArtifactMetaDataDto();
        }

        return updateArtifactWithMetadataRaw(groupId, artifactId, version, contentId, createdBy, createdOn,
                metaData, globalIdGenerator);
    }


    @Override
    @Transactional
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, String contentHash, String createdBy,
                                                          Date createdOn, EditableArtifactMetaDataDto metaData, IdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException {

        long contentId = contentIdFromHash(contentHash)
                .orElseThrow(() -> new RegistryStorageException("Content hash not found."));

        if (metaData == null) {
            metaData = new EditableArtifactMetaDataDto();
        }

        return createArtifactWithMetadataRaw(groupId, artifactId, version, artifactType, contentId, createdBy, createdOn,
                metaData, globalIdGenerator);
    }


    @Override
    @Transactional
    public Map<BranchId, List<GAV>> getArtifactBranches(GA ga) {

        var data1 = handles.withHandleNoException(handle -> {

            if (!isArtifactExists(ga.getRawGroupIdWithDefaultString(), ga.getRawArtifactId())) {
                throw new ArtifactNotFoundException(ga.getRawGroupIdWithDefaultString(), ga.getRawArtifactId());
            }

            return handle.createQuery(sqlStatements.selectArtifactBranches())
                    .bind(0, ga.getRawGroupId())
                    .bind(1, ga.getRawArtifactId())
                    .map(ArtifactVersionBranchDtoMapper.instance)
                    .list();
        });

        var data2 = new HashMap<BranchId, List<BranchDto>>();
        for (BranchDto dto : data1) {
            data2.compute(new BranchId(dto.getBranch()), (_ignored, v) -> {
                if (v == null) {
                    var initial = new ArrayList<BranchDto>();
                    initial.add(dto);
                    return initial;
                } else {
                    v.add(dto);
                    return v;
                }
            });
        }

        var data3 = new HashMap<BranchId, List<GAV>>();
        for (Entry<BranchId, List<BranchDto>> entry : data2.entrySet()) {
            data3.put(entry.getKey(), entry.getValue().stream()
                    .sorted(Comparator.comparingInt(BranchDto::getBranchOrder).reversed()) // Highest first
                    .map(BranchDto::toGAV)
                    .collect(toList()));
        }

        return data3;
    }


    @Override
    @Transactional
    public List<GAV> getArtifactBranch(GA ga, BranchId branchId, ArtifactRetrievalBehavior behavior) {

        String sql;
        switch (behavior) {
            case DEFAULT:
                sql = sqlStatements.selectArtifactBranchOrdered();
                break;
            case SKIP_DISABLED_LATEST:
                sql = sqlStatements.selectArtifactBranchOrderedNotDisabled();
                break;
            default:
                throw new UnreachableCodeException();
        }
        var finalSql = sql;

        var res = handles.withHandleNoException(handle -> {

            return handle.createQuery(finalSql)
                    .bind(0, ga.getRawGroupId())
                    .bind(1, ga.getRawArtifactId())
                    .bind(2, branchId.getRawBranchId())
                    .map(ArtifactVersionBranchDtoMapper.instance)
                    .list()
                    .stream()
                    .map(BranchDto::toGAV)
                    .collect(toList());
        });

        if (res.isEmpty()) {
            throw new BranchNotFoundException(ga, branchId);
        }

        return res;
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private boolean doesArtifactBranchContainVersion(GAV gav, BranchId branchId) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectDoesArtifactBranchContainVersion())
                    .bind(0, gav.getRawGroupId())
                    .bind(1, gav.getRawArtifactId())
                    .bind(2, branchId.getRawBranchId())
                    .bind(3, gav.getRawVersionId())
                    .mapTo(Long.class)
                    .findOne()
                    .isPresent();
        });
    }


    @Override
    @Transactional
    public void createOrUpdateArtifactBranch(GAV gav, BranchId branchId) {
        if (doesArtifactBranchContainVersion(gav, branchId)) {
            throw new BranchVersionAlreadyExistsException(gav, branchId);
        }
        handles.withHandleNoException(handle -> {
            try {
                handle.createUpdate(sqlStatements.insertArtifactBranch())
                        .bind(0, gav.getRawGroupId())
                        .bind(1, gav.getRawArtifactId())
                        .bind(2, branchId.getRawBranchId())
                        .bind(3, gav.getRawVersionId())
                        .bind(4, gav.getRawGroupId())
                        .bind(5, gav.getRawArtifactId())
                        .bind(6, branchId.getRawBranchId())
                        .execute();
            } catch (Exception ex) {
                if (sqlStatements.isForeignKeyViolation(ex)) {
                    throw new VersionNotFoundException(gav, ex);
                }
                throw ex;
            }
        });
    }


    @Override
    @Transactional
    public GAV getArtifactBranchLeaf(GA ga, BranchId branchId, ArtifactRetrievalBehavior behavior) {
        return handles.withHandleNoException(handle -> {
            switch (behavior) {

                case DEFAULT:
                    return handle.createQuery(sqlStatements.selectArtifactBranchLeaf())
                            .bind(0, ga.getRawGroupId())
                            .bind(1, ga.getRawArtifactId())
                            .bind(2, branchId.getRawBranchId())
                            .map(GAVMapper.instance)
                            .findOne()
                            .orElseThrow(() -> new VersionNotFoundException(ga.getRawGroupIdWithDefaultString(), ga.getRawArtifactId(),
                                    "<leaf of branch '" + branchId.getRawBranchId() + "'>"));

                case SKIP_DISABLED_LATEST:
                    return handle.createQuery(sqlStatements.selectArtifactBranchLeafNotDisabled())
                            .bind(0, ga.getRawGroupId())
                            .bind(1, ga.getRawArtifactId())
                            .bind(2, branchId.getRawBranchId())
                            .map(GAVMapper.instance)
                            .findOne()
                            .orElseThrow(() -> new VersionNotFoundException(ga.getRawGroupIdWithDefaultString(), ga.getRawArtifactId(),
                                    "<leaf of branch '" + branchId.getRawBranchId() + "' that does not  have disabled status>"));
            }
            throw new UnreachableCodeException();
        });
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private GAV getGAVByGlobalId(long globalId) {
        return handles.withHandle(handle -> {
            return handle.createQuery(sqlStatements.selectGAVByGlobalId())
                    .bind(0, globalId)
                    .map(GAVMapper.instance)
                    .findOne()
                    .orElseThrow(() -> new VersionNotFoundException(globalId));
        });
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private void deleteAllBranchesInArtifact(GA ga) {
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.deleteAllBranchesInArtifact())
                    .bind(0, ga.getRawGroupId())
                    .bind(1, ga.getRawArtifactId())
                    .execute();
        });
    }


    /**
     * IMPORTANT: Private methods can't be @Transactional. Callers MUST have started a transaction.
     */
    private void deleteAllBranchesInGroup(GroupId groupId) {
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.deleteAllBranchesInGroup())
                    .bind(0, groupId.getRawGroupId())
                    .execute();
        });
    }


    @Override
    @Transactional
    public void deleteArtifactBranch(GA ga, BranchId branchId) {
        if (BranchId.LATEST.equals(branchId)) {
            throw new NotAllowedException("Artifact version branch 'latest' cannot be deleted.");
        }

        handles.withHandleNoException(handle -> {

            var affected = handle.createUpdate(sqlStatements.deleteArtifactBranch())
                    .bind(0, ga.getRawGroupId())
                    .bind(1, ga.getRawArtifactId())
                    .bind(2, branchId.getRawBranchId())
                    .execute();

            if (affected == 0) {
                throw new BranchNotFoundException(ga, branchId);
            }

            var gavs = handle.createQuery(sqlStatements.selectVersionsWithoutBranch())
                    .bind(0, ga.getRawGroupId())
                    .bind(1, ga.getRawArtifactId())
                    .map(GAVMapper.instance)
                    .list();

            for (GAV gav : gavs) {
                deleteArtifactVersion(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());
            }
        });
    }


    private static boolean hasContentFilter(Set<SearchFilter> filters) {
        for (SearchFilter searchFilter : filters) {
            if (searchFilter.getType() == SearchFilterType.contentHash || searchFilter.getType() == SearchFilterType.canonicalHash) {
                return true;
            }
        }
        return false;
    }
}
