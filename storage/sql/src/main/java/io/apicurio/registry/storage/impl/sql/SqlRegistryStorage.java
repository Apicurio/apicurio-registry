/*
 * Copyright 2020 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.sql;

import static io.apicurio.registry.metrics.MetricIDs.STORAGE_CONCURRENT_OPERATION_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_CONCURRENT_OPERATION_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_GROUP_TAG;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_TIME;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_TIME_DESC;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.result.ResultIterable;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.agroal.api.AgroalDataSource;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SearchedArtifact;
import io.apicurio.registry.rest.beans.SearchedVersion;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.rest.beans.VersionSearchResults;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.impl.AbstractRegistryStorage;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactVersionMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedArtifactMapper;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedVersionMapper;
import io.apicurio.registry.storage.impl.sql.mappers.StoredArtifactMapper;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.utils.StringUtil;

/**
 * A SQL implementation of the {@link RegistryStorage} interface.  This impl does not
 * use any ORM technology - it simply uses native SQL for all operations.
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@PersistenceExceptionLivenessApply
@PersistenceTimeoutReadinessApply
@Counted(name = STORAGE_OPERATION_COUNT, description = STORAGE_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_COUNT})
@ConcurrentGauge(name = STORAGE_CONCURRENT_OPERATION_COUNT, description = STORAGE_CONCURRENT_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_CONCURRENT_OPERATION_COUNT})
@Timed(name = STORAGE_OPERATION_TIME, description = STORAGE_OPERATION_TIME_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_TIME}, unit = MILLISECONDS)
@Logged
public class SqlRegistryStorage extends AbstractRegistryStorage {
    
    private static final Logger log = LoggerFactory.getLogger(SqlRegistryStorage.class);
    private static int DB_VERSION = 1;
    private static final Object dbMutex = new Object();

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Inject
    SqlStatements sqlStatements;

    @ConfigProperty(name = "registry.sql.init", defaultValue = "true")
    boolean initDB;

    private Jdbi jdbi;

    /**
     * Constructor.
     */
    public SqlRegistryStorage() {
    }
    
    @PostConstruct
    @Transactional
    protected void initialize() {
        log.debug("SqlRegistryStorage constructed successfully.");
        
        jdbi = Jdbi.create(dataSource);

        if (initDB) {
            // TODO create the JDBI handle once and pass it in to all these DB related methods
            synchronized (dbMutex) {
                if (!isDatabaseInitialized()) {
                    log.info("Database not initialized.");
                    initializeDatabase();
                } else {
                    log.info("Database was already initialized, skipping.");
                }
                
                if (!isDatabaseCurrent()) {
                    log.info("Old database version detected, upgrading.");
                    upgradeDatabase();
                }
            }
        } else {
            if (!isDatabaseInitialized()) {
                log.error("Database not initialized.  Please use the DDL scripts to initialize the database before starting the application.");
                throw new RuntimeException("Database not initialized.");
            }
            
            if (!isDatabaseCurrent()) {
                log.error("Detected an old version of the database.  Please use the DDL upgrade scripts to bring your database up to date.");
                throw new RuntimeException("Database not upgraded.");
            }
        }
    }

    /**
     * @return true if the database has already been initialized
     */
    private boolean isDatabaseInitialized() {
        log.info("Checking to see if the DB is initialized.");
        return this.jdbi.withHandle(handle -> {
            ResultIterable<Integer> result = handle.createQuery(this.sqlStatements.isDatabaseInitialized()).mapTo(Integer.class);
            return result.one().intValue() > 0;
        });
    }

    /**
     * @return true if the database has already been initialized
     */
    private boolean isDatabaseCurrent() {
        log.info("Checking to see if the DB is up-to-date.");
        int version = this.getDatabaseVersion();
        return version == DB_VERSION;
    }

    private void initializeDatabase() {
        log.info("Initializing the Apicurio Registry database.");
        log.info("\tDatabase type: " + this.sqlStatements.dbType());
        
        final List<String> statements = this.sqlStatements.databaseInitialization();
        log.debug("---");
        
        this.jdbi.withHandle( handle -> {
            statements.forEach( statement -> {
                log.debug(statement);
                handle.createUpdate(statement).execute();
            });
            return null;
        });
        log.debug("---");
    }

    /**
     * Upgrades the database by executing a number of DDL statements found in DB-specific
     * DDL upgrade scripts.
     */
    private void upgradeDatabase() {
        log.info("Upgrading the Apicurio Hub API database.");
        
        int fromVersion = this.getDatabaseVersion();
        int toVersion = DB_VERSION;

        log.info("\tDatabase type: {}", this.sqlStatements.dbType());
        log.info("\tFrom Version:  {}", fromVersion);
        log.info("\tTo Version:    {}", toVersion);

        final List<String> statements = this.sqlStatements.databaseUpgrade(fromVersion, toVersion);
        log.debug("---");
        this.jdbi.withHandle( handle -> {
            statements.forEach( statement -> {
                log.debug(statement);
                
                if (statement.startsWith("UPGRADER:")) {
                    String cname = statement.substring(9).trim();
                    applyUpgrader(handle, cname);
                } else {
                    handle.createUpdate(statement).execute();
                }
            });
            return null;
        });
        log.debug("---");
    }
    
    /**
     * Instantiates an instance of the given upgrader class and then invokes it.  Used to perform
     * advanced upgrade logic when upgrading the DB (logic that cannot be handled in simple SQL 
     * statements).
     * @param handle
     * @param cname
     */
    private void applyUpgrader(Handle handle, String cname) {
        try {
            @SuppressWarnings("unchecked")
            Class<IDbUpgrader> upgraderClass = (Class<IDbUpgrader>) Class.forName(cname);
            IDbUpgrader upgrader = upgraderClass.newInstance();
            upgrader.upgrade(handle);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reuturns the current DB version by selecting the value in the 'apicurio' table.
     */
    private int getDatabaseVersion() {
        return this.jdbi.withHandle(handle -> {
            ResultIterable<String> result = handle.createQuery(this.sqlStatements.getDatabaseVersion())
                    .bind(0, "db_version")
                    .mapTo(String.class);
            try {
                String versionStr = result.one();
                int version = Integer.parseInt(versionStr);
                return version;
            } catch (Exception e) {
                return 0;
            }
        });
    }
    
    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactState(java.lang.String, io.apicurio.registry.types.ArtifactState)
     */
    @Override @Transactional
    public void updateArtifactState(String artifactId, ArtifactState state) {
        log.debug("Updating the state of artifact {} to {}", artifactId, state.name());
        ArtifactMetaDataDto dto = this.getLatestArtifactMetaDataInternal(artifactId);
        try {
            this.jdbi.withHandle( handle -> {
                long globalId = dto.getGlobalId();
                ArtifactState oldState = dto.getState();
                ArtifactState newState = state;
                ArtifactStateExt.applyState(s -> {
                    String sql = sqlStatements.updateArtifactVersionState();
                    int rowCount = handle.createUpdate(sql)
                            .bind(0, s.name())
                            .bind(1, globalId)
                            .execute();
                    if (rowCount == 0) {
                        throw new VersionNotFoundException(artifactId, dto.getVersion());
                    }
                }, oldState, newState);
                return null;
            });
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactState(java.lang.String, io.apicurio.registry.types.ArtifactState, java.lang.Integer)
     */
    @Override @Transactional
    public void updateArtifactState(String artifactId, ArtifactState state, Integer version) {
        log.debug("Updating the state of artifact {}, version {} to {}", artifactId, version, state.name());
        ArtifactVersionMetaDataDto dto = this.getArtifactVersionMetaData(artifactId, version);
        try {
            this.jdbi.withHandle( handle -> {
                long globalId = dto.getGlobalId();
                ArtifactState oldState = dto.getState();
                ArtifactState newState = state;
                ArtifactStateExt.applyState(s -> {
                    String sql = sqlStatements.updateArtifactVersionState();
                    int rowCount = handle.createUpdate(sql)
                            .bind(0, s.name())
                            .bind(1, globalId)
                            .execute();
                    if (rowCount == 0) {
                        throw new VersionNotFoundException(artifactId, dto.getVersion());
                    }
                }, oldState, newState);
                return null;
            });
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifact(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override @Transactional
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String artifactId, ArtifactType artifactType,
            ContentHandle content) throws ArtifactAlreadyExistsException, RegistryStorageException {
        log.debug("Inserting an artifact row for: {}", artifactId);
        String createdBy = null;
        Date createdOn = new Date();
        try {
            return this.jdbi.withHandle( handle -> {
                EditableArtifactMetaDataDto metaData = extractMetaData(artifactType, content);
                ArtifactMetaDataDto amdd = createArtifactInternal(handle, artifactId, artifactType, content,
                        createdBy, createdOn, metaData);
                return CompletableFuture.completedFuture(amdd);
            });
        } catch (Exception e) {
            if (sqlStatements.isPrimaryKeyViolation(e)) {
                throw new ArtifactAlreadyExistsException(artifactId);
            }
            throw new RegistryStorageException(e);
        }
    }

    private EditableArtifactMetaDataDto extractMetaData(ArtifactType artifactType, ContentHandle content) {
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
        ContentExtractor extractor = provider.getContentExtractor();
        EditableMetaData emd = extractor.extract(content);
        EditableArtifactMetaDataDto metaData;
        if (emd != null) {
            metaData = new EditableArtifactMetaDataDto(emd.getName(), emd.getDescription(), emd.getLabels(), emd.getProperties());
        } else {
            metaData = new EditableArtifactMetaDataDto();
        }
        return metaData;
    }

    /**
     * Creates an artifact version by storing content in the versions table.
     * @param handle
     * @param firstVersion
     * @param artifactId
     * @param content
     */
    private ArtifactVersionMetaDataDto createArtifactVersion(Handle handle, ArtifactType artifactType, 
            boolean firstVersion, String artifactId, String name, String description, List<String> labels, 
            Map<String, String> properties, ContentHandle content) {
        ArtifactState state = ArtifactState.ENABLED;
        String createdBy = null;
        Date createdOn = new Date();
        byte[] contentBytes = content.bytes();
        String contentHash = DigestUtils.sha256Hex(contentBytes);
        String labelsStr = SqlUtil.serializeLabels(labels);
        String propertiesStr = SqlUtil.serializeProperties(properties);
        
        ContentHandle canonicalContent = this.canonicalizeContent(artifactType, content);
        byte[] canonicalContentBytes = canonicalContent.bytes();
        String canonicalContentHash = DigestUtils.sha256Hex(canonicalContentBytes);

        // Upsert a row in the "content" table.  This will insert a row for the content
        // iff a row doesn't already exist.  We use the canonical hash to determine whether
        // a row for this content already exists.  If we find a row we return its globalId.
        // If we don't find a row, we insert one and then return its globalId.
        String sql = sqlStatements.upsertContent();
        Long contentId;
        if ("postgresql".equals(sqlStatements.dbType()) || "h2".equals(sqlStatements.dbType())) {
            handle.createUpdate(sql)
                    .bind(0, canonicalContentHash)
                    .bind(1, contentHash)
                    .bind(2, contentBytes)
                    .execute();
            sql = sqlStatements.selectContentIdByHash();
            contentId = handle.createQuery(sql)
                    .bind(0, contentHash)
                    .mapTo(Long.class)
                    .one();
        } else {
            // Handle other supported DBs here in the case that they handle UPSERT differently.
            contentId = 0l;
        }

        // Create a row in the "versions" table
        sql = sqlStatements.insertVersion(firstVersion);
        Long globalId;
        if (firstVersion) {
            globalId = handle.createUpdate(sql)
                    .bind(0, artifactId)
                    .bind(1, state)
                    .bind(2, name)
                    .bind(3, description)
                    .bind(4, createdBy)
                    .bind(5, createdOn)
                    .bind(6, labelsStr)
                    .bind(7, propertiesStr)
                    .bind(8, contentId)
                    .executeAndReturnGeneratedKeys("globalid")
                    .mapTo(Long.class)
                    .one();
        } else {
            globalId = handle.createUpdate(sql)
                    .bind(0, artifactId)
                    .bind(1, artifactId)
                    .bind(2, state)
                    .bind(3, name)
                    .bind(4, description)
                    .bind(5, createdBy)
                    .bind(6, createdOn)
                    .bind(7, labelsStr)
                    .bind(8, propertiesStr)
                    .bind(9, contentId)
                    .executeAndReturnGeneratedKeys("globalid")
                    .mapTo(Long.class)
                    .one();
        }

        // Insert labels into the "labels" table
        if (labels != null && !labels.isEmpty()) {
            labels.forEach(label -> {
                String sqli = sqlStatements.insertLabel();
                handle.createUpdate(sqli)
                      .bind(0, globalId)
                      .bind(1, label)
                      .execute();
            });
        }

        // Insert properties into the "properties" table
        if (properties != null && !properties.isEmpty()) {
            properties.forEach((k,v) -> {
                String sqli = sqlStatements.insertProperty();
                handle.createUpdate(sqli)
                      .bind(0, globalId)
                      .bind(1, k)
                      .bind(2, v)
                      .execute();
            });
        }
        
        // Update the "latest" column in the artifacts table with the globalId of the new version
        sql = sqlStatements.updateArtifactLatestVersion();
        handle.createUpdate(sql)
              .bind(0, globalId)
              .bind(1, artifactId)
              .execute();

        sql = sqlStatements.selectArtifactVersionMetaDataByGlobalId();
        return handle.createQuery(sql)
            .bind(0, globalId)
            .map(ArtifactVersionMetaDataDtoMapper.instance)
            .one();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactWithMetadata(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override @Transactional
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String artifactId,
            ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        log.debug("Inserting an artifact (with meta-data) row for: {}", artifactId);
        String createdBy = null;
        Date createdOn = new Date();
        try {
            return this.jdbi.withHandle( handle -> {
                ArtifactMetaDataDto amdd = createArtifactInternal(handle, artifactId, artifactType, content,
                        createdBy, createdOn, metaData);
                return CompletableFuture.completedFuture(amdd);
            });
        } catch (Exception e) {
            if (sqlStatements.isPrimaryKeyViolation(e)) {
                throw new ArtifactAlreadyExistsException(artifactId);
            }
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifact(java.lang.String)
     */
    @Override @Transactional
    public SortedSet<Long> deleteArtifact(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Deleting an artifact: {}", artifactId);
        try {
            return this.jdbi.withHandle( handle -> {
                // Get the list of versions of the artifact (will be deleted)
                String sql = sqlStatements.selectArtifactVersions();
                List<Long> versions = handle.createQuery(sql)
                        .bind(0, artifactId)
                        .mapTo(Long.class)
                        .list();
                SortedSet<Long> rval = new TreeSet<Long>(versions);
                
                // Set the 'latest' version of an artifact to NULL
                sql = sqlStatements.updateArtifactLatestVersion();
                handle.createUpdate(sql)
                      .bind(0, (Long) null)
                      .bind(1, artifactId)
                      .execute();
                
                // TODO use CASCADE when deleting rows from the "versions" table

                // Delete labels
                sql = sqlStatements.deleteLabels();
                handle.createUpdate(sql)
                    .bind(0, artifactId)
                    .execute();
                
                // Delete properties
                sql = sqlStatements.deleteProperties();
                handle.createUpdate(sql)
                    .bind(0, artifactId)
                    .execute();

                // Delete versions
                sql = sqlStatements.deleteVersions();
                handle.createUpdate(sql)
                    .bind(0, artifactId)
                    .execute();
                
                // TODO reap orphaned rows in the "content" table?

                // Delete artifact rules
                sql = sqlStatements.deleteArtifactRules();
                handle.createUpdate(sql)
                    .bind(0, artifactId)
                    .execute();

                // Delete artifact row (should be just one)
                sql = sqlStatements.deleteArtifact();
                int rowCount = handle.createUpdate(sql)
                    .bind(0, artifactId)
                    .execute();
                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(artifactId);
                }
                return rval;
            });
        } catch (ArtifactNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifact(java.lang.String)
     */
    @Override @Transactional
    public StoredArtifact getArtifact(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact (latest version) by artifactId: {}", artifactId);
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectLatestArtifactContent();
                return handle.createQuery(sql)
                        .bind(0, artifactId)
                        .map(StoredArtifactMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new ArtifactNotFoundException(e);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifact(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override @Transactional
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String artifactId, ArtifactType artifactType,
            ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Updating artifact {} with a new version (content).", artifactId);
        
        // Get meta-data from previous (latest) version
        ArtifactMetaDataDto latest = this.getLatestArtifactMetaDataInternal(artifactId);
        
        // Extract meta-data from the new content
        EditableArtifactMetaDataDto emd = extractMetaData(artifactType, content);

        // Create version and return
        return this.jdbi.withHandle(handle -> {
            // Merge latest meta-data with extracted meta-data
            String name = latest.getName();
            String description = latest.getDescription();
            List<String> labels = latest.getLabels();
            Map<String, String> properties = latest.getProperties();
            if (!StringUtil.isEmpty(emd.getName())) {
                name = emd.getName();
            }
            if (!StringUtil.isEmpty(emd.getDescription())) {
                description = emd.getDescription();
            }
            if (emd.getLabels() != null) {
                labels = emd.getLabels();
            }
            if (emd.getProperties() != null) {
                properties = emd.getProperties();
            }

            ArtifactVersionMetaDataDto versionDto = this.createArtifactVersion(handle, artifactType, false, artifactId, name, description, labels, properties, content);
            ArtifactMetaDataDto dto = versionToArtifactDto(artifactId, versionDto);
            dto.setCreatedOn(latest.getCreatedOn());
            dto.setCreatedBy(latest.getCreatedBy());
            dto.setLabels(labels);
            dto.setProperties(properties);
            return CompletableFuture.completedFuture(dto);
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactWithMetadata(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override @Transactional
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String artifactId,
            ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Updating artifact {} with a new version (content).", artifactId);

        // Get meta-data from previous (latest) version
        ArtifactMetaDataDto latest = this.getLatestArtifactMetaDataInternal(artifactId);

        // Create version and return
        return this.jdbi.withHandle(handle -> {
            String name = metaData.getName();
            String description = metaData.getDescription();
            List<String> labels = metaData.getLabels();
            Map<String, String> properties = metaData.getProperties();

            ArtifactVersionMetaDataDto versionDto = this.createArtifactVersion(handle, artifactType, false, artifactId, name, description, labels, properties, content);
            ArtifactMetaDataDto dto = versionToArtifactDto(artifactId, versionDto);
            dto.setCreatedOn(latest.getCreatedOn());
            dto.setCreatedBy(latest.getCreatedBy());
            dto.setLabels(labels);
            dto.setProperties(properties);
            return CompletableFuture.completedFuture(dto);
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactIds(java.lang.Integer)
     */
    @Override @Transactional
    public Set<String> getArtifactIds(Integer limit) {
        log.debug("Getting the set of all artifact IDs");
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactIds();
                List<String> ids = handle.createQuery(sql)
                        .bind(0, limit)
                        .mapTo(String.class)
                        .list();
                return new TreeSet<String>(ids);
            });
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#searchArtifacts(java.lang.String, int, int, io.apicurio.registry.rest.beans.SearchOver, io.apicurio.registry.rest.beans.SortOrder)
     */
    @Override @Transactional
    public ArtifactSearchResults searchArtifacts(String search, int offset, int limit, SearchOver searchOver,
            SortOrder sortOrder) {
        log.info("Searching for artifacts: {} over {} with {} ordering", search, searchOver, sortOrder);
        try {
            return this.jdbi.withHandle( handle -> {
                List<SqlStatementVariableBinder> binders = new LinkedList<>();

                StringBuilder select = new StringBuilder();
                StringBuilder where = new StringBuilder();
                StringBuilder orderBy = new StringBuilder();
                StringBuilder limitOffset = new StringBuilder();

                // Formulate the SELECT clause for the artifacts query
                select.append(
                        "SELECT a.*, v.globalId, v.version, v.state, v.name, v.description, v.labels, v.properties, "
                        +      "v.createdBy AS modifiedBy, v.createdOn AS modifiedOn "
                        + "FROM artifacts a "
                        + "JOIN versions v ON a.latest = v.globalId ");
                
                // Formulate the WHERE clause for both queries
                if (!StringUtil.isEmpty(search)) {
                    where.append("WHERE ");
                    switch (searchOver) {
                        case description:
                            where.append("v.description LIKE ?");
                            binders.add((query, idx) -> {
                                query.bind(idx, "%" + search + "%");
                            });
                            break;
                        case everything:
                            where.append("("
                                    + "v.name LIKE ? OR "
                                    + "a.artifactId LIKE ? OR "
                                    + "v.description LIKE ? OR "
                                    + "EXISTS(SELECT l.globalId FROM labels l WHERE l.label = ? AND l.globalId = v.globalId)"
                                    + ")");
                            binders.add((query, idx) -> {
                                query.bind(idx, "%" + search + "%");
                            });
                            binders.add((query, idx) -> {
                                query.bind(idx, "%" + search + "%");
                            });
                            binders.add((query, idx) -> {
                                query.bind(idx, "%" + search + "%");
                            });
                            binders.add((query, idx) -> {
                                query.bind(idx, search);
                            });
                            break;
                        case labels:
                            where.append("EXISTS(SELECT l.globalId FROM labels l WHERE l.label = ? AND l.globalId = v.globalId)");
                            binders.add((query, idx) -> {
                                query.bind(idx, search);
                            });
                            break;
                        case name:
                            where.append("(v.name LIKE ?) OR (a.artifactId LIKE ?)");
                            binders.add((query, idx) -> {
                                query.bind(idx, "%" + search + "%");
                            });
                            binders.add((query, idx) -> {
                                query.bind(idx, "%" + search + "%");
                            });
                            break;
                    }
                }

                // Add order by to artifact query
                orderBy.append(" ORDER BY coalesce(v.name, a.artifactId) ");
                orderBy.append(sortOrder.name());

                // Add limit and offset to artifact query
                limitOffset.append(" LIMIT ? OFFSET ?");

                // Query for the artifacts
                String artifactsQuerySql = select.toString() + where.toString() + orderBy.toString() + limitOffset.toString();
                Query artifactsQuery = handle.createQuery(artifactsQuerySql);
                // Query for the total row count
                String countSelect = "SELECT count(a.artifactId) FROM artifacts a JOIN versions v ON a.latest = v.globalId ";
                Query countQuery = handle.createQuery(countSelect + where.toString());

                // Bind all query parameters
                int idx = 0;
                for (SqlStatementVariableBinder binder : binders) {
                    binder.bind(artifactsQuery, idx);
                    binder.bind(countQuery, idx);
                    idx++;
                }
                artifactsQuery.bind(idx++, limit);
                artifactsQuery.bind(idx++, offset);
                
                // Execute artifact query
                List<SearchedArtifact> artifacts = artifactsQuery.map(SearchedArtifactMapper.instance).list();
                // Execute count query
                Integer count = countQuery.mapTo(Integer.class).one();
                
                ArtifactSearchResults results = new ArtifactSearchResults();
                results.setArtifacts(artifacts);
                results.setCount(count);
                return results;
            });
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(java.lang.String)
     */
    @Override @Transactional
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Selecting artifact (latest version) meta-data: {}", artifactId);
        return this.getLatestArtifactMetaDataInternal(artifactId);
    }

    /**
     * Internal method to retrieve the meta-data of the latest version of the given artifact.
     * @param artifactId
     */
    private ArtifactMetaDataDto getLatestArtifactMetaDataInternal(String artifactId) {
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectLatestArtifactMetaData();
                return handle.createQuery(sql)
                        .bind(0, artifactId)
                        .map(ArtifactMetaDataDtoMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new ArtifactNotFoundException(artifactId);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, boolean, io.apicurio.registry.content.ContentHandle)
     */
    @Override @Transactional
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, boolean canonical,
            ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        String hash;
        if (canonical) {
            ArtifactType type = this.getArtifactMetaData(artifactId).getType();
            ContentHandle canonicalContent = this.canonicalizeContent(type, content);
            hash = DigestUtils.sha256Hex(canonicalContent.bytes());
        } else {
            hash = DigestUtils.sha256Hex(content.bytes());
        }

        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactMetaDataByContentHash();
                if (canonical) {
                    sql = sqlStatements.selectArtifactMetaDataByCanonicalHash();
                }
                return handle.createQuery(sql)
                        .bind(0, artifactId)
                        .bind(1, hash)
                        .map(ArtifactVersionMetaDataDtoMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new ArtifactNotFoundException(artifactId);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(long)
     */
    @Override @Transactional
    public ArtifactMetaDataDto getArtifactMetaData(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting meta-data for globalId: {}", globalId);
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactMetaDataByGlobalId();
                return handle.createQuery(sql)
                        .bind(0, globalId)
                        .map(ArtifactMetaDataDtoMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new ArtifactNotFoundException(String.valueOf(globalId));
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactMetaData(java.lang.String, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override @Transactional
    public void updateArtifactMetaData(String artifactId, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Updating meta-data for an artifact: {}", artifactId);

        ArtifactMetaDataDto dto = this.getLatestArtifactMetaDataInternal(artifactId);
        long globalId = dto.getGlobalId();

        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.updateArtifactMetaDataLatestVersion();
                int rowCount = handle.createUpdate(sql)
                        .bind(0, metaData.getName())
                        .bind(1, metaData.getDescription())
                        .bind(2, SqlUtil.serializeLabels(metaData.getLabels()))
                        .bind(3, SqlUtil.serializeProperties(metaData.getProperties()))
                        .bind(4, artifactId)
                        .execute();
                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(artifactId);
                }
                
                // Delete all appropriate rows in the "labels" table
                sql = sqlStatements.deleteLabelsByGlobalId();
                handle.createUpdate(sql)
                    .bind(0, globalId)
                    .execute();
                
                // Delete all appropriate rows in the "properties" table
                sql = sqlStatements.deletePropertiesByGlobalId();
                handle.createUpdate(sql)
                    .bind(0, globalId)
                    .execute();
                
                // Insert new labels into the "labels" table
                List<String> labels = metaData.getLabels();
                if (labels != null && !labels.isEmpty()) {
                    labels.forEach(label -> {
                        String sqli = sqlStatements.insertLabel();
                        handle.createUpdate(sqli)
                              .bind(0, globalId)
                              .bind(1, label)
                              .execute();
                    });
                }

                // Insert new properties into the "properties" table
                Map<String, String> properties = metaData.getProperties();
                if (properties != null && !properties.isEmpty()) {
                    properties.forEach((k,v) -> {
                        String sqli = sqlStatements.insertProperty();
                        handle.createUpdate(sqli)
                              .bind(0, globalId)
                              .bind(1, k)
                              .bind(2, v)
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

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRules(java.lang.String)
     */
    @Override @Transactional
    public List<RuleType> getArtifactRules(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting a list of all artifact rules for: {}", artifactId);
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactRules();
                return handle.createQuery(sql)
                        .bind(0, artifactId)
                        .map(new RowMapper<RuleType>() {
                            @Override
                            public RuleType map(ResultSet rs, StatementContext ctx) throws SQLException {
                                return RuleType.fromValue(rs.getString("type"));
                            }
                        })
                        .list();
            });
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactRuleAsync(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override @Transactional
    public CompletionStage<Void> createArtifactRuleAsync(String artifactId, RuleType rule,
            RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        log.debug("Inserting an artifact rule row for artifact: {} rule: {}", artifactId, rule.name());
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.insertArtifactRule();
                handle.createUpdate(sql)
                      .bind(0, artifactId)
                      .bind(1, rule.name())
                      .bind(2, config.getConfiguration())
                      .execute();
                return null;
            });
        } catch (Exception e) {
            if (sqlStatements.isPrimaryKeyViolation(e)) {
                throw new RuleAlreadyExistsException(rule);
            }
            if (sqlStatements.isForeignKeyViolation(e)) {
                throw new ArtifactNotFoundException(artifactId, e);
            }
            throw new RegistryStorageException(e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRules(java.lang.String)
     */
    @Override @Transactional
    public void deleteArtifactRules(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Deleting all artifact rules for artifact: {}", artifactId);
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.deleteArtifactRules();
                handle.createUpdate(sql)
                      .bind(0, artifactId)
                      .execute();
                return null;
            });
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override @Transactional
    public RuleConfigurationDto getArtifactRule(String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact rule for artifact: {} and rule: {}", artifactId, rule.name());
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactRuleByType();
                return handle.createQuery(sql)
                        .bind(0, artifactId)
                        .bind(1, rule.name())
                        .mapToBean(RuleConfigurationDto.class)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new RuleNotFoundException(rule);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override @Transactional
    public void updateArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Updating an artifact rule for artifact: {} and rule: {}::{}", artifactId, rule.name(), config.getConfiguration());
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.updateArtifactRule();
                int rowCount = handle.createUpdate(sql)
                        .bind(0, config.getConfiguration())
                        .bind(1, artifactId)
                        .bind(2, rule.name())
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

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override @Transactional
    public void deleteArtifactRule(String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Deleting an artifact rule for artifact: {} and rule: {}", artifactId, rule.name());
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.deleteArtifactRule();
                int rowCount = handle.createUpdate(sql)
                      .bind(0, artifactId)
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

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersions(java.lang.String)
     */
    @Override @Transactional
    public SortedSet<Long> getArtifactVersions(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting a list of versions for an artifact");
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactVersions();
                List<Long> versions = handle.createQuery(sql)
                        .bind(0, artifactId)
                        .mapTo(Long.class)
                        .list();
                SortedSet<Long> rval = new TreeSet<Long>(versions);
                if (rval.isEmpty()) {
                    throw new ArtifactNotFoundException(artifactId);
                }
                return rval;
            });
        } catch (ArtifactNotFoundException anfe) {
            throw anfe;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#searchVersions(java.lang.String, int, int)
     */
    @Override @Transactional
    public VersionSearchResults searchVersions(String artifactId, int offset, int limit) {
        log.debug("Searching for versions of artifact {}", artifactId);
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectAllArtifactVersions();
                List<SearchedVersion> versions = handle.createQuery(sql)
                        .bind(0, artifactId)
                        .bind(1, limit)
                        .bind(2, offset)
                        .map(SearchedVersionMapper.instance)
                        .list();
                VersionSearchResults rval = new VersionSearchResults();
                rval.setVersions(versions);
                
                sql = sqlStatements.selectAllArtifactVersionsCount();
                Integer count = handle.createQuery(sql)
                        .bind(0, artifactId)
                        .mapTo(Integer.class)
                        .one();
                rval.setCount(count);

                return rval;
            });
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(long)
     */
    @Override @Transactional
    public StoredArtifact getArtifactVersion(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact version by globalId: {}", globalId);
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactVersionContentByGlobalId();
                return handle.createQuery(sql)
                        .bind(0, globalId)
                        .map(StoredArtifactMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new ArtifactNotFoundException(e);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(java.lang.String, long)
     */
    @Override @Transactional
    public StoredArtifact getArtifactVersion(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact version by artifactId: {} and version {}", artifactId, version);
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactVersionContent();
                return handle.createQuery(sql)
                        .bind(0, artifactId)
                        .bind(1, version)
                        .map(StoredArtifactMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new ArtifactNotFoundException(e);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersion(java.lang.String, long)
     */
    @Override @Transactional
    public void deleteArtifactVersion(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Deleting version {} of artifact {}", version, artifactId);
        
        SortedSet<Long> versions = getArtifactVersions(artifactId);
        
        // If the version we're deleting is the *only* version, then just delete the
        // entire artifact.
        if (versions.size() == 1 && versions.iterator().next().equals(version)) {
            this.deleteArtifact(artifactId);
            return;
        }

        // If there is only one version, but it's not the version being deleted, then
        // we can't find the version to delete!  This is an optimization.
        if (versions.size() == 1 && !versions.iterator().next().equals(version)) {
            throw new VersionNotFoundException(artifactId, version);
        }

        // Otherwise, delete just the one version and then reset the "latest" column on the artifacts table.
        try {
            this.jdbi.withHandle( handle -> {
                // Set the 'latest' version of an artifact to NULL
                String sql = sqlStatements.updateArtifactLatestVersion();
                handle.createUpdate(sql)
                      .bind(0, (Long) null)
                      .bind(1, artifactId)
                      .execute();
                
                // TODO use CASCADE when deleting rows from the "versions" table
                
                // Delete labels
                sql = sqlStatements.deleteVersionLabels();
                handle.createUpdate(sql)
                    .bind(0, artifactId)
                    .bind(1, version)
                    .execute();
                
                // Delete properties
                sql = sqlStatements.deleteVersionProperties();
                handle.createUpdate(sql)
                    .bind(0, artifactId)
                    .bind(1, version)
                    .execute();

                // Delete version
                sql = sqlStatements.deleteVersion();
                int rows = handle.createUpdate(sql)
                    .bind(0, artifactId)
                    .bind(1, version)
                    .execute();

                // TODO reap orphaned rows in the "content" table?

                // If the row was deleted, remove the version from the set of versions
                if (rows == 1) {
                    versions.remove(version);
                }
                
                // Update the 'latest' version of the artifact to the highest version remaining.
                long latestVersion = versions.last();
                sql = sqlStatements.updateArtifactLatestVersion();
                handle.createUpdate(sql)
                      .bind(0, latestVersion)
                      .bind(1, artifactId)
                      .execute();
                
                if (rows == 0) {
                    throw new VersionNotFoundException(artifactId, version);
                }
                
                return null;
            });
        } catch (VersionNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, long)
     */
    @Override @Transactional
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Selecting artifact version meta-data: {} version {}", artifactId, version);
        return getArtifactVersionMetaDataInternal(artifactId, version);
    }

    private ArtifactVersionMetaDataDto getArtifactVersionMetaDataInternal(String artifactId, long version) {
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactVersionMetaData();
                return handle.createQuery(sql)
                        .bind(0, artifactId)
                        .bind(1, version)
                        .map(ArtifactVersionMetaDataDtoMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new VersionNotFoundException(artifactId, version);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactVersionMetaData(java.lang.String, long, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override @Transactional
    public void updateArtifactVersionMetaData(String artifactId, long version,
            EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Updating meta-data for an artifact version: {}", artifactId);
        
        ArtifactVersionMetaDataDto dto = this.getArtifactVersionMetaDataInternal(artifactId, version);
        long globalId = dto.getGlobalId();
        
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.updateArtifactVersionMetaData();
                int rowCount = handle.createUpdate(sql)
                        .bind(0, metaData.getName())
                        .bind(1, metaData.getDescription())
                        .bind(2, SqlUtil.serializeLabels(metaData.getLabels()))
                        .bind(3, SqlUtil.serializeProperties(metaData.getProperties()))
                        .bind(4, artifactId)
                        .bind(5, version)
                        .execute();
                if (rowCount == 0) {
                    throw new VersionNotFoundException(artifactId, version);
                }

                // Delete all appropriate rows in the "labels" table
                sql = sqlStatements.deleteLabelsByGlobalId();
                handle.createUpdate(sql)
                    .bind(0, globalId)
                    .execute();
                
                // Delete all appropriate rows in the "properties" table
                sql = sqlStatements.deletePropertiesByGlobalId();
                handle.createUpdate(sql)
                    .bind(0, globalId)
                    .execute();
                
                // Insert new labels into the "labels" table
                List<String> labels = metaData.getLabels();
                if (labels != null && !labels.isEmpty()) {
                    labels.forEach(label -> {
                        String sqli = sqlStatements.insertLabel();
                        handle.createUpdate(sqli)
                              .bind(0, globalId)
                              .bind(1, label)
                              .execute();
                    });
                }

                // Insert new properties into the "properties" table
                Map<String, String> properties = metaData.getProperties();
                if (properties != null && !properties.isEmpty()) {
                    properties.forEach((k,v) -> {
                        String sqli = sqlStatements.insertProperty();
                        handle.createUpdate(sqli)
                              .bind(0, globalId)
                              .bind(1, k)
                              .bind(2, v)
                              .execute();
                    });
                }
                
                return null;
            });
        } catch (VersionNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersionMetaData(java.lang.String, long)
     */
    @Override @Transactional
    public void deleteArtifactVersionMetaData(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Deleting user-defined meta-data for artifact {} version {}", artifactId, version);
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.updateArtifactVersionMetaData();
                // NULL out the name, description, labels, and properties columns of the "versions" table.
                int rowCount = handle.createUpdate(sql)
                        .bind(0, (String) null)
                        .bind(1, (String) null)
                        .bind(2, (String) null)
                        .bind(3, (String) null)
                        .bind(4, artifactId)
                        .bind(5, version)
                        .execute();
                
                // Delete labels
                sql = sqlStatements.deleteVersionLabels();
                handle.createUpdate(sql)
                    .bind(0, artifactId)
                    .bind(1, version)
                    .execute();
                
                // Delete properties
                sql = sqlStatements.deleteVersionProperties();
                handle.createUpdate(sql)
                    .bind(0, artifactId)
                    .bind(1, version)
                    .execute();
                
                if (rowCount == 0) {
                    throw new VersionNotFoundException(artifactId, version);
                }
                return null;
            });
        } catch (VersionNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRules()
     */
    @Override @Transactional
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        log.debug("Getting a list of all Global Rules");
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectGlobalRules();
                return handle.createQuery(sql)
                        .map(new RowMapper<RuleType>() {
                            @Override
                            public RuleType map(ResultSet rs, StatementContext ctx) throws SQLException {
                                return RuleType.fromValue(rs.getString("type"));
                            }
                        })
                        .list();
            });
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override @Transactional
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        log.debug("Inserting a global rule row for: {}", rule.name());
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.insertGlobalRule();
                handle.createUpdate(sql)
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

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRules()
     */
    @Override @Transactional
    public void deleteGlobalRules() throws RegistryStorageException {
        log.debug("Deleting all Global Rules");
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.deleteGlobalRules();
                handle.createUpdate(sql)
                      .execute();
                return null;
            });
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override @Transactional
    public RuleConfigurationDto getGlobalRule(RuleType rule)
            throws RuleNotFoundException, RegistryStorageException {
        log.debug("Selecting a single global rule: {}", rule.name());
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectGlobalRuleByType();
                return handle.createQuery(sql)
                        .bind(0, rule.name())
                        .mapToBean(RuleConfigurationDto.class)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new RuleNotFoundException(rule);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override @Transactional
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleNotFoundException, RegistryStorageException {
        log.debug("Updating a global rule: {}::{}", rule.name(), config.getConfiguration());
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.updateGlobalRule();
                int rowCount = handle.createUpdate(sql)
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

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override @Transactional
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        log.debug("Deleting a global rule: {}", rule.name());
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.deleteGlobalRule();
                int rowCount = handle.createUpdate(sql)
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

    /**
     * Internal method to create a new artifact.
     * @param handle
     * @param artifactId
     * @param artifactType
     * @param content
     * @param createdBy
     * @param createdOn
     * @param metaData
     */
    private ArtifactMetaDataDto createArtifactInternal(Handle handle, String artifactId, ArtifactType artifactType,
            ContentHandle content, String createdBy, Date createdOn, EditableArtifactMetaDataDto metaData) {
        // Create a row in the artifacts table.
        String sql = sqlStatements.insertArtifact();
        handle.createUpdate(sql)
              .bind(0, artifactId)
              .bind(1, artifactType.name())
              .bind(2, (String) null) // no createdBy (yet)
              .bind(3, new Date())
              .execute();

        // Then create a row in the content and versions tables (for the content and version meta-data)
        ArtifactVersionMetaDataDto vmdd = this.createArtifactVersion(handle, artifactType, true, artifactId,
                metaData.getName(), metaData.getDescription(), metaData.getLabels(), metaData.getProperties(), content);
        
        // Return the new artifact meta-data
        ArtifactMetaDataDto amdd = versionToArtifactDto(artifactId, vmdd);
        amdd.setCreatedBy(createdBy);
        amdd.setCreatedOn(createdOn.getTime());
        amdd.setLabels(metaData.getLabels());
        amdd.setProperties(metaData.getProperties());
        return amdd;
    }

    /**
     * Converts a version DTO to an artifact DTO.
     * @param artifactId
     * @param vmdd
     */
    private ArtifactMetaDataDto versionToArtifactDto(String artifactId, ArtifactVersionMetaDataDto vmdd) {
        ArtifactMetaDataDto amdd = new ArtifactMetaDataDto();
        amdd.setGlobalId(vmdd.getGlobalId());
        amdd.setId(artifactId);
        amdd.setModifiedBy(vmdd.getCreatedBy());
        amdd.setModifiedOn(vmdd.getCreatedOn());
        amdd.setState(vmdd.getState());
        amdd.setName(vmdd.getName());
        amdd.setDescription(vmdd.getDescription());
        amdd.setLabels(vmdd.getLabels());
        amdd.setProperties(vmdd.getProperties());
        amdd.setType(vmdd.getType());
        amdd.setVersion(vmdd.getVersion());
        return amdd;
    }

    /**
     * Canonicalize the given content, returns the content unchanged in the case of an error.
     * @param artifactType
     * @param content
     */
    private ContentHandle canonicalizeContent(ArtifactType artifactType, ContentHandle content) {
        try {
            ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
            ContentCanonicalizer canonicalizer = provider.getContentCanonicalizer();
            ContentHandle canonicalContent = canonicalizer.canonicalize(content);
            return canonicalContent;
        } catch (Exception e) {
            log.debug("Failed to canonicalize content of type: {}", artifactType.name());
            return content;
        }
    }

}
