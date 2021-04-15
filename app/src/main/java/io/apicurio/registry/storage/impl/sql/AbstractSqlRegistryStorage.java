/*
 * Copyright 2020 Red Hat Inc
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

import static io.apicurio.registry.storage.impl.sql.SqlUtil.denormalizeGroupId;
import static io.apicurio.registry.storage.impl.sql.SqlUtil.normalizeGroupId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.result.ResultIterable;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.core.statement.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.agroal.api.AgroalDataSource;
import io.apicurio.registry.System;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.ExtractedMetaData;
import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.ContentNotFoundException;
import io.apicurio.registry.storage.GroupAlreadyExistsException;
import io.apicurio.registry.storage.GroupNotFoundException;
import io.apicurio.registry.storage.LogConfigurationNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StorageException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.LogConfigurationDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchFilterType;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.storage.impl.AbstractRegistryStorage;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactRuleEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactVersionEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactVersionMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ContentEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ContentMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GlobalRuleEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GroupEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GroupMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.LogConfigurationMapper;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedArtifactMapper;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedVersionMapper;
import io.apicurio.registry.storage.impl.sql.mappers.StoredArtifactMapper;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.GroupEntity;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * A SQL implementation of the {@link io.apicurio.registry.storage.RegistryStorage} interface.  This impl does not
 * use any ORM technology - it simply uses native SQL for all operations.
 * @author eric.wittmann@gmail.com
 */
public abstract class AbstractSqlRegistryStorage extends AbstractRegistryStorage {

    private static final Logger log = LoggerFactory.getLogger(AbstractSqlRegistryStorage.class);
    private static int DB_VERSION = 1;
    private static final Object dbMutex = new Object();

    @Inject
    TenantContext tenantContext;
    protected TenantContext tenantContext() {
        return tenantContext;
    }

    @Inject
    System system;

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Inject
    SqlStatements sqlStatements;

    @Inject
    SecurityIdentity securityIdentity;

    protected SqlStatements sqlStatements() {
        return sqlStatements;
    }

    @ConfigProperty(name = "registry.sql.init", defaultValue = "true")
    boolean initDB;

    protected Jdbi jdbi;

    /**
     * Constructor.
     */
    public AbstractSqlRegistryStorage() {
    }

    protected <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) {
        try {
            return this.jdbi.withHandle(callback);
        } catch (RegistryException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
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
        return withHandle(handle -> {
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

        withHandle( handle -> {
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
        withHandle( handle -> {
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
            IDbUpgrader upgrader = upgraderClass.getConstructor().newInstance();
            upgrader.upgrade(handle);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reuturns the current DB version by selecting the value in the 'apicurio' table.
     */
    private int getDatabaseVersion() {
        return withHandle(handle -> {
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
     * @see io.apicurio.registry.storage.RegistryStorage#storageName()
     */
    @Override
    public String storageName() {
        return "sql";
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#supportsMultiTenancy()
     */
    @Override
    public boolean supportsMultiTenancy() {
        return true;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactByContentId(long)
     */
    @Override
    public ContentHandle getArtifactByContentId(long contentId) throws ContentNotFoundException, RegistryStorageException {
        return withHandle( handle -> {
            try {
                String sql = sqlStatements().selectContentById();
                return handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, contentId)
                        .map(ContentMapper.instance)
                        .first();
            } catch (IllegalStateException e) {
                log.debug("Error getArtifactByContentId", e);
                throw new ContentNotFoundException("contentId-" + contentId);
            }
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactByContentHash(java.lang.String)
     */
    @Override
    public ContentHandle getArtifactByContentHash(String contentHash) throws ContentNotFoundException, RegistryStorageException {
        return withHandle( handle -> {
            try {
                String sql = sqlStatements().selectContentByContentHash();
                return handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, contentHash)
                        .map(ContentMapper.instance)
                        .first();
            } catch (IllegalStateException e) {
                log.debug("Error getArtifactByContentHash", e);
                throw new ContentNotFoundException("contentHash-" + contentHash);
            }
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactByContentId(long)
     */
    @Override
    public List<ArtifactMetaDataDto> getArtifactVersionsByContentId(long contentId) {
        return withHandle( handle -> {
            try {
                String sql = sqlStatements().selectArtifactVersionMetaDataByContentId();
                return handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, contentId)
                        .map(ArtifactMetaDataDtoMapper.instance)
                        .list();
            } catch (IllegalStateException e) {
                log.debug("Error getArtifactVersionsByContentId", e);
                throw new ContentNotFoundException("contentId-" + contentId);
            }
        });
    }


    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactState(java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactState)
     */
    @Override @Transactional
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state) throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Updating the state of artifact {} {} to {}", groupId, artifactId, state.name());
        ArtifactMetaDataDto dto = this.getLatestArtifactMetaDataInternal(groupId, artifactId);
        withHandle( handle -> {
            long globalId = dto.getGlobalId();
            ArtifactState oldState = dto.getState();
            ArtifactState newState = state;
            ArtifactStateExt.applyState(s -> {
                String sql = sqlStatements.updateArtifactVersionState();
                int rowCount = handle.createUpdate(sql)
                        .bind(0, s.name())
                        .bind(1, tenantContext.tenantId())
                        .bind(2, globalId)
                        .execute();
                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
            }, oldState, newState);
            return null;
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactState(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactState)
     */
    @Override
    public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Updating the state of artifact {} {}, version {} to {}", groupId, artifactId, version, state.name());
        ArtifactVersionMetaDataDto dto = this.getArtifactVersionMetaData(groupId, artifactId, version);
        withHandle( handle -> {
            long globalId = dto.getGlobalId();
            ArtifactState oldState = dto.getState();
            ArtifactState newState = state;
            if (oldState != newState) {
                ArtifactStateExt.applyState(s -> {
                    String sql = sqlStatements.updateArtifactVersionState();
                    int rowCount = handle.createUpdate(sql)
                            .bind(0, s.name())
                            .bind(1, tenantContext.tenantId())
                            .bind(2, globalId)
                            .execute();
                    if (rowCount == 0) {
                        throw new VersionNotFoundException(groupId, artifactId, dto.getVersion());
                    }
                }, oldState, newState);
            }
            return null;
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifact(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override @Transactional
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String groupId, String artifactId, String version, ArtifactType artifactType,
            ContentHandle content) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return createArtifact(groupId, artifactId, version, artifactType, content, null);
    }

    protected CompletionStage<ArtifactMetaDataDto> createArtifact(String groupId, String artifactId, String version, ArtifactType artifactType,
            ContentHandle content, GlobalIdGenerator globalIdGenerator) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return this.createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, null, globalIdGenerator);
    }

    /**
     * Creates an artifact version by storing information in the versions table.
     */
    private ArtifactVersionMetaDataDto createArtifactVersion(Handle handle, ArtifactType artifactType,
            boolean firstVersion, String groupId, String artifactId, String version, String name, String description, List<String> labels,
            Map<String, String> properties, String createdBy, Date createdOn, Long contentId,
            GlobalIdGenerator globalIdGenerator) {

        ArtifactState state = ArtifactState.ENABLED;
        String labelsStr = SqlUtil.serializeLabels(labels);
        String propertiesStr = SqlUtil.serializeProperties(properties);

        if (globalIdGenerator == null) {
            globalIdGenerator = new GlobalIdGenerator() {
                @Override
                public Long generate() {
                    return nextGlobalId(handle);
                }
            };
        }

        Long globalId = globalIdGenerator.generate();

        // Create a row in the "versions" table
        String sql = sqlStatements.insertVersion(firstVersion);
        if (firstVersion) {
            if (version == null) {
                version = "1";
            }
            handle.createUpdate(sql)
                .bind(0, globalId)
                .bind(1, tenantContext.tenantId())
                .bind(2, normalizeGroupId(groupId))
                .bind(3, artifactId)
                .bind(4, version)
                .bind(5, state)
                .bind(6, name)
                .bind(7, description)
                .bind(8, createdBy)
                .bind(9, createdOn)
                .bind(10, labelsStr)
                .bind(11, propertiesStr)
                .bind(12, contentId)
                .execute();
        } else {
            handle.createUpdate(sql)
                .bind(0, globalId)
                .bind(1, tenantContext.tenantId())
                .bind(2, normalizeGroupId(groupId))
                .bind(3, artifactId)
                .bind(4, version)
                .bind(5, tenantContext.tenantId())
                .bind(6, normalizeGroupId(groupId))
                .bind(7, artifactId)
                .bind(8, state)
                .bind(9, name)
                .bind(10, description)
                .bind(11, createdBy)
                .bind(12, createdOn)
                .bind(13, labelsStr)
                .bind(14, propertiesStr)
                .bind(15, contentId)
                .execute();

            // If version is null, update the row we just inserted to set the version to the generated versionId
            if (version == null) {
                sql = sqlStatements.autoUpdateVersionForGlobalId();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, globalId)
                    .bind(2, tenantContext.tenantId())
                    .bind(3, globalId)
                    .execute();
            }
        }

        // Insert labels into the "labels" table
        if (labels != null && !labels.isEmpty()) {
            labels.forEach(label -> {
                String sqli = sqlStatements.insertLabel();
                handle.createUpdate(sqli)
                      .bind(0, globalId)
                      .bind(1, label.toLowerCase())
                      .execute();
            });
        }

        // Insert properties into the "properties" table
        if (properties != null && !properties.isEmpty()) {
            properties.forEach((k,v) -> {
                String sqli = sqlStatements.insertProperty();
                handle.createUpdate(sqli)
                      .bind(0, globalId)
                      .bind(1, k.toLowerCase())
                      .bind(2, v.toLowerCase())
                      .execute();
            });
        }

        // Update the "latest" column in the artifacts table with the globalId of the new version
        sql = sqlStatements.updateArtifactLatest();
        handle.createUpdate(sql)
              .bind(0, globalId)
              .bind(1, tenantContext.tenantId())
              .bind(2, normalizeGroupId(groupId))
              .bind(3, artifactId)
              .execute();

        sql = sqlStatements.selectArtifactVersionMetaDataByGlobalId();
        return handle.createQuery(sql)
            .bind(0, globalId)
            .map(ArtifactVersionMetaDataDtoMapper.instance)
            .one();
    }

    /**
     * Store the content in the database and return the ID of the new row.  If the content already exists,
     * just return the content ID of the existing row.
     *
     * @param handle
     * @param artifactType
     * @param content
     */
    protected Long createOrUpdateContent(Handle handle, ArtifactType artifactType, ContentHandle content) {
        byte[] contentBytes = content.bytes();
        String contentHash = DigestUtils.sha256Hex(contentBytes);
        ContentHandle canonicalContent = this.canonicalizeContent(artifactType, content);
        byte[] canonicalContentBytes = canonicalContent.bytes();
        String canonicalContentHash = DigestUtils.sha256Hex(canonicalContentBytes);

        // Upsert a row in the "content" table.  This will insert a row for the content
        // iff a row doesn't already exist.  We use the canonical hash to determine whether
        // a row for this content already exists.  If we find a row we return its globalId.
        // If we don't find a row, we insert one and then return its globalId.
        String sql;
        Long contentId;
        if ("postgresql".equals(sqlStatements.dbType())) {
            sql = sqlStatements.upsertContent();
            handle.createUpdate(sql)
                    .bind(0, nextContentId(handle))
                    .bind(1, canonicalContentHash)
                    .bind(2, contentHash)
                    .bind(3, contentBytes)
                    .execute();
            sql = sqlStatements.selectContentIdByHash();
            contentId = handle.createQuery(sql)
                    .bind(0, contentHash)
                    .mapTo(Long.class)
                    .one();
        } else if ("h2".equals(sqlStatements.dbType())) {
            sql = sqlStatements.selectContentIdByHash();
            Optional<Long> contentIdOptional = handle.createQuery(sql)
                    .bind(0, contentHash)
                    .mapTo(Long.class)
                    .findOne();
            if (contentIdOptional.isPresent()) {
                contentId = contentIdOptional.get();
            } else {
                sql = sqlStatements.upsertContent();
                handle.createUpdate(sql)
                    .bind(0, nextContentId(handle))
                    .bind(1, canonicalContentHash)
                    .bind(2, contentHash)
                    .bind(3, contentBytes)
                    .execute();
                sql = sqlStatements.selectContentIdByHash();
                contentId = handle.createQuery(sql)
                        .bind(0, contentHash)
                        .mapTo(Long.class)
                        .one();
            }
        } else {
            throw new UnsupportedOperationException("Unsupported database type: " + sqlStatements.dbType());
        }
        return contentId;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactWithMetadata(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override @Transactional
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String groupId, String artifactId, String version,
            ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        return createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData, null);
    }

    protected CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String groupId, String artifactId, String version,
            ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData, GlobalIdGenerator globalIdGenerator)
            throws ArtifactAlreadyExistsException, RegistryStorageException {

        String createdBy = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();

        // Put the content in the DB and get the unique content ID back.
        long contentId = withHandle(handle -> {
            return createOrUpdateContent(handle, artifactType, content);
        });

        // If the metaData provided is null, try to figure it out from the content.
        EditableArtifactMetaDataDto md = metaData;
        if (md == null) {
            md = extractMetaData(artifactType, content);
        }

        return createArtifactWithMetadata(groupId, artifactId, version, artifactType, contentId, createdBy, createdOn, md, globalIdGenerator);
    }

    protected CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String groupId, String artifactId, String version,
            ArtifactType artifactType, long contentId, String createdBy, Date createdOn, EditableArtifactMetaDataDto metaData,
            GlobalIdGenerator globalIdGenerator) {
        log.debug("Inserting an artifact row for: {} {}", groupId, artifactId);
        try {
            return this.jdbi.withHandle( handle -> {
                // Create a row in the artifacts table.
                String sql = sqlStatements.insertArtifact();
                handle.createUpdate(sql)
                      .bind(0, tenantContext.tenantId())
                      .bind(1, normalizeGroupId(groupId))
                      .bind(2, artifactId)
                      .bind(3, artifactType.name())
                      .bind(4, createdBy)
                      .bind(5, createdOn)
                      .execute();

                // Then create a row in the content and versions tables (for the content and version meta-data)
                ArtifactVersionMetaDataDto vmdd = this.createArtifactVersion(handle, artifactType, true, groupId, artifactId, version,
                        metaData.getName(), metaData.getDescription(), metaData.getLabels(), metaData.getProperties(), createdBy, createdOn,
                        contentId, globalIdGenerator);

                // Return the new artifact meta-data
                ArtifactMetaDataDto amdd = versionToArtifactDto(groupId, artifactId, vmdd);
                amdd.setCreatedBy(createdBy);
                amdd.setCreatedOn(createdOn.getTime());
                amdd.setLabels(metaData.getLabels());
                amdd.setProperties(metaData.getProperties());
                return CompletableFuture.completedFuture(amdd);
            });
        } catch (Exception e) {
            if (sqlStatements.isPrimaryKeyViolation(e)) {
                throw new ArtifactAlreadyExistsException(groupId, artifactId);
            }
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifact(java.lang.String, java.lang.String)
     */
    @Override @Transactional
    public List<String> deleteArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Deleting an artifact: {} {}", groupId, artifactId);
        try {
            return this.jdbi.withHandle( handle -> {
                // Get the list of versions of the artifact (will be deleted)
                String sql = sqlStatements.selectArtifactVersions();
                List<String> versions = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .mapTo(String.class)
                        .list();

                // TODO use CASCADE when deleting rows from the "versions" table

                // Delete labels
                sql = sqlStatements.deleteLabels();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .execute();

                // Delete properties
                sql = sqlStatements.deleteProperties();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .execute();

                // Delete versions
                sql = sqlStatements.deleteVersions();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .execute();

                // TODO reap orphaned rows in the "content" table?

                // Delete artifact rules
                sql = sqlStatements.deleteArtifactRules();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .execute();

                // Delete artifact row (should be just one)
                sql = sqlStatements.deleteArtifact();
                int rowCount = handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .execute();
                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
                return versions;
            });
        } catch (ArtifactNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifacts(java.lang.String)
     */
    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        log.debug("Deleting all artifacts in group: {}", groupId);
        try {
            this.jdbi.withHandle( handle -> {

                // TODO use CASCADE when deleting rows from the "versions" table

                // Delete labels
                String sql = sqlStatements.deleteLabelsByGroupId();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .execute();

                // Delete properties
                sql = sqlStatements.deletePropertiesByGroupId();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .execute();

                // Delete versions
                sql = sqlStatements.deleteVersionsByGroupId();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .execute();

                // TODO reap orphaned rows in the "content" table?

                // Delete artifact rules
                sql = sqlStatements.deleteArtifactRulesByGroupId();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .execute();

                // Delete artifact row (should be just one)
                sql = sqlStatements.deleteArtifactsByGroupId();
                int rowCount = handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .execute();
                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(groupId, null);
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
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifact(java.lang.String, java.lang.String)
     */
    @Override @Transactional
    public StoredArtifactDto getArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact (latest version) by artifactId: {} {}", groupId, artifactId);
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectLatestArtifactContent();
                return handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .map(StoredArtifactMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new ArtifactNotFoundException(groupId, artifactId, e);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifact(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override @Transactional
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String groupId, String artifactId, String version, ArtifactType artifactType,
            ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        return updateArtifact(groupId, artifactId, version, artifactType, content, null);
    }

    protected CompletionStage<ArtifactMetaDataDto> updateArtifact(String groupId, String artifactId, String version, ArtifactType artifactType,
            ContentHandle content, GlobalIdGenerator globalIdGenerator) throws ArtifactNotFoundException, RegistryStorageException {
        return updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, null, globalIdGenerator);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactWithMetadata(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override @Transactional
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String groupId, String artifactId, String version,
            ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        return updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData, null);
    }

    protected CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String groupId, String artifactId, String version,
            ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData,
            GlobalIdGenerator globalIdGenerator) throws ArtifactNotFoundException, RegistryStorageException {

        String createdBy = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();

        // Put the content in the DB and get the unique content ID back.
        long contentId = withHandle(handle -> {
            return createOrUpdateContent(handle, artifactType, content);
        });

        // Extract meta-data from the content if no metadata is provided
        if (metaData == null) {
            metaData = extractMetaData(artifactType, content);
        }

        return updateArtifactWithMetadata(groupId, artifactId, version, artifactType, contentId, createdBy, createdOn,
                metaData, globalIdGenerator);
    }

    protected CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String groupId, String artifactId, String version,
            ArtifactType artifactType, long contentId, String createdBy, Date createdOn, EditableArtifactMetaDataDto metaData,
            GlobalIdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException {

        log.debug("Updating artifact {} {} with a new version (content).", groupId, artifactId);

        // Get meta-data from previous (latest) version
        ArtifactMetaDataDto latest = this.getLatestArtifactMetaDataInternal(groupId, artifactId);

        // Create version and return
        return withHandle(handle -> {
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
            ArtifactVersionMetaDataDto versionDto = this.createArtifactVersion(handle, artifactType, false, groupId, artifactId, version,
                    name, description, labels, properties, createdBy, createdOn, contentId, globalIdGenerator);
            ArtifactMetaDataDto dto = versionToArtifactDto(groupId, artifactId, versionDto);
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
        return withHandle( handle -> {
            String sql = sqlStatements.selectArtifactIds();
            List<String> ids = handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, limit)
                    .mapTo(String.class)
                    .list();
            return new TreeSet<String>(ids);
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#searchArtifacts(java.util.Set, io.apicurio.registry.storage.dto.OrderBy, io.apicurio.registry.storage.dto.OrderDirection, int, int)
     */
    @Override @Transactional
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy, OrderDirection orderDirection,
            int offset, int limit) {
        return withHandle( handle -> {
            List<SqlStatementVariableBinder> binders = new LinkedList<>();

            StringBuilder select = new StringBuilder();
            StringBuilder where = new StringBuilder();
            StringBuilder orderByQuery = new StringBuilder();
            StringBuilder limitOffset = new StringBuilder();

            boolean joinContentTable = hasContentFilter(filters);

            // Formulate the SELECT clause for the artifacts query
            select.append(
                    "SELECT a.*, v.globalId, v.version, v.state, v.name, v.description, v.labels, v.properties, "
                    +      "v.createdBy AS modifiedBy, v.createdOn AS modifiedOn "
                    + "FROM artifacts a "
                    + "JOIN versions v ON a.tenantId = v.tenantId AND a.latest = v.globalId ");
            if (joinContentTable) {
                select.append("JOIN content c ON v.contentId = c.contentId ");
            }

            where.append("WHERE a.tenantId = ?");
            binders.add((query, idx) -> {
                query.bind(idx, tenantContext.tenantId());
            });
            // Formulate the WHERE clause for both queries

            for (SearchFilter filter : filters) {
                where.append(" AND (");
                switch (filter.getType()) {
                    case description:
                        where.append("v.description LIKE ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getValue() + "%");
                        });
                        break;
                    case everything:
                        where.append("("
                                + "v.name LIKE ? OR "
                                + "v.groupId LIKE ? OR "
                                + "a.artifactId LIKE ? OR "
                                + "v.description LIKE ? OR "
                                + "EXISTS(SELECT l.globalId FROM labels l WHERE l.label = ? AND l.globalId = v.globalId)"
                                + ")");
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getValue() + "%");
                        });
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getValue() + "%");
                        });
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getValue() + "%");
                        });
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getValue() + "%");
                        });
                        binders.add((query, idx) -> {
                          //    Note: convert search to lowercase when searching for labels (case-insensitivity support).
                            query.bind(idx, filter.getValue().toLowerCase());
                        });
                        break;
                    case labels:
                        where.append("EXISTS(SELECT l.globalId FROM labels l WHERE l.label = ? AND l.globalId = v.globalId)");
                        binders.add((query, idx) -> {
                          //    Note: convert search to lowercase when searching for labels (case-insensitivity support).
                            query.bind(idx, filter.getValue().toLowerCase());
                        });
                        break;
                    case name:
                        where.append("(v.name LIKE ?) OR (a.artifactId LIKE ?)");
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getValue() + "%");
                        });
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getValue() + "%");
                        });
                        break;
                    case group:
                        where.append("(v.groupId = ?)");
                        binders.add((query, idx) -> {
                            query.bind(idx, normalizeGroupId(filter.getValue()));
                        });
                        break;
                    case contentHash:
                        where.append("(c.contentHash = ?)");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getValue());
                        });
                        break;
                    case canonicalHash:
                        where.append("(c.canonicalHash = ?)");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getValue());
                        });
                        break;
                    case properties:
                        // TODO implement filtering by properties!
                        throw new RuntimeException("Searching over properties is not yet implemented.");
                    default :
                        break;
                }
                where.append(")");
            }

            // Add order by to artifact query
            switch (orderBy) {
                case name:
                    orderByQuery.append(" ORDER BY coalesce(v." + orderBy.name() + ", a.artifactId) ");
                    break;
                case createdOn:
                    orderByQuery.append(" ORDER BY v." + orderBy.name() + " ");
                    break;
                default:
                    break;
            }
            orderByQuery.append(orderDirection.name());

            // Add limit and offset to artifact query
            limitOffset.append(" LIMIT ? OFFSET ?");

            // Query for the artifacts
            String artifactsQuerySql = select.toString() + where.toString() + orderByQuery.toString() + limitOffset.toString();
            Query artifactsQuery = handle.createQuery(artifactsQuerySql);
            // Query for the total row count
            String countSelect = "SELECT count(a.artifactId) FROM artifacts a JOIN versions v ON a.latest = v.globalId ";
            if (joinContentTable) {
                countSelect += "JOIN content c ON v.contentId = c.contentId ";
            }
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
            List<SearchedArtifactDto> artifacts = artifactsQuery.map(SearchedArtifactMapper.instance).list();
            // Execute count query
            Integer count = countQuery.mapTo(Integer.class).one();

            ArtifactSearchResultsDto results = new ArtifactSearchResultsDto();
            results.setArtifacts(artifacts);
            results.setCount(count);
            return results;
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(java.lang.String, java.lang.String)
     */
    @Override @Transactional
    public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Selecting artifact (latest version) meta-data: {} {}", groupId, artifactId);
        return this.getLatestArtifactMetaDataInternal(groupId, artifactId);
    }

    /**
     * Internal method to retrieve the meta-data of the latest version of the given artifact.
     * @param groupId
     * @param artifactId
     */
    private ArtifactMetaDataDto getLatestArtifactMetaDataInternal(String groupId, String artifactId) {
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectLatestArtifactMetaData();
                return handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .map(ArtifactMetaDataDtoMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, java.lang.String, boolean, io.apicurio.registry.content.ContentHandle)
     */
    @Override @Transactional
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, boolean canonical,
            ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        String hash;
        if (canonical) {
            ArtifactType type = this.getArtifactMetaData(groupId, artifactId).getType();
            ContentHandle canonicalContent = this.canonicalizeContent(type, content);
            hash = DigestUtils.sha256Hex(canonicalContent.bytes());
        } else {
            hash = DigestUtils.sha256Hex(content.bytes());
        }

        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactVersionMetaDataByContentHash();
                if (canonical) {
                    sql = sqlStatements.selectArtifactVersionMetaDataByCanonicalHash();
                }
                return handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .bind(3, hash)
                        .map(ArtifactVersionMetaDataDtoMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new ArtifactNotFoundException(groupId, artifactId);
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
                        .bind(0, tenantContext.tenantId())
                        .bind(1, globalId)
                        .map(ArtifactMetaDataDtoMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new ArtifactNotFoundException(null, String.valueOf(globalId));
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactMetaData(java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override @Transactional
    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Updating meta-data for an artifact: {} {}", groupId, artifactId);

        ArtifactMetaDataDto dto = this.getLatestArtifactMetaDataInternal(groupId, artifactId);

        internalUpdateArtifactVersionMetadata(dto.getGlobalId(), groupId, artifactId, dto.getVersion(), metaData);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRules(java.lang.String, java.lang.String)
     */
    @Override @Transactional
    public List<RuleType> getArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting a list of all artifact rules for: {} {}", groupId, artifactId);
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactRules();
                List<RuleType> rules = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .map(new RowMapper<RuleType>() {
                            @Override
                            public RuleType map(ResultSet rs, StatementContext ctx) throws SQLException {
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

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactRuleAsync(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override @Transactional
    public CompletionStage<Void> createArtifactRuleAsync(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        log.debug("Inserting an artifact rule row for artifact: {} {} rule: {}", groupId, artifactId, rule.name());
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.insertArtifactRule();
                handle.createUpdate(sql)
                      .bind(0, tenantContext.tenantId())
                      .bind(1, normalizeGroupId(groupId))
                      .bind(2, artifactId)
                      .bind(3, rule.name())
                      .bind(4, config.getConfiguration())
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
        return CompletableFuture.completedFuture(null);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRules(java.lang.String, java.lang.String)
     */
    @Override @Transactional
    public void deleteArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Deleting all artifact rules for artifact: {} {}", groupId, artifactId);
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.deleteArtifactRules();
                int count = handle.createUpdate(sql)
                      .bind(0, tenantContext.tenantId())
                      .bind(1, normalizeGroupId(groupId))
                      .bind(2, artifactId)
                      .execute();
                if (count == 0) {
                    if (!isArtifactExists(groupId, artifactId)) {
                        throw new ArtifactNotFoundException(groupId, artifactId);
                    }
                }
                return null;
            });
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override @Transactional
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact rule for artifact: {} {} and rule: {}", groupId, artifactId, rule.name());
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactRuleByType();
                try {
                    return handle.createQuery(sql)
                            .bind(0, tenantContext.tenantId())
                            .bind(1, normalizeGroupId(groupId))
                            .bind(2, artifactId)
                            .bind(3, rule.name())
                            .mapToBean(RuleConfigurationDto.class)
                            .one();
                } catch (IllegalStateException e) {
                    if (!isArtifactExists(groupId, artifactId)) {
                        throw new ArtifactNotFoundException(groupId, artifactId);
                    }
                    throw new RuleNotFoundException(rule);
                }

            });
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override @Transactional
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Updating an artifact rule for artifact: {} {} and rule: {}::{}", groupId, artifactId, rule.name(), config.getConfiguration());
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.updateArtifactRule();
                int rowCount = handle.createUpdate(sql)
                        .bind(0, config.getConfiguration())
                        .bind(1, tenantContext.tenantId())
                        .bind(2, normalizeGroupId(groupId))
                        .bind(3, artifactId)
                        .bind(4, rule.name())
                        .execute();
                if (rowCount == 0) {
                    if (!isArtifactExists(groupId, artifactId)) {
                        throw new ArtifactNotFoundException(groupId, artifactId);
                    }
                    throw new RuleNotFoundException(rule);
                }
                return null;
            });
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override @Transactional
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Deleting an artifact rule for artifact: {} {} and rule: {}", groupId, artifactId, rule.name());
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.deleteArtifactRule();
                int rowCount = handle.createUpdate(sql)
                      .bind(0, tenantContext.tenantId())
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
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersions(java.lang.String, java.lang.String)
     */
    @Override @Transactional
    public List<String> getArtifactVersions(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting a list of versions for artifact: {} {}", groupId, artifactId);
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactVersions();
                List<String> versions = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .mapTo(String.class)
                        .list();
                if (versions.isEmpty()) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
                return versions;
            });
        } catch (ArtifactNotFoundException anfe) {
            throw anfe;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#searchVersions(java.lang.String, java.lang.String, int, int)
     */
    @Override @Transactional
    public VersionSearchResultsDto searchVersions(String groupId, String artifactId, int offset, int limit) {
        log.debug("Searching for versions of artifact {} {}", groupId, artifactId);
        return withHandle( handle -> {
            VersionSearchResultsDto rval = new VersionSearchResultsDto();

            String sql = sqlStatements.selectAllArtifactVersionsCount();
            Integer count = handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .mapTo(Integer.class)
                    .one();
            rval.setCount(count);

            if (!isArtifactExists(groupId, artifactId)) {
                throw new ArtifactNotFoundException(groupId, artifactId);
            }

            sql = sqlStatements.selectAllArtifactVersions();
            List<SearchedVersionDto> versions = handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .bind(3, limit)
                    .bind(4, offset)
                    .map(SearchedVersionMapper.instance)
                    .list();
            rval.setVersions(versions);

            return rval;
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(long)
     */
    @Override @Transactional
    public StoredArtifactDto getArtifactVersion(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact version by globalId: {}", globalId);
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactVersionContentByGlobalId();
                return handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, globalId)
                        .map(StoredArtifactMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new ArtifactNotFoundException(null, "gid-" + globalId, e);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override @Transactional
    public StoredArtifactDto getArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact version by artifactId: {} {} and version {}", groupId, artifactId, version);
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactVersionContent();
                return handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .bind(3, version)
                        .map(StoredArtifactMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new ArtifactNotFoundException(groupId, artifactId, e);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override @Transactional
    public void deleteArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Deleting version {} of artifact {} {}", version, groupId, artifactId);

        List<String> versions = getArtifactVersions(groupId, artifactId);

        // If the version we're deleting is the *only* version, then just delete the
        // entire artifact.
        if (versions.size() == 1 && versions.iterator().next().equals(version)) {
            this.deleteArtifact(groupId, artifactId);
            return;
        }

        // If there is only one version, but it's not the version being deleted, then
        // we can't find the version to delete!  This is an optimization.
        if (versions.size() == 1 && !versions.iterator().next().equals(version)) {
            throw new VersionNotFoundException(groupId, artifactId, version);
        }

        // Otherwise, delete just the one version and then reset the "latest" column on the artifacts table.
        try {
            this.jdbi.withHandle( handle -> {
                // Set the 'latest' version of an artifact to NULL
                String sql = sqlStatements.updateArtifactLatest();
                handle.createUpdate(sql)
                      .bind(0, (Long) null)
                      .bind(1, tenantContext.tenantId())
                      .bind(2, normalizeGroupId(groupId))
                      .bind(3, artifactId)
                      .execute();

                // TODO use CASCADE when deleting rows from the "versions" table

                // Delete labels
                sql = sqlStatements.deleteVersionLabels();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .bind(3, version)
                    .execute();

                // Delete properties
                sql = sqlStatements.deleteVersionProperties();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .bind(3, version)
                    .execute();

                // Delete version
                sql = sqlStatements.deleteVersion();
                int rows = handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .bind(3, version)
                    .execute();

                // TODO reap orphaned rows in the "content" table?

                // If the row was deleted, update the "latest" column to the globalId of the highest remaining version
                if (rows == 1) {
                    versions.remove(version);

                    // Update the 'latest' version of the artifact to the globalId of the highest remaining version
                    String latestVersion = versions.get(versions.size() - 1);
                    sql = sqlStatements.updateArtifactLatestGlobalId();
                    handle.createUpdate(sql)
                          .bind(0, tenantContext.tenantId())
                          .bind(1, normalizeGroupId(groupId))
                          .bind(2, artifactId)
                          .bind(3, latestVersion)
                          .bind(4, tenantContext.tenantId())
                          .bind(5, groupId)
                          .bind(6, artifactId)
                          .execute();
                }

                if (rows == 0) {
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

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override @Transactional
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Selecting artifact version meta-data: {} {} version {}", groupId, artifactId, version);
        return getArtifactVersionMetaDataInternal(groupId, artifactId, version);
    }

    private ArtifactVersionMetaDataDto getArtifactVersionMetaDataInternal(String groupId, String artifactId, String version) {
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactVersionMetaData();
                return handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .bind(3, version)
                        .map(ArtifactVersionMetaDataDtoMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new VersionNotFoundException(groupId, artifactId, version);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override @Transactional
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Updating meta-data for an artifact version: {} {}", groupId, artifactId);

        ArtifactVersionMetaDataDto dto = this.getArtifactVersionMetaDataInternal(groupId, artifactId, version);

        internalUpdateArtifactVersionMetadata(dto.getGlobalId(), groupId, artifactId, dto.getVersion(), metaData);
    }

    /**
     * Common logic for updating artifact version metadata
     * @param globalId
     * @param groupId
     * @param artifactId
     * @param version
     * @param metaData
     */
    private void internalUpdateArtifactVersionMetadata(long globalId, String groupId, String artifactId, String version, EditableArtifactMetaDataDto metaData) {
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.updateArtifactVersionMetaData();
                int rowCount = handle.createUpdate(sql)
                        .bind(0, metaData.getName())
                        .bind(1, metaData.getDescription())
                        .bind(2, SqlUtil.serializeLabels(metaData.getLabels()))
                        .bind(3, SqlUtil.serializeProperties(metaData.getProperties()))
                        .bind(4, tenantContext.tenantId())
                        .bind(5, normalizeGroupId(groupId))
                        .bind(6, artifactId)
                        .bind(7, version)
                        .execute();
                if (rowCount == 0) {
                    throw new VersionNotFoundException(groupId, artifactId, version);
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
                              .bind(1, label.toLowerCase())
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
                              .bind(1, k.toLowerCase())
                              .bind(2, v.toLowerCase())
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
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override @Transactional
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Deleting user-defined meta-data for artifact {} {} version {}", groupId, artifactId, version);
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.updateArtifactVersionMetaData();
                // NULL out the name, description, labels, and properties columns of the "versions" table.
                int rowCount = handle.createUpdate(sql)
                        .bind(0, (String) null)
                        .bind(1, (String) null)
                        .bind(2, (String) null)
                        .bind(3, (String) null)
                        .bind(4, tenantContext.tenantId())
                        .bind(5, normalizeGroupId(groupId))
                        .bind(6, artifactId)
                        .bind(7, version)
                        .execute();

                // Delete labels
                sql = sqlStatements.deleteVersionLabels();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .bind(3, version)
                    .execute();

                // Delete properties
                sql = sqlStatements.deleteVersionProperties();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .bind(3, version)
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

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRules()
     */
    @Override @Transactional
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        log.debug("Getting a list of all Global Rules");
        return withHandle( handle -> {
            String sql = sqlStatements.selectGlobalRules();
            return handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .map(new RowMapper<RuleType>() {
                        @Override
                        public RuleType map(ResultSet rs, StatementContext ctx) throws SQLException {
                            return RuleType.fromValue(rs.getString("type"));
                        }
                    })
                    .list();
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override @Transactional
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        log.debug("Inserting a global rule row for: {}", rule.name());
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.insertGlobalRule();
                handle.createUpdate(sql)
                      .bind(0, tenantContext.tenantId())
                      .bind(1, rule.name())
                      .bind(2, config.getConfiguration())
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
        withHandle( handle -> {
            String sql = sqlStatements.deleteGlobalRules();
            handle.createUpdate(sql)
                  .bind(0, tenantContext.tenantId())
                  .execute();
            return null;
        });
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
                        .bind(0, tenantContext.tenantId())
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
     * @see io.apicurio.registry.storage.RegistryStorage#updateGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
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
                        .bind(1, tenantContext.tenantId())
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
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override @Transactional
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        log.debug("Deleting a global rule: {}", rule.name());
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.deleteGlobalRule();
                int rowCount = handle.createUpdate(sql)
                      .bind(0, tenantContext.tenantId())
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
     * @see io.apicurio.registry.storage.RegistryStorage#getLogConfiguration(java.lang.String)
     */
    @Override
    public LogConfigurationDto getLogConfiguration(String logger) throws RegistryStorageException, LogConfigurationNotFoundException {
        log.debug("Selecting a single log configuration: {}", logger);
        try {
            return this.jdbi.withHandle(handle -> {
                String sql = sqlStatements.selectLogConfigurationByLogger();
                return handle.createQuery(sql)
                        .bind(0, logger)
                        .map(LogConfigurationMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new LogConfigurationNotFoundException(logger);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#setLogConfiguration(io.apicurio.registry.storage.dto.LogConfigurationDto)
     */
    @Override @Transactional
    public void setLogConfiguration(LogConfigurationDto logConfiguration) throws RegistryStorageException {
        log.debug("Upsert log configuration: {}", logConfiguration.getLogger());
        withHandle(handle -> {
            String sql = sqlStatements.upsertLogConfiguration();

            Update query = handle.createUpdate(sql)
                    .bind(0, logConfiguration.getLogger())
                    .bind(1, logConfiguration.getLogLevel().value());
            if ("postgresql".equals(sqlStatements.dbType())) {
                query.bind(2, logConfiguration.getLogLevel().value());
            }
            query.execute();

            return null;
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#removeLogConfiguration(java.lang.String)
     */
    @Override @Transactional
    public void removeLogConfiguration(String logger) throws RegistryStorageException, LogConfigurationNotFoundException {
        log.debug("Removing a log configuration: {}", logger);
        withHandle( handle -> {
            String sql = sqlStatements.deleteLogConfiguration();
            int rowCount = handle.createUpdate(sql)
                  .bind(0, logger)
                  .execute();
            if (rowCount == 0) {
                throw new LogConfigurationNotFoundException(logger);
            }
            return null;
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#listLogConfigurations()
     */
    @Override
    public List<LogConfigurationDto> listLogConfigurations() throws RegistryStorageException {
        log.debug("Selecting all log configurations");
        return withHandle(handle -> {
            String sql = sqlStatements.selectAllLogConfigurations();
            return handle.createQuery(sql)
                    .map(LogConfigurationMapper.instance)
                    .list();
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createGroup(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override
    public void createGroup(GroupMetaDataDto group) throws GroupAlreadyExistsException, RegistryStorageException {
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.insertGroup();
                handle.createUpdate(sql)
                      .bind(0, tenantContext.tenantId())
                      .bind(1, group.getGroupId())
                      .bind(2, group.getDescription())
                      .bind(3, group.getArtifactsType() == null ? null : group.getArtifactsType().value())
                      .bind(4, group.getCreatedBy())
                      .bind(5, group.getCreatedOn())
                      .bind(6, group.getModifiedBy())
                      .bind(7, group.getModifiedOn())
                      .bind(8, SqlUtil.serializeProperties(group.getProperties()))
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

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateGroupMetaData(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override
    public void updateGroupMetaData(GroupMetaDataDto group) throws GroupNotFoundException, RegistryStorageException {
        withHandle(handle -> {
            String sql = sqlStatements.updateGroup();
            int rows = handle.createUpdate(sql)
                  .bind(0, group.getDescription())
                  .bind(1, group.getArtifactsType() == null ? null : group.getArtifactsType().value())
                  .bind(2, group.getModifiedBy())
                  .bind(3, group.getModifiedOn())
                  .bind(4, SqlUtil.serializeProperties(group.getProperties()))
                  .bind(5, tenantContext.tenantId())
                  .bind(6, group.getGroupId())
                  .execute();
            if (rows == 0) {
                throw new GroupNotFoundException(group.getGroupId());
            }
            return null;
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGroup(java.lang.String)
     */
    @Override
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        withHandle(handle -> {
            String sql = sqlStatements.deleteGroup();
            int rows = handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, groupId)
                    .execute();
            if (rows == 0) {
                throw new GroupNotFoundException(groupId);
            }
            deleteArtifacts(groupId);
            return null;
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGroupIds(java.lang.Integer)
     */
    @Override
    public List<String> getGroupIds(Integer limit) throws RegistryStorageException {
        return withHandle(handle -> {
            String sql = sqlStatements.selectGroups();
            List<String> groups = handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, limit)
                    .map(new RowMapper<String>() {
                        @Override
                        public String map(ResultSet rs, StatementContext ctx) throws SQLException {
                            return rs.getString("groupId");
                        }
                    })
                    .list();
            return groups;
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGroupMetaData(java.lang.String)
     */
    @Override
    public GroupMetaDataDto getGroupMetaData(String groupId) throws GroupNotFoundException, RegistryStorageException {
        try {
            return this.jdbi.withHandle(handle -> {
                String sql = sqlStatements.selectGroupByGroupId();
                return handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(0, groupId)
                        .map(GroupMetaDataDtoMapper.instance)
                        .one();
            });
        } catch (IllegalStateException e) {
            throw new GroupNotFoundException(groupId);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#exportData(java.util.function.Function)
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
            this.jdbi.withHandle(handle -> {
                String sql = sqlStatements.exportContent();
                Stream<ContentEntity> stream = handle.createQuery(sql)
                        .setFetchSize(50)
                        .map(ContentEntityMapper.instance)
                        .stream();
                // Process and then close the stream.
                try (stream) {
                    stream.forEach(entity -> {
                        handler.apply(entity);
                    });
                }
                return null;
            });

            // Export all groups
            /////////////////////////////////
            this.jdbi.withHandle(handle -> {
                String sql = sqlStatements.exportGroups();
                Stream<GroupEntity> stream = handle.createQuery(sql)
                        .bind(0, tenantContext().tenantId())
                        .setFetchSize(50)
                        .map(GroupEntityMapper.instance)
                        .stream();
                // Process and then close the stream.
                try (stream) {
                    stream.forEach(entity -> {
                        handler.apply(entity);
                    });
                }
                return null;
            });

            // Export all artifact versions
            /////////////////////////////////
            this.jdbi.withHandle(handle -> {
                String sql = sqlStatements.exportArtifactVersions();
                Stream<ArtifactVersionEntity> stream = handle.createQuery(sql)
                        .bind(0, tenantContext().tenantId())
                        .setFetchSize(50)
                        .map(ArtifactVersionEntityMapper.instance)
                        .stream();
                // Process and then close the stream.
                try (stream) {
                    stream.forEach(entity -> {
                        handler.apply(entity);
                    });
                }
                return null;
            });

            // Export all artifact rules
            /////////////////////////////////
            this.jdbi.withHandle(handle -> {
                String sql = sqlStatements.exportArtifactRules();
                Stream<ArtifactRuleEntity> stream = handle.createQuery(sql)
                        .bind(0, tenantContext().tenantId())
                        .setFetchSize(50)
                        .map(ArtifactRuleEntityMapper.instance)
                        .stream();
                // Process and then close the stream.
                try (stream) {
                    stream.forEach(entity -> {
                        handler.apply(entity);
                    });
                }
                return null;
            });

            // Export all global rules
            /////////////////////////////////
            this.jdbi.withHandle(handle -> {
                String sql = sqlStatements.exportGlobalRules();
                Stream<GlobalRuleEntity> stream = handle.createQuery(sql)
                        .bind(0, tenantContext().tenantId())
                        .setFetchSize(50)
                        .map(GlobalRuleEntityMapper.instance)
                        .stream();
                // Process and then close the stream.
                try (stream) {
                    stream.forEach(entity -> {
                        handler.apply(entity);
                    });
                }
                return null;
            });


        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#importData(io.apicurio.registry.storage.impexp.EntityInputStream)
     */
    @Override
    @Transactional
    public void importData(EntityInputStream entities) throws RegistryStorageException {
        withHandle( handle -> {
            Entity entity = null;
            while ( (entity = entities.nextEntity()) != null ) {
                if (entity != null) {
                    importEntity(handle, entity);
                }
            }

            // Make sure the contentId sequence is set high enough
            resetContentId(handle);

            // Make sure the globalId sequence is set high enough
            resetGlobalId(handle);

            return null;
        });
    }

    // TODO this can be improved using use ALTER SEQUENCE serial RESTART WITH 105;
    protected void resetGlobalId(Handle handle) {
        String sql = sqlStatements.selectMaxGlobalId();
        Optional<Long> maxGlobalId = handle.createQuery(sql)
                .mapTo(Long.class)
                .findOne();

        if (maxGlobalId.isPresent()) {
            long id = maxGlobalId.get();

            log.info("Resetting globalId sequence to {}", id);
            while (nextGlobalId(handle) < id) {}
            log.info("Successfully reset globalId to {}", nextGlobalId(handle));
        }
    }

    // TODO this can be improved using use ALTER SEQUENCE serial RESTART WITH 105;
    protected void resetContentId(Handle handle) {
        String sql = sqlStatements.selectMaxContentId();
        Optional<Long> maxContentId = handle.createQuery(sql)
                .mapTo(Long.class)
                .findOne();

        if (maxContentId.isPresent()) {
            long id = maxContentId.get() + 1;

            log.info("Resetting contentId sequence to {}", id);
            while (nextContentId(handle) < id) {}
            log.info("Successfully reset contentId to {}", nextContentId(handle));
        }
    }

    protected void importEntity(Handle handle, Entity entity) throws RegistryStorageException {
        switch (entity.getEntityType()) {
            case ArtifactRule:
                importArtifactRule(handle, (ArtifactRuleEntity) entity);
                break;
            case ArtifactVersion:
                importArtifactVersion(handle, (ArtifactVersionEntity) entity);
                break;
            case Content:
                importContent(handle, (ContentEntity) entity);
                break;
            case GlobalRule:
                importGlobalRule(handle, (GlobalRuleEntity) entity);
                break;
            case Group:
                importGroup(handle, (GroupEntity) entity);
                break;
            case Manifest:
                ManifestEntity manifest = (ManifestEntity) entity;
                log.info("---------- Import Info ----------");
                log.info("System Name:    {}", manifest.systemName);
                log.info("System Desc:    {}", manifest.systemDescription);
                log.info("System Version: {}", manifest.systemVersion);
                log.info("Data exported on {} by user {}", manifest.exportedOn, manifest.exportedBy);
                log.info("---------- ----------- ----------");
                // Ignore the manifest for now.
                break;
            default:
                throw new RegistryStorageException("Unhandled entity type during import: " + entity.getEntityType());
        }
    }
    protected void importArtifactRule(Handle handle, ArtifactRuleEntity entity) {
        if (isArtifactExists(entity.groupId, entity.artifactId)) {
            try {
                String sql = sqlStatements.importArtifactRule();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(entity.groupId))
                    .bind(2, entity.artifactId)
                    .bind(3, entity.type.name())
                    .bind(4, entity.configuration)
                    .execute();
                log.info("Content entity imported successfully.");
            } catch (Exception e) {
                log.warn("Failed to import content entity (likely it already exists).");
            }
        } else {
            log.warn("Artifact rule import failed: artifact not found.");
        }
    }
    protected void importArtifactVersion(Handle handle, ArtifactVersionEntity entity) {
        if (!isArtifactExists(entity.groupId, entity.artifactId)) {
            String sql = sqlStatements.insertArtifact();
            handle.createUpdate(sql)
                .bind(0, tenantContext.tenantId())
                .bind(1, normalizeGroupId(entity.groupId))
                .bind(2, entity.artifactId)
                .bind(3, entity.artifactType.name())
                .bind(4, entity.createdBy)
                .bind(5, new Date(entity.createdOn))
                .execute();
            log.info("Artifact created successfully.");
        }

        if (!isGlobalIdExists(entity.globalId)) {
            try {
                String sql = sqlStatements.importArtifactVersion();
                handle.createUpdate(sql)
                    .bind(0, entity.globalId)
                    .bind(1, tenantContext.tenantId())
                    .bind(2, normalizeGroupId(entity.groupId))
                    .bind(3, entity.artifactId)
                    .bind(4, entity.version)
                    .bind(5, entity.versionId)
                    .bind(6, entity.state)
                    .bind(7, entity.name)
                    .bind(8, entity.description)
                    .bind(9, entity.createdBy)
                    .bind(10, new Date(entity.createdOn))
                    .bind(11, entity.labels)
                    .bind(12, entity.properties)
                    .bind(13, entity.contentId)
                    .execute();
                log.info("Content entity imported successfully.");

                // Insert labels into the "labels" table
                if (entity.labels != null && !entity.labels.isEmpty()) {
                    entity.labels.forEach(label -> {
                        String sqli = sqlStatements.insertLabel();
                        handle.createUpdate(sqli)
                              .bind(0, entity.globalId)
                              .bind(1, label.toLowerCase())
                              .execute();
                    });
                }

                // Insert properties into the "properties" table
                if (entity.properties != null && !entity.properties.isEmpty()) {
                    entity.properties.forEach((k,v) -> {
                        String sqli = sqlStatements.insertProperty();
                        handle.createUpdate(sqli)
                              .bind(0, entity.globalId)
                              .bind(1, k.toLowerCase())
                              .bind(2, v.toLowerCase())
                              .execute();
                    });
                }

                if (entity.isLatest) {
                    // Update the "latest" column in the artifacts table with the globalId of the new version
                    sql = sqlStatements.updateArtifactLatest();
                    handle.createUpdate(sql)
                          .bind(0, entity.globalId)
                          .bind(1, tenantContext.tenantId())
                          .bind(2, normalizeGroupId(entity.groupId))
                          .bind(3, entity.artifactId)
                          .execute();
                }
            } catch (Exception e) {
                log.warn("Failed to import content entity.", e);
            }
        } else {
            log.info("Duplicate globalId detected, skipping import of artifact version.");
        }
    }
    protected void importContent(Handle handle, ContentEntity entity) {
        try {
            if (!isContentExists(entity.contentId)) {
                String sql = sqlStatements.importContent();
                handle.createUpdate(sql)
                    .bind(0, entity.contentId)
                    .bind(1, entity.canonicalHash)
                    .bind(2, entity.contentHash)
                    .bind(3, entity.contentBytes)
                    .execute();
                log.info("Content entity imported successfully.");
            } else {
                log.info("Duplicate content entity already exists, skipped.");
            }
        } catch (Exception e) {
            log.warn("Failed to import content entity.", e);
        }
    }
    protected void importGlobalRule(Handle handle, GlobalRuleEntity entity) {
        try {
            String sql = sqlStatements.importGlobalRule();
            handle.createUpdate(sql)
                .bind(0, tenantContext().tenantId())
                .bind(1, entity.ruleType.name())
                .bind(2, entity.configuration)
                .execute();
            log.info("Global Rule entity imported successfully.");
        } catch (Exception e) {
            log.warn("Failed to import content entity (likely it already exists).");
        }
    }
    protected void importGroup(Handle handle, GroupEntity entity) {
        try {
            String sql = sqlStatements.importGroup();
            handle.createUpdate(sql)
                .bind(0, tenantContext().tenantId())
                .bind(1, SqlUtil.normalizeGroupId(entity.groupId))
                .bind(2, entity.description)
                .bind(3, entity.artifactsType.name())
                .bind(4, entity.createdBy)
                .bind(5, new Date(entity.createdOn))
                .bind(6, entity.modifiedBy)
                .bind(7, new Date(entity.modifiedOn))
                .bind(8, SqlUtil.serializeProperties(entity.properties))
                .execute();
            log.info("Group entity imported successfully.");
        } catch (Exception e) {
            log.warn("Failed to import group entity (likely it already exists).");
        }
    }

    public boolean isArtifactExists(String groupId, String artifactId) throws RegistryStorageException {
        return withHandle( handle -> {
            String sql = sqlStatements().selectArtifactCountById();
            return handle.createQuery(sql)
                    .bind(0, tenantContext().tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }

    public boolean isContentExists(long contentId) throws RegistryStorageException {
        return withHandle( handle -> {
            String sql = sqlStatements().selectContentExists();
            return handle.createQuery(sql)
                    .bind(0, contentId)
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }

    public boolean isGlobalIdExists(long globalId) throws RegistryStorageException {
        return withHandle( handle -> {
            String sql = sqlStatements().selectGlobalIdExists();
            return handle.createQuery(sql)
                    .bind(0, globalId)
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }

    /**
     * Converts a version DTO to an artifact DTO.
     * @param artifactId
     * @param vmdd
     */
    private ArtifactMetaDataDto versionToArtifactDto(String groupId, String artifactId, ArtifactVersionMetaDataDto vmdd) {
        ArtifactMetaDataDto amdd = new ArtifactMetaDataDto();
        amdd.setGlobalId(vmdd.getGlobalId());
        amdd.setContentId(vmdd.getContentId());
        amdd.setGroupId(denormalizeGroupId(groupId));
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
        amdd.setVersionId(vmdd.getVersionId());
        return amdd;
    }

    /**
     * Canonicalize the given content, returns the content unchanged in the case of an error.
     * @param artifactType
     * @param content
     */
    protected ContentHandle canonicalizeContent(ArtifactType artifactType, ContentHandle content) {
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

    protected EditableArtifactMetaDataDto extractMetaData(ArtifactType artifactType, ContentHandle content) {
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
        ContentExtractor extractor = provider.getContentExtractor();
        ExtractedMetaData emd = extractor.extract(content);
        EditableArtifactMetaDataDto metaData;
        if (emd != null) {
            metaData = new EditableArtifactMetaDataDto(emd.getName(), emd.getDescription(), emd.getLabels(), emd.getProperties());
        } else {
            metaData = new EditableArtifactMetaDataDto();
        }
        return metaData;
    }

    private static boolean hasContentFilter(Set<SearchFilter> filters) {
        for (SearchFilter searchFilter : filters) {
            if (searchFilter.getType() == SearchFilterType.contentHash || searchFilter.getType() == SearchFilterType.canonicalHash) {
                return true;
            }
        }
        return false;
    }

    protected static long nextContentId(Handle handle) {
        return handle.createQuery("SELECT nextval('contentidsequence')")
                .mapTo(Long.class)
                .one();
    }

    protected static long nextGlobalId(Handle handle) {
        return handle.createQuery("SELECT nextval('globalidsequence')")
                .mapTo(Long.class)
                .one();
    }

}
