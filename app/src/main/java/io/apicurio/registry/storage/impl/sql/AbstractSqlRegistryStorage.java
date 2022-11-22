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
import java.time.Instant;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.transaction.Transactional;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.VersionAlreadyExistsException;
import io.apicurio.registry.storage.dto.ArtifactOwnerDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.SearchedGroupDto;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedGroupMapper;
import io.apicurio.registry.util.DataImporter;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.Info;
import io.apicurio.common.apps.core.System;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.ExtractedMetaData;
import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.ContentNotFoundException;
import io.apicurio.registry.storage.DownloadNotFoundException;
import io.apicurio.registry.storage.GroupAlreadyExistsException;
import io.apicurio.registry.storage.GroupNotFoundException;
import io.apicurio.registry.storage.LogConfigurationNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RoleMappingAlreadyExistsException;
import io.apicurio.registry.storage.RoleMappingNotFoundException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StorageException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.LogConfigurationDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.RoleMappingDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchFilterType;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.storage.impl.AbstractRegistryStorage;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.Query;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.storage.impl.sql.jdb.Update;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactRuleEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactVersionEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactVersionMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.DynamicConfigPropertyDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ContentEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ContentMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GlobalRuleEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GroupEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GroupMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.LogConfigurationMapper;
import io.apicurio.registry.storage.impl.sql.mappers.RoleMappingDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.RuleConfigurationDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedArtifactMapper;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedVersionMapper;
import io.apicurio.registry.storage.impl.sql.mappers.StoredArtifactMapper;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.StringUtil;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.GroupEntity;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import io.quarkus.security.identity.SecurityIdentity;

import java.util.Collections;
import java.util.LinkedHashMap;


/**
 * A SQL implementation of the {@link RegistryStorage} interface.  This impl does not
 * use any ORM technology - it simply uses native SQL for all operations.
 * @author eric.wittmann@gmail.com
 */
public abstract class AbstractSqlRegistryStorage extends AbstractRegistryStorage {

    private static int DB_VERSION = Integer.valueOf(
        IoUtil.toString(AbstractSqlRegistryStorage.class.getResourceAsStream("db-version"))).intValue();
    private static final Object dbMutex = new Object();
    private static final Object inmemorySequencesMutex = new Object();

    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, true);
    }
    private static final String GLOBAL_ID_SEQUENCE = "globalId";
    private static final String CONTENT_ID_SEQUENCE = "contentId";

    @Inject
    Logger log;

    @Inject
    TenantContext tenantContext;

    protected TenantContext tenantContext() {
        return tenantContext;
    }

    @Inject
    System system;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Inject
    SqlStatements sqlStatements;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    ArtifactStateExt artifactStateEx;

    @Inject
    HandleFactory handles;

    protected SqlStatements sqlStatements() {
        return sqlStatements;
    }

    @ConfigProperty(name = "registry.sql.init", defaultValue = "true")
    @Info(category = "store", description = "SQL init", availableSince = "2.0.0.Final")
    boolean initDB;

    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    @Info(category = "store", description = "Datasource jdbc URL", availableSince = "2.1.0.Final")
    String jdbcUrl;

    /**
     * Constructor.
     */
    public AbstractSqlRegistryStorage() {
    }

    @PostConstruct
    @Transactional
    protected void initialize() {
        log.info("SqlRegistryStorage constructed successfully.  JDBC URL: " + jdbcUrl);

        synchronized (dbMutex) {
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

        statements.forEach( statement -> {
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
        statements.forEach( statement -> {
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

    /**
     * @see RegistryStorage#storageName()
     */
    @Override
    public String storageName() {
        return "sql";
    }

    /**
     * @see RegistryStorage#supportsMultiTenancy()
     */
    @Override
    public boolean supportsMultiTenancy() {
        return true;
    }

    /**
     * @see RegistryStorage#getArtifactByContentId(long)
     */
    @Override
    public ContentWrapperDto getArtifactByContentId(long contentId) throws ContentNotFoundException, RegistryStorageException {
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements().selectContentById();
            Optional<ContentWrapperDto> res = handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, contentId)
                    .map(ContentMapper.instance)
                    .findFirst();
            return res.orElseThrow(() -> new ContentNotFoundException("contentId-" + contentId));
        });
    }

    /**
     * @see RegistryStorage#getArtifactByContentHash(java.lang.String)
     */
    @Override
    public ContentWrapperDto getArtifactByContentHash(String contentHash) throws ContentNotFoundException, RegistryStorageException {
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements().selectContentByContentHash();
            Optional<ContentWrapperDto> res = handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, contentHash)
                    .map(ContentMapper.instance)
                    .findFirst();
            return res.orElseThrow(() -> new ContentNotFoundException("contentHash-" + contentHash));
        });
    }

    /**
     * @see RegistryStorage#getArtifactByContentId(long)
     */
    @Override
    public List<ArtifactMetaDataDto> getArtifactVersionsByContentId(long contentId) {
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements().selectArtifactVersionMetaDataByContentId();
            List<ArtifactMetaDataDto> dtos = handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, contentId)
                    .map(ArtifactMetaDataDtoMapper.instance)
                    .list();
            if (dtos.isEmpty()) {
                throw new ContentNotFoundException("contentId-" + contentId);
            }
            return dtos;
        });
    }

    /**
     * @see RegistryStorage#getArtifactContentIds(String, String)
     */
    @Override
    public List<Long> getArtifactContentIds(String groupId, String artifactId) {
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements().selectArtifactContentIds();

            return handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .mapTo(Long.class)
                    .list();
        });
    }

    /**
     * @see RegistryStorage#updateArtifactState(java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactState)
     */
    @Override @Transactional
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state) throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Updating the state of artifact {} {} to {}", groupId, artifactId, state.name());
        ArtifactMetaDataDto dto = this.getLatestArtifactMetaDataInternal(groupId, artifactId);
        handles.withHandleNoException( handle -> {
            long globalId = dto.getGlobalId();
            ArtifactState oldState = dto.getState();
            ArtifactState newState = state;
            artifactStateEx.applyState(s -> {
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
     * @see RegistryStorage#updateArtifactState(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactState)
     */
    @Override @Transactional
    public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Updating the state of artifact {} {}, version {} to {}", groupId, artifactId, version, state.name());
        ArtifactVersionMetaDataDto dto = this.getArtifactVersionMetaData(groupId, artifactId, version);
        handles.withHandleNoException( handle -> {
            long globalId = dto.getGlobalId();
            ArtifactState oldState = dto.getState();
            ArtifactState newState = state;
            if (oldState != newState) {
                artifactStateEx.applyState(s -> {
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
     * @see RegistryStorage#createArtifact (java.lang.String, java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    @Transactional
    public ArtifactMetaDataDto createArtifact(String groupId, String artifactId, String version, String artifactType,
            ContentHandle content, List<ArtifactReferenceDto> references) throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException {
        return createArtifact(groupId, artifactId, version, artifactType, content, references, null);
    }

    protected ArtifactMetaDataDto createArtifact(String groupId, String artifactId, String version, String artifactType,
            ContentHandle content, List<ArtifactReferenceDto> references, GlobalIdGenerator globalIdGenerator) throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException {
        return this.createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, null, references, globalIdGenerator);
    }

    /**
     * Creates an artifact version by storing information in the versions table.
     */
    private ArtifactVersionMetaDataDto createArtifactVersion(Handle handle, String artifactType,
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
                .bind(6, limitStr(name, 512))
                .bind(7, limitStr(description, 1024, true))
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
                .bind(9, limitStr(name, 512))
                .bind(10, limitStr(description, 1024, true))
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
                        .bind(0, tenantContext.tenantId())
                        .bind(1, globalId)
                        .bind(2, limitStr(label.toLowerCase(), 256))
                        .execute();
            });
        }

        // Insert properties into the "properties" table
        if (properties != null && !properties.isEmpty()) {
            properties.forEach((k,v) -> {
                String sqli = sqlStatements.insertProperty();
                handle.createUpdate(sqli)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, globalId)
                        .bind(2, limitStr(k.toLowerCase(), 256))
                        .bind(3, limitStr(v.toLowerCase(), 1024))
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
                .bind(0, tenantContext.tenantId())
                .bind(1, globalId)
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
    protected Long createOrUpdateContent(Handle handle, String artifactType, ContentHandle content, List<ArtifactReferenceDto> references) {
        byte[] contentBytes = content.bytes();
        String contentHash = DigestUtils.sha256Hex(contentBytes);
        ContentHandle canonicalContent = this.canonicalizeContent(artifactType, content, references);
        byte[] canonicalContentBytes = canonicalContent.bytes();
        String canonicalContentHash = DigestUtils.sha256Hex(canonicalContentBytes);
        return createOrUpdateContent(handle, content, contentHash, canonicalContentHash, references);
    }

    /**
     * Store the content in the database and return the ID of the new row.  If the content already exists,
     * just return the content ID of the existing row.
     *
     * @param handle
     * @param content
     * @param contentHash
     * @param canonicalContentHash
     */
    protected Long createOrUpdateContent(Handle handle, ContentHandle content, String contentHash, String canonicalContentHash, List<ArtifactReferenceDto> references) {
        byte[] contentBytes = content.bytes();
        String referencesSerialized = SqlUtil.serializeReferences(references);

        // Upsert a row in the "content" table.  This will insert a row for the content
        // if a row doesn't already exist.  We use the canonical hash to determine whether
        // a row for this content already exists.  If we find a row we return its globalId.
        // If we don't find a row, we insert one and then return its globalId.
        String sql;
        Long contentId;
        boolean insertReferences = true;
        if ("postgresql".equals(sqlStatements.dbType())) {
            sql = sqlStatements.upsertContent();
            handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, nextContentId(handle))
                    .bind(2, canonicalContentHash)
                    .bind(3, contentHash)
                    .bind(4, contentBytes)
                    .bind(5, referencesSerialized)
                    .execute();
            sql = sqlStatements.selectContentIdByHash();
            contentId = handle.createQuery(sql)
                    .bind(0, contentHash)
                    .bind(1, tenantContext.tenantId())
                    .mapTo(Long.class)
                    .one();
        } else if ("h2".equals(sqlStatements.dbType())) {
            sql = sqlStatements.selectContentIdByHash();
            Optional<Long> contentIdOptional = handle.createQuery(sql)
                    .bind(0, contentHash)
                    .bind(1, tenantContext.tenantId())
                    .mapTo(Long.class)
                    .findOne();
            if (contentIdOptional.isPresent()) {
                contentId = contentIdOptional.get();
                //If the content is already present there's no need to create the references.
                insertReferences = false;
            } else {
                sql = sqlStatements.upsertContent();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, nextContentId(handle))
                    .bind(2, canonicalContentHash)
                    .bind(3, contentHash)
                    .bind(4, contentBytes)
                    .bind(5, referencesSerialized)
                    .execute();
                sql = sqlStatements.selectContentIdByHash();
                contentId = handle.createQuery(sql)
                        .bind(0, contentHash)
                        .bind(1, tenantContext.tenantId())
                        .mapTo(Long.class)
                        .one();
            }
        } else {
            throw new UnsupportedOperationException("Unsupported database type: " + sqlStatements.dbType());
        }

        if (insertReferences) {
            //Finally, insert references into the "artifactreferences" table if the content wasn't present yet.
            insertReferences(handle, contentId, references);
        }
        return contentId;
    }

    protected void insertReferences(Handle handle, Long contentId, List<ArtifactReferenceDto> references) {
        if (references != null && !references.isEmpty()) {
            references.forEach(reference -> {
                try {
                    String sqli = sqlStatements.upsertReference();
                    handle.createUpdate(sqli)
                            .bind(0, tenantContext.tenantId())
                            .bind(1, contentId)
                            .bind(2, normalizeGroupId(reference.getGroupId()))
                            .bind(3, reference.getArtifactId())
                            .bind(4, reference.getVersion())
                            .bind(5, reference.getName())
                            .execute();
                } catch (Exception e) {
                    if (sqlStatements.isPrimaryKeyViolation(e)) {
                        //Do nothing, the reference already exist, only needed for H2
                    } else {
                        throw new RegistryStorageException(e);
                    }
                }
            });
        }
    }

    /**
     * @see RegistryStorage#createArtifactWithMetadata (java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto, java.util.List)
     */
    @Override
    @Transactional
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, ArtifactAlreadyExistsException, RegistryStorageException {
        return createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData, references, null);
    }

    protected ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
            String artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references, GlobalIdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, ArtifactAlreadyExistsException, RegistryStorageException {

        String createdBy = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();

        if (groupId != null && !isGroupExists(groupId)) {
            //Only create group metadata for non-default groups.
            createGroup(GroupMetaDataDto.builder()
                    .groupId(groupId)
                    .createdOn(0)
                    .modifiedOn(0)
                    .createdBy(createdBy)
                    .modifiedBy(createdBy)
                    .build());
        }

        // Put the content in the DB and get the unique content ID back.
        long contentId = handles.withHandleNoException(handle -> {
            return createOrUpdateContent(handle, artifactType, content, references);
        });

        // If the metaData provided is null, try to figure it out from the content.
        EditableArtifactMetaDataDto md = metaData;
        if (md == null) {
            md = extractMetaData(artifactType, content);
        }

        return createArtifactWithMetadata(groupId, artifactId, version, artifactType, contentId, createdBy, createdOn, md, globalIdGenerator);
    }

    protected ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
            String artifactType, long contentId, String createdBy, Date createdOn, EditableArtifactMetaDataDto metaData,
            GlobalIdGenerator globalIdGenerator) {
        log.debug("Inserting an artifact row for: {} {}", groupId, artifactId);
        try {
            return this.handles.withHandle( handle -> {
                // Create a row in the artifacts table.
                String sql = sqlStatements.insertArtifact();
                handle.createUpdate(sql)
                      .bind(0, tenantContext.tenantId())
                      .bind(1, normalizeGroupId(groupId))
                      .bind(2, artifactId)
                      .bind(3, artifactType)
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
                return amdd;
            });
        } catch (Exception e) {
            if (sqlStatements.isPrimaryKeyViolation(e)) {
                throw new ArtifactAlreadyExistsException(groupId, artifactId);
            }
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see RegistryStorage#deleteArtifact(java.lang.String, java.lang.String)
     */
    @Override @Transactional
    public List<String> deleteArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Deleting an artifact: {} {}", groupId, artifactId);
        try {
            List<String> res = this.handles.withHandle(handle -> {
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
                    .bind(1, tenantContext.tenantId())
                    .bind(2, normalizeGroupId(groupId))
                    .bind(3, artifactId)
                    .execute();

                // Delete properties
                sql = sqlStatements.deleteProperties();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, tenantContext.tenantId())
                    .bind(2, normalizeGroupId(groupId))
                    .bind(3, artifactId)
                    .execute();

                // Delete versions
                sql = sqlStatements.deleteVersions();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .execute();

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
            deleteAllOrphanedContent();
            return res;
        } catch (ArtifactNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see RegistryStorage#deleteArtifacts(java.lang.String)
     */
    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        log.debug("Deleting all artifacts in group: {}", groupId);
        try {
            this.handles.withHandle( handle -> {

                // TODO use CASCADE when deleting rows from the "versions" table

                // Delete labels
                String sql = sqlStatements.deleteLabelsByGroupId();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, tenantContext.tenantId())
                    .bind(2, normalizeGroupId(groupId))
                    .execute();

                // Delete properties
                sql = sqlStatements.deletePropertiesByGroupId();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, tenantContext.tenantId())
                    .bind(2, normalizeGroupId(groupId))
                    .execute();

                // Delete versions
                sql = sqlStatements.deleteVersionsByGroupId();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .execute();

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
            deleteAllOrphanedContent();
        } catch (ArtifactNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see RegistryStorage#getArtifact(java.lang.String, java.lang.String)
     */
    @Override
    public StoredArtifactDto getArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact (latest version) by artifactId: {} {}", groupId, artifactId);
        try {
            return this.handles.withHandle( handle -> {
                String sql = sqlStatements.selectLatestArtifactContent();
                Optional<StoredArtifactDto> res = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
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

    /**
     * @see RegistryStorage#updateArtifact (java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override @Transactional
    public ArtifactMetaDataDto updateArtifact(String groupId, String artifactId, String version, String artifactType,
            ContentHandle content, List<ArtifactReferenceDto> references) throws ArtifactNotFoundException, RegistryStorageException {
        return updateArtifact(groupId, artifactId, version, artifactType, content, references, null);
    }

    protected ArtifactMetaDataDto updateArtifact(String groupId, String artifactId, String version, String artifactType,
            ContentHandle content, List<ArtifactReferenceDto> references, GlobalIdGenerator globalIdGenerator) throws ArtifactNotFoundException, RegistryStorageException {
        return updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, null, references, globalIdGenerator);
    }

    /**
     * @see RegistryStorage#updateArtifactWithMetadata (java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override @Transactional
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
            String artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, RegistryStorageException {
        return updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData, references, null);
    }

    protected ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
            String artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references,
            GlobalIdGenerator globalIdGenerator) throws ArtifactNotFoundException, RegistryStorageException {

        String createdBy = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();

        // Put the content in the DB and get the unique content ID back.
        long contentId = handles.withHandleNoException(handle -> {
            return createOrUpdateContent(handle, artifactType, content, references);
        });

        // Extract meta-data from the content if no metadata is provided
        if (metaData == null) {
            metaData = extractMetaData(artifactType, content);
        }

        return updateArtifactWithMetadata(groupId, artifactId, version, artifactType, contentId, createdBy, createdOn,
                metaData, globalIdGenerator);
    }

    protected ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
            String artifactType, long contentId, String createdBy, Date createdOn, EditableArtifactMetaDataDto metaData,
            GlobalIdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException {

        log.debug("Updating artifact {} {} with a new version (content).", groupId, artifactId);

        // Get meta-data from previous (latest) version
        ArtifactMetaDataDto latest = this.getLatestArtifactMetaDataInternal(groupId, artifactId);

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
                ArtifactVersionMetaDataDto versionDto = this.createArtifactVersion(handle, artifactType, false, groupId, artifactId, version,
                        name, description, labels, properties, createdBy, createdOn, contentId, globalIdGenerator);
                ArtifactMetaDataDto dto = versionToArtifactDto(groupId, artifactId, versionDto);
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

    /**
     * @see RegistryStorage#getArtifactIds(java.lang.Integer)
     */
    @Override
    public Set<String> getArtifactIds(Integer limit) {
        //Set limit to max integer in case limit is null (not allowed)
        final Integer adjustedLimit = limit == null ? Integer.MAX_VALUE : limit;
        log.debug("Getting the set of all artifact IDs");
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements.selectArtifactIds();
            List<String> ids = handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, adjustedLimit)
                    .mapTo(String.class)
                    .list();
            return new TreeSet<String>(ids);
        });
    }

    /**
     * @see RegistryStorage#searchArtifacts(java.util.Set, io.apicurio.registry.storage.dto.OrderBy, io.apicurio.registry.storage.dto.OrderDirection, int, int)
     */
    @Override @Transactional
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy, OrderDirection orderDirection,
            int offset, int limit) {
        return handles.withHandleNoException( handle -> {
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
                select.append("JOIN content c ON v.contentId = c.contentId AND v.tenantId = c.tenantId ");
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
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        break;
                    case everything:
                        where.append("("
                                + "v.name LIKE ? OR "
                                + "v.groupId LIKE ? OR "
                                + "a.artifactId LIKE ? OR "
                                + "v.description LIKE ? OR "
                                + "EXISTS(SELECT l.globalId FROM labels l WHERE l.label = ? AND l.globalId = v.globalId AND l.tenantId = v.tenantId) OR "
                                + "EXISTS(SELECT p.globalId FROM properties p WHERE p.pkey = ? AND p.globalId = v.globalId AND p.tenantId = v.tenantId)"
                                + ")");
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
                        where.append("EXISTS(SELECT l.globalId FROM labels l WHERE l.label = ? AND l.globalId = v.globalId AND l.tenantId = v.tenantId)");
                        binders.add((query, idx) -> {
                          //    Note: convert search to lowercase when searching for labels (case-insensitivity support).
                            query.bind(idx, filter.getStringValue().toLowerCase());
                        });
                        break;
                    case name:
                        where.append("(v.name LIKE ?) OR (a.artifactId LIKE ?)");
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        break;
                    case group:
                        where.append("(v.groupId = ?)");
                        binders.add((query, idx) -> {
                            query.bind(idx, normalizeGroupId(filter.getStringValue()));
                        });
                        break;
                    case contentHash:
                        where.append("(c.contentHash = ?)");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getStringValue());
                        });
                        break;
                    case canonicalHash:
                        where.append("(c.canonicalHash = ?)");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getStringValue());
                        });
                        break;
                    case properties:
                        Pair<String, String> property = filter.getPropertyFilterValue();
                        //    Note: convert search to lowercase when searching for properties (case-insensitivity support).
                        String propKey = property.getKey().toLowerCase();
                        where.append("EXISTS(SELECT p.globalId FROM properties p WHERE p.pkey = ? ");
                        binders.add((query, idx) -> {
                            query.bind(idx, propKey);
                        });
                        if (property.getValue() != null) {
                            String propValue = property.getValue().toLowerCase();
                            where.append("AND p.pvalue = ? ");
                            binders.add((query, idx) -> {
                                query.bind(idx, propValue);
                            });
                        }
                        where.append("AND p.globalId = v.globalId AND p.tenantId = v.tenantId)");
                        break;
                    case globalId:
                        where.append("(v.globalId = ?)");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getNumberValue().longValue());
                        });
                        break;
                    case contentId:
                        where.append("(v.contentId = ?)");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getNumberValue().longValue());
                        });
                        break;
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
            String countSelect = "SELECT count(a.artifactId) "
                    + "FROM artifacts a "
                    + "JOIN versions v ON a.tenantId = v.tenantId AND a.latest = v.globalId ";
            if (joinContentTable) {
                countSelect += "JOIN content c ON v.contentId = c.contentId AND v.tenantId = c.tenantId ";
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
     * @see RegistryStorage#getArtifactMetaData(java.lang.String, java.lang.String)
     */
    @Override
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
            return this.handles.withHandle( handle -> {
                String sql = sqlStatements.selectLatestArtifactMetaData();
                Optional<ArtifactMetaDataDto> res = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .map(ArtifactMetaDataDtoMapper.instance)
                        .findOne();
                return res.orElseThrow(() -> new ArtifactNotFoundException(groupId, artifactId));
            });
        } catch (ArtifactNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see RegistryStorage#getArtifactVersionMetaData(java.lang.String, java.lang.String, boolean, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, boolean canonical,
            ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        String hash;
        if (canonical) {
            final ArtifactMetaDataDto artifactMetaData = this.getArtifactMetaData(groupId, artifactId);
            final ContentWrapperDto contentWrapperDto = getArtifactByContentId(artifactMetaData.getContentId());
            String type = artifactMetaData.getType();
            ContentHandle canonicalContent = this.canonicalizeContent(type, content, contentWrapperDto.getReferences());
            hash = DigestUtils.sha256Hex(canonicalContent.bytes());
        } else {
            hash = DigestUtils.sha256Hex(content.bytes());
        }

        try {
            return this.handles.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactVersionMetaDataByContentHash();
                if (canonical) {
                    sql = sqlStatements.selectArtifactVersionMetaDataByCanonicalHash();
                }
                Optional<ArtifactVersionMetaDataDto> res = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .bind(3, hash)
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

    /**
     * @see RegistryStorage#getArtifactMetaData(long)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting meta-data for globalId: {}", globalId);
        try {
            return this.handles.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactMetaDataByGlobalId();
                Optional<ArtifactMetaDataDto> res = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, globalId)
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

    /**
     * @see RegistryStorage#updateArtifactMetaData(java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override @Transactional
    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Updating meta-data for an artifact: {} {}", groupId, artifactId);

        ArtifactMetaDataDto dto = this.getLatestArtifactMetaDataInternal(groupId, artifactId);

        internalUpdateArtifactVersionMetadata(dto.getGlobalId(), groupId, artifactId, dto.getVersion(), metaData);
    }

    /**
     * @see RegistryStorage#updateArtifactOwner(String, String, ArtifactOwnerDto)
     */
    @Override @Transactional
    public void updateArtifactOwner(String groupId, String artifactId, ArtifactOwnerDto owner) throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Updating ownership of an artifact: {} {}", groupId, artifactId);

        try {
            this.handles.withHandle( handle -> {
                String sql = sqlStatements.updateArtifactOwner();
                int rowCount = handle.createUpdate(sql)
                        .bind(0, owner.getOwner())
                        .bind(1, tenantContext.tenantId())
                        .bind(2, normalizeGroupId(groupId))
                        .bind(3, artifactId)
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
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see RegistryStorage#getArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    public List<RuleType> getArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting a list of all artifact rules for: {} {}", groupId, artifactId);
        try {
            return this.handles.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactRules();
                List<RuleType> rules = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
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

    /**
     * @see RegistryStorage#createArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override @Transactional
    public void createArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        log.debug("Inserting an artifact rule row for artifact: {} {} rule: {}", groupId, artifactId, rule.name());
        try {
            this.handles.withHandle( handle -> {
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
    }

    /**
     * @see RegistryStorage#deleteArtifactRules(java.lang.String, java.lang.String)
     */
    @Override @Transactional
    public void deleteArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Deleting all artifact rules for artifact: {} {}", groupId, artifactId);
        try {
            this.handles.withHandle( handle -> {
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
     * @see RegistryStorage#getArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact rule for artifact: {} {} and rule: {}", groupId, artifactId, rule.name());
        try {
            return this.handles.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactRuleByType();
                Optional<RuleConfigurationDto> res = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .bind(3, rule.name())
                        .map(RuleConfigurationDtoMapper.instance)
                        .findOne();
                return res.orElseThrow(() -> {
                    if (!isArtifactExists(groupId, artifactId)) {
                        return new ArtifactNotFoundException(groupId, artifactId);
                    }
                    return new RuleNotFoundException(rule);
                });
            });
        } catch (ArtifactNotFoundException e) {
            throw e;
        } catch (RuleNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see RegistryStorage#updateArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override @Transactional
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Updating an artifact rule for artifact: {} {} and rule: {}::{}", groupId, artifactId, rule.name(), config.getConfiguration());
        try {
            this.handles.withHandle( handle -> {
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
     * @see RegistryStorage#deleteArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override @Transactional
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Deleting an artifact rule for artifact: {} {} and rule: {}", groupId, artifactId, rule.name());
        try {
            this.handles.withHandle( handle -> {
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
     * @see RegistryStorage#getArtifactVersions(java.lang.String, java.lang.String)
     */
    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting a list of versions for artifact: {} {}", groupId, artifactId);
        try {
            return this.handles.withHandle( handle -> {
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
     * @see RegistryStorage#searchVersions(java.lang.String, java.lang.String, int, int)
     */
    @Override @Transactional
    public VersionSearchResultsDto searchVersions(String groupId, String artifactId, int offset, int limit) {
        log.debug("Searching for versions of artifact {} {}", groupId, artifactId);
        return handles.withHandleNoException( handle -> {
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
     * @see RegistryStorage#getArtifactVersion(long)
     */
    @Override
    public StoredArtifactDto getArtifactVersion(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact version by globalId: {}", globalId);
        try {
            return this.handles.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactVersionContentByGlobalId();
                Optional<StoredArtifactDto> res = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, globalId)
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

    /**
     * @see RegistryStorage#getArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public StoredArtifactDto getArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact version by artifactId: {} {} and version {}", groupId, artifactId, version);
        try {
            return this.handles.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactVersionContent();
                Optional<StoredArtifactDto> res = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .bind(3, version)
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

    /**
     * @see RegistryStorage#deleteArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
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
            this.handles.withHandle( handle -> {
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
                    .bind(1, tenantContext.tenantId())
                    .bind(2, normalizeGroupId(groupId))
                    .bind(3, artifactId)
                    .bind(4, version)
                    .execute();

                // Delete properties
                sql = sqlStatements.deleteVersionProperties();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, tenantContext.tenantId())
                    .bind(2, normalizeGroupId(groupId))
                    .bind(3, artifactId)
                    .bind(4, version)
                    .execute();

                // Delete version
                sql = sqlStatements.deleteVersion();
                int rows = handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .bind(3, version)
                    .execute();

                // If the row was deleted, update the "latest" column to the globalId of the highest remaining version
                if (rows == 1) {
                    versions.remove(version);

                    // Update the 'latest' version of the artifact to the globalId of the highest remaining version
                    String latestVersion = versions.get(versions.size() - 1);
                    sql = sqlStatements.updateArtifactLatestGlobalId();
                    int latestUpdateRows = handle.createUpdate(sql)
                          .bind(0, tenantContext.tenantId())
                          .bind(1, normalizeGroupId(groupId))
                          .bind(2, artifactId)
                          .bind(3, latestVersion)
                          .bind(4, tenantContext.tenantId())
                          .bind(5, normalizeGroupId(groupId))
                          .bind(6, artifactId)
                          .execute();
                    if (latestUpdateRows == 0) {
                        throw new RegistryStorageException("latest column was not updated");
                    }
                }

                if (rows == 0) {
                    throw new VersionNotFoundException(groupId, artifactId, version);
                }

                if (rows > 1) {
                    throw new RegistryStorageException("Multiple versions deleted, artifact latest column left null");
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

    /**
     * @see RegistryStorage#getArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Selecting artifact version meta-data: {} {} version {}", groupId, artifactId, version);
        return getArtifactVersionMetaDataInternal(groupId, artifactId, version);
    }

    private ArtifactVersionMetaDataDto getArtifactVersionMetaDataInternal(String groupId, String artifactId, String version) {
        try {
            return this.handles.withHandle( handle -> {
                String sql = sqlStatements.selectArtifactVersionMetaData();
                Optional<ArtifactVersionMetaDataDto> res = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId)
                        .bind(3, version)
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

    /**
     * @see RegistryStorage#updateArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
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
            this.handles.withHandle( handle -> {
                String sql = sqlStatements.updateArtifactVersionMetaData();
                int rowCount = handle.createUpdate(sql)
                        .bind(0, limitStr(metaData.getName(), 512))
                        .bind(1, limitStr(metaData.getDescription(), 1024, true))
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
                    .bind(0, tenantContext.tenantId())
                    .bind(1, globalId)
                    .execute();

                // Delete all appropriate rows in the "properties" table
                sql = sqlStatements.deletePropertiesByGlobalId();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, globalId)
                    .execute();

                // Insert new labels into the "labels" table
                List<String> labels = metaData.getLabels();
                if (labels != null && !labels.isEmpty()) {
                    labels.forEach(label -> {
                        String sqli = sqlStatements.insertLabel();
                        handle.createUpdate(sqli)
                                .bind(0, tenantContext.tenantId())
                                .bind(1, globalId)
                                .bind(2, limitStr(label.toLowerCase(), 256))
                                .execute();
                    });
                }

                // Insert new properties into the "properties" table
                Map<String, String> properties = metaData.getProperties();
                if (properties != null && !properties.isEmpty()) {
                    properties.forEach((k,v) -> {
                        String sqli = sqlStatements.insertProperty();
                        handle.createUpdate(sqli)
                                .bind(0, tenantContext.tenantId())
                                .bind(1, globalId)
                                .bind(2, limitStr(k.toLowerCase(), 256))
                                .bind(3, limitStr(v.toLowerCase(), 1024))
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
     * @see RegistryStorage#deleteArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override @Transactional
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Deleting user-defined meta-data for artifact {} {} version {}", groupId, artifactId, version);
        try {
            this.handles.withHandle( handle -> {
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
                    .bind(1, tenantContext.tenantId())
                    .bind(2, normalizeGroupId(groupId))
                    .bind(3, artifactId)
                    .bind(4, version)
                    .execute();

                // Delete properties
                sql = sqlStatements.deleteVersionProperties();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, tenantContext.tenantId())
                    .bind(2, normalizeGroupId(groupId))
                    .bind(3, artifactId)
                    .bind(4, version)
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
     * @see RegistryStorage#getGlobalRules()
     */
    @Override
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements.selectGlobalRules();
            return handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .map(new RowMapper<RuleType>() {
                        @Override
                        public RuleType map(ResultSet rs) throws SQLException {
                            return RuleType.fromValue(rs.getString("type"));
                        }
                    })
                    .list();
        });
    }

    /**
     * @see RegistryStorage#createGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override @Transactional
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        log.debug("Inserting a global rule row for: {}", rule.name());
        try {
            this.handles.withHandle( handle -> {
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
      * @see RegistryStorage#deleteGlobalRules()
      */
    @Override @Transactional
    public void deleteGlobalRules() throws RegistryStorageException {
        log.debug("Deleting all Global Rules");
        handles.withHandleNoException( handle -> {
            String sql = sqlStatements.deleteGlobalRules();
            handle.createUpdate(sql)
                .bind(0, tenantContext.tenantId())
                .execute();
            return null;
        });
    }

    /**
     * @see RegistryStorage#getGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule)
            throws RuleNotFoundException, RegistryStorageException {
        log.debug("Selecting a single global rule: {}", rule.name());
        try {
            return this.handles.withHandle( handle -> {
                String sql = sqlStatements.selectGlobalRuleByType();
                Optional<RuleConfigurationDto> res = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, rule.name())
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

    /**
     * @see RegistryStorage#updateGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override @Transactional
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleNotFoundException, RegistryStorageException {
        log.debug("Updating a global rule: {}::{}", rule.name(), config.getConfiguration());
        try {
            this.handles.withHandle( handle -> {
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
     * @see RegistryStorage#deleteGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override @Transactional
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        log.debug("Deleting a global rule: {}", rule.name());
        try {
            this.handles.withHandle( handle -> {
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
     * @see io.apicurio.common.apps.config.DynamicConfigStorage#getConfigProperties()
     */
    @Override
    public List<DynamicConfigPropertyDto> getConfigProperties() throws RegistryStorageException {
        log.debug("Getting all config properties.");
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements.selectConfigProperties();
            return handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .map(DynamicConfigPropertyDtoMapper.instance)
                    .list()
                    .stream()
                    // Filter out possible null values.
                    .filter(item -> item != null)
                    .collect(Collectors.toList());
        });
    }

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigStorage#getConfigProperty(java.lang.String)
     */
    @Override
    public DynamicConfigPropertyDto getConfigProperty(String propertyName) throws RegistryStorageException {
        return this.getRawConfigProperty(propertyName);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getRawConfigProperty(java.lang.String)
     */
    @Override
    public DynamicConfigPropertyDto getRawConfigProperty(String propertyName) {
        log.debug("Selecting a single config property: {}", propertyName);
        try {
            return this.handles.withHandle( handle -> {
                String sql = sqlStatements.selectConfigPropertyByName();
                Optional<DynamicConfigPropertyDto> res = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, propertyName)
                        .map(DynamicConfigPropertyDtoMapper.instance)
                        .findOne();
                return res.orElse(null);
            });
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigStorage#setConfigProperty(io.apicurio.common.apps.config.DynamicConfigPropertyDto)
     */
    @Override @Transactional
    public void setConfigProperty(DynamicConfigPropertyDto propertyDto) throws RegistryStorageException {
        log.debug("Setting a config property with name: {}  and value: {}", propertyDto.getName(), propertyDto.getValue());
        this.handles.withHandleNoException( handle -> {
            String propertyName = propertyDto.getName();
            String propertyValue = propertyDto.getValue();

            // First delete the property row from the table
            String sql = sqlStatements.deleteConfigProperty();
            handle.createUpdate(sql)
                  .bind(0, tenantContext.tenantId())
                  .bind(1, propertyName)
                  .execute();

            // Then create the row again with the new value
            sql = sqlStatements.insertConfigProperty();
            handle.createUpdate(sql)
                  .bind(0, tenantContext.tenantId())
                  .bind(1, propertyName)
                  .bind(2, propertyValue)
                  .bind(3, java.lang.System.currentTimeMillis())
                  .execute();

            return null;
        });
    }

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigStorage#deleteConfigProperty(java.lang.String)
     */
    @Override @Transactional
    public void deleteConfigProperty(String propertyName) throws RegistryStorageException {
        handles.withHandle(handle -> {
            String sql = sqlStatements.deleteConfigProperty();
            handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, propertyName)
                    .execute();
            return null;
        });
    }

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigStorage#getTenantsWithStaleConfigProperties(java.time.Instant)
     */
    @Override
    public List<String> getTenantsWithStaleConfigProperties(Instant lastRefresh) throws RegistryStorageException {
        log.debug("Getting all tenant IDs with stale config properties.");
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements.selectTenantIdsByConfigModifiedOn();
            return handle.createQuery(sql)
                    .bind(0, lastRefresh.toEpochMilli())
                    .mapTo(String.class)
                    .list();
        });
    }

    /**
     * @see RegistryStorage#getLogConfiguration(java.lang.String)
     */
    @Override
    public LogConfigurationDto getLogConfiguration(String logger) throws RegistryStorageException, LogConfigurationNotFoundException {
        log.debug("Selecting a single log configuration: {}", logger);
        try {
            return this.handles.withHandle(handle -> {
                String sql = sqlStatements.selectLogConfigurationByLogger();
                Optional<LogConfigurationDto> res = handle.createQuery(sql)
                        .bind(0, logger)
                        .map(LogConfigurationMapper.instance)
                        .findOne();
                return res.orElseThrow(() -> new LogConfigurationNotFoundException(logger));
            });
        } catch (LogConfigurationNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see RegistryStorage#setLogConfiguration(io.apicurio.registry.storage.dto.LogConfigurationDto)
     */
    @Override @Transactional
    public void setLogConfiguration(LogConfigurationDto logConfiguration) throws RegistryStorageException {
        log.debug("Upsert log configuration: {}", logConfiguration.getLogger());
        handles.withHandleNoException(handle -> {
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
     * @see RegistryStorage#removeLogConfiguration(java.lang.String)
     */
    @Override @Transactional
    public void removeLogConfiguration(String logger) throws RegistryStorageException, LogConfigurationNotFoundException {
        log.debug("Removing a log configuration: {}", logger);
        handles.withHandleNoException( handle -> {
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
     * @see RegistryStorage#listLogConfigurations()
     */
    @Override
    public List<LogConfigurationDto> listLogConfigurations() throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            String sql = sqlStatements.selectAllLogConfigurations();
            return handle.createQuery(sql)
                    .map(LogConfigurationMapper.instance)
                    .list();
        });
    }

    /**
     * @see RegistryStorage#createGroup(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override @Transactional
    public void createGroup(GroupMetaDataDto group) throws GroupAlreadyExistsException, RegistryStorageException {
        try {
            this.handles.withHandle( handle -> {
                String sql = sqlStatements.insertGroup();
                handle.createUpdate(sql)
                      .bind(0, tenantContext.tenantId())
                      .bind(1, group.getGroupId())
                      .bind(2, group.getDescription())
                      .bind(3, group.getArtifactsType())
                      .bind(4, group.getCreatedBy())
                      // TODO io.apicurio.registry.storage.dto.GroupMetaDataDto should not use raw numeric timestamps
                      .bind(5, group.getCreatedOn() == 0 ? new Date() : new Date(group.getCreatedOn()))
                      .bind(6, group.getModifiedBy())
                      .bind(7, group.getModifiedOn() == 0 ? null : new Date(group.getModifiedOn()))
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
     * @see RegistryStorage#updateGroupMetaData(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override @Transactional
    public void updateGroupMetaData(GroupMetaDataDto group) throws GroupNotFoundException, RegistryStorageException {
        handles.withHandleNoException(handle -> {
            String sql = sqlStatements.updateGroup();
            int rows = handle.createUpdate(sql)
                  .bind(0, group.getDescription())
                  .bind(1, group.getArtifactsType())
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
     * @see RegistryStorage#deleteGroup(java.lang.String)
     */
    @Override @Transactional
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        handles.withHandleNoException(handle -> {
            String sql = sqlStatements.deleteGroup();
            int rows = handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, groupId)
                    .execute();
            if (rows == 0) {
                throw new GroupNotFoundException(groupId);
            }
            try {
                deleteArtifacts(groupId);
            } catch (ArtifactNotFoundException anfe) {
                //Just ignore, group with no artifacts
            }
            return null;
        });
    }

    /**
     * @see RegistryStorage#getGroupIds(java.lang.Integer)
     */
    @Override
    public List<String> getGroupIds(Integer limit) throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            String sql = sqlStatements.selectGroups();
            List<String> groups = handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, limit)
                    .map(new RowMapper<String>() {
                        @Override
                        public String map(ResultSet rs) throws SQLException {
                            return rs.getString("groupId");
                        }
                    })
                    .list();
            return groups;
        });
    }

    /**
     * @see RegistryStorage#getGroupMetaData(java.lang.String)
     */
    @Override
    public GroupMetaDataDto getGroupMetaData(String groupId) throws GroupNotFoundException, RegistryStorageException {
        try {
            return this.handles.withHandle(handle -> {
                String sql = sqlStatements.selectGroupByGroupId();
                Optional<GroupMetaDataDto> res = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, groupId)
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
     * @see RegistryStorage#exportData(java.util.function.Function)
     */
    @Override @Transactional
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
            this.handles.withHandle(handle -> {
                String sql = sqlStatements.exportContent();
                Stream<ContentEntity> stream = handle.createQuery(sql)
                        .bind(0, tenantContext().tenantId())
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
            this.handles.withHandle(handle -> {
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
            this.handles.withHandle(handle -> {
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
            this.handles.withHandle(handle -> {
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
            this.handles.withHandle(handle -> {
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
     * @see RegistryStorage#importData(io.apicurio.registry.storage.impexp.EntityInputStream, boolean, boolean)
     */
    @Override
    public void importData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId) throws RegistryStorageException {
        handles.withHandleNoException(handle -> {
            DataImporter dataImporter;
            if (preserveContentId) {
                dataImporter = new SqlDataImporter(log, handle, this, preserveGlobalId);
            } else {
                dataImporter = new ContentIdNotPreserveSqlDataImporter(log, handle, this, preserveGlobalId);
            }

            Entity entity = null;
            while ((entity = entities.nextEntity()) != null) {
                dataImporter.importEntity(entity);
            }

            // Make sure the contentId sequence is set high enough
            resetContentId(handle);

            // Make sure the globalId sequence is set high enough
            resetGlobalId(handle);

            return null;
        });
    }

    /**
     * @see RegistryStorage#countArtifacts()
     */
    @Override
    public long countArtifacts() throws RegistryStorageException {
        return handles.withHandle(handle -> {
            String sql = sqlStatements.selectAllArtifactCount();
            Long count = handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .mapTo(Long.class)
                    .one();
            return count;
        });
    }

    /**
     * @see RegistryStorage#countArtifactVersions(java.lang.String, java.lang.String)
     */
    @Override
    public long countArtifactVersions(String groupId, String artifactId) throws RegistryStorageException {
        if (!isArtifactExists(groupId, artifactId)) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }

        return handles.withHandle(handle -> {
            String sql = sqlStatements.selectAllArtifactVersionsCount();
            Long count = handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .mapTo(Long.class)
                    .one();
            return count;
        });
    }

    /**
     * @see RegistryStorage#countTotalArtifactVersions()
     */
    @Override
    public long countTotalArtifactVersions() throws RegistryStorageException {
        return handles.withHandle(handle -> {
            String sql = sqlStatements.selectTotalArtifactVersionsCount();
            Long count = handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .mapTo(Long.class)
                    .one();
            return count;
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createRoleMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override @Transactional
    public void createRoleMapping(String principalId, String role, String principalName) throws RegistryStorageException {
        log.debug("Inserting a role mapping row for: {}", principalId);
        try {
            this.handles.withHandle( handle -> {
                String sql = sqlStatements.insertRoleMapping();
                handle.createUpdate(sql)
                      .bind(0, tenantContext.tenantId())
                      .bind(1, principalId)
                      .bind(2, role)
                      .bind(3, principalName)
                      .execute();
                return null;
            });
        } catch (Exception e) {
            if (sqlStatements.isPrimaryKeyViolation(e)) {
                throw new RoleMappingAlreadyExistsException();
            }
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see RegistryStorage#deleteRoleMapping(java.lang.String)
     */
    @Override @Transactional
    public void deleteRoleMapping(String principalId) throws RegistryStorageException {
        log.debug("Deleting a role mapping row for: {}", principalId);
        try {
            this.handles.withHandle( handle -> {
                String sql = sqlStatements.deleteRoleMapping();
                int rowCount = handle.createUpdate(sql)
                      .bind(0, tenantContext.tenantId())
                      .bind(1, principalId)
                      .execute();
                if (rowCount == 0) {
                    throw new RoleMappingNotFoundException();
                }
                return null;
            });
        } catch (RoleMappingNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see RegistryStorage#getRoleMapping(java.lang.String)
     */
    @Override
    public RoleMappingDto getRoleMapping(String principalId) throws RegistryStorageException {
        log.debug("Selecting a single role mapping for: {}", principalId);
        try {
            return this.handles.withHandle( handle -> {
                String sql = sqlStatements.selectRoleMappingByPrincipalId();
                Optional<RoleMappingDto> res = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, principalId)
                        .map(RoleMappingDtoMapper.instance)
                        .findOne();
                return res.orElseThrow(() -> new RoleMappingNotFoundException());
            });
        } catch (RoleMappingNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see RegistryStorage#getRoleForPrincipal(java.lang.String)
     */
    @Override
    public String getRoleForPrincipal(String principalId) throws RegistryStorageException {
        log.debug("Selecting the role for: {}", principalId);
        try {
            return this.handles.withHandle( handle -> {
                String sql = sqlStatements.selectRoleByPrincipalId();
                Optional<String> res = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, principalId)
                        .mapTo(String.class)
                        .findOne();
                return res.orElse(null);
            });
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see RegistryStorage#getRoleMappings()
     */
    @Override
    public List<RoleMappingDto> getRoleMappings() throws RegistryStorageException {
        log.debug("Getting a list of all role mappings.");
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements.selectRoleMappings();
            return handle.createQuery(sql)
                    .bind(0, tenantContext.tenantId())
                    .map(RoleMappingDtoMapper.instance)
                    .list();
        });
    }

    /**
     * @see RegistryStorage#updateRoleMapping(java.lang.String, java.lang.String)
     */
    @Override @Transactional
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException {
        log.debug("Updating a role mapping: {}::{}", principalId, role);
        try {
            this.handles.withHandle( handle -> {
                String sql = sqlStatements.updateRoleMapping();
                int rowCount = handle.createUpdate(sql)
                        .bind(0, role)
                        .bind(1, tenantContext.tenantId())
                        .bind(2, principalId)
                        .execute();
                if (rowCount == 0) {
                    throw new RoleMappingNotFoundException();
                }
                return null;
            });
        } catch (RoleMappingNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createDownload(io.apicurio.registry.storage.dto.DownloadContextDto)
     */
    @Override
    @Transactional
    public String createDownload(DownloadContextDto context) throws RegistryStorageException {
        log.debug("Inserting a download.");
        try {
            String downloadId = UUID.randomUUID().toString();
            return this.handles.withHandle( handle -> {
                String sql = sqlStatements.insertDownload();
                handle.createUpdate(sql)
                      .bind(0, tenantContext.tenantId())
                      .bind(1, downloadId)
                      .bind(2, context.getExpires())
                      .bind(3, mapper.writeValueAsString(context))
                      .execute();
                return downloadId;
            });
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#consumeDownload(java.lang.String)
     */
    @Override @Transactional
    public DownloadContextDto consumeDownload(String downloadId) throws RegistryStorageException {
        log.debug("Consuming a download ID: {}", downloadId);

        try {
            return this.handles.withHandle( handle -> {
                long now = java.lang.System.currentTimeMillis();

                // Select the download context.
                String sql = sqlStatements.selectDownloadContext();
                Optional<String> res = handle.createQuery(sql)
                        .bind(0, tenantContext.tenantId())
                        .bind(1, downloadId)
                        .bind(2, now)
                        .mapTo(String.class)
                        .findOne();
                String downloadContext = res.orElseThrow(() -> new DownloadNotFoundException());

                // Attempt to delete the row.
                sql = sqlStatements.deleteDownload();
                int rowCount = handle.createUpdate(sql)
                      .bind(0, tenantContext.tenantId())
                      .bind(1, downloadId)
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

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteAllExpiredDownloads()
     */
    @Override @Transactional
    public void deleteAllExpiredDownloads() throws RegistryStorageException {
        log.debug("Deleting all expired downloads");
        long now = java.lang.System.currentTimeMillis();
        handles.withHandleNoException( handle -> {
            String sql = sqlStatements.deleteExpiredDownloads();
            handle.createUpdate(sql)
                .bind(0, now)
                .execute();
            return null;
        });
    }

    @Override @Transactional
    public void deleteAllUserData() {
        log.debug("Deleting all user data");

        deleteGlobalRules();

        handles.withHandleNoException( handle -> {
            // Delete all artifacts and related data

            String sql = sqlStatements.deleteAllReferences();
            handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .execute();

            sql = sqlStatements.deleteAllLabels();
            handle.createUpdate(sql)
                .bind(0, tenantContext.tenantId())
                .bind(1, tenantContext.tenantId())
                .execute();

            sql = sqlStatements.deleteAllProperties();
            handle.createUpdate(sql)
                .bind(0, tenantContext.tenantId())
                .bind(1, tenantContext.tenantId())
                .execute();

            sql = sqlStatements.deleteAllVersions();
            handle.createUpdate(sql)
                .bind(0, tenantContext.tenantId())
                .execute();

            sql = sqlStatements.deleteAllArtifactRules();
            handle.createUpdate(sql)
                .bind(0, tenantContext.tenantId())
                .execute();

            sql = sqlStatements.deleteAllArtifacts();
            handle.createUpdate(sql)
                .bind(0, tenantContext.tenantId())
                .execute();

            // Delete all groups

            sql = sqlStatements.deleteAllGroups();
            handle.createUpdate(sql)
                .bind(0, tenantContext.tenantId())
                .execute();

            // Delete all role mappings

            sql = sqlStatements.deleteAllRoleMappings();
            handle.createUpdate(sql)
                .bind(0, tenantContext.tenantId())
                .execute();

            // Delete all content by tenantId

            sql = sqlStatements.deleteAllContent();
            handle.createUpdate(sql)
                .bind(0, tenantContext.tenantId())
                .execute();

            // Delete all config properties

            sql = sqlStatements.deleteAllConfigProperties();
            handle.createUpdate(sql)
                .bind(0, tenantContext.tenantId())
                .execute();
            return null;
        });

    }

    /**
     * @see RegistryStorage#resolveReferences(java.util.List)
     */
    @Override
    public Map<String, ContentHandle> resolveReferences(List<ArtifactReferenceDto> references) {
        if (references == null || references.isEmpty()) {
            return Collections.emptyMap();
        } else {
            Map<String, ContentHandle> result = new LinkedHashMap<>();
            resolveReferences(result, references);
            return result;
        }
    }

    /**
     * @see RegistryStorage#isArtifactExists(String, String)
     */
    @Override
    public boolean isArtifactExists(String groupId, String artifactId) throws RegistryStorageException {
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements().selectArtifactCountById();
            return handle.createQuery(sql)
                    .bind(0, tenantContext().tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }

    /**
     * @see RegistryStorage#isGroupExists(String)
     */
    @Override
    public boolean isGroupExists(String groupId) throws RegistryStorageException {
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements().selectGroupCountById();
            return handle.createQuery(sql)
                    .bind(0, tenantContext().tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }

    /**
     * @see RegistryStorage#getContentIdsReferencingArtifact(String, String, String)
     */
    @Override
    public List<Long> getContentIdsReferencingArtifact(String groupId, String artifactId, String version) {
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements().selectContentIdsReferencingArtifactBy();
            return handle.createQuery(sql)
                    .bind(0, tenantContext().tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .bind(3, version)
                    .mapTo(Long.class)
                    .list();
        });
    }

    /**
     * @see RegistryStorage#getGlobalIdsReferencingArtifact(String, String, String)
     */
    @Override
    public List<Long> getGlobalIdsReferencingArtifact(String groupId, String artifactId, String version) {
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements().selectGlobalIdsReferencingArtifactBy();
            return handle.createQuery(sql)
                    .bind(0, tenantContext().tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .bind(3, version)
                    .mapTo(Long.class)
                    .list();
        });
    }

    /**
     * @see RegistryStorage#isArtifactExists(String, String)
     */
    @Override
    public boolean isArtifactVersionExists(String groupId, String artifactId, String version) throws RegistryStorageException {
        try {
            getArtifactVersionMetaData(groupId, artifactId, version);
            return true;
        } catch (VersionNotFoundException ignored) {
            return false;
        }
    }

    @Override
    public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy, OrderDirection orderDirection, Integer offset, Integer limit) {

        return handles.withHandleNoException( handle -> {
            List<SqlStatementVariableBinder> binders = new LinkedList<>();

            StringBuilder select = new StringBuilder();
            StringBuilder where = new StringBuilder();
            StringBuilder orderByQuery = new StringBuilder();
            StringBuilder limitOffset = new StringBuilder();

            // Formulate the SELECT clause for the artifacts query
            select.append(
                    "SELECT * FROM groups g ");

            where.append("WHERE g.tenantId = ?");
            binders.add((query, idx) -> {
                query.bind(idx, tenantContext.tenantId());
            });
            // Formulate the WHERE clause for both queries

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
                        where.append("("
                                + "g.groupId LIKE ? OR "
                                + "g.description LIKE ? "
                                + ")");
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        break;
                    case group:
                        where.append("(g.groupId = ?)");
                        binders.add((query, idx) -> {
                            query.bind(idx, normalizeGroupId(filter.getStringValue()));
                        });
                        break;
                    default :
                        break;
                }
                where.append(")");
            }

            // Add order by to artifact query
            switch (orderBy) {
                case name:
                    orderByQuery.append(" ORDER BY g.groupId ");
                    break;
                case createdOn:
                    orderByQuery.append(" ORDER BY g." + orderBy.name() + " ");
                    break;
                default:
                    break;
            }
            orderByQuery.append(orderDirection.name());

            // Add limit and offset to artifact query
            limitOffset.append(" LIMIT ? OFFSET ?");

            // Query for the artifacts
            String groupsQuerySql = select + where.toString() + orderByQuery + limitOffset.toString();
            Query groupsQuery = handle.createQuery(groupsQuerySql);
            // Query for the total row count
            String countSelect = "SELECT count(g.groupId) "
                    + "FROM groups g ";

            Query countQuery = handle.createQuery(countSelect + where.toString());

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

    private void resolveReferences(Map<String, ContentHandle> resolvedReferences, List<ArtifactReferenceDto> references) {
        if (references != null && !references.isEmpty()) {
            for (ArtifactReferenceDto reference : references) {
                if (reference.getArtifactId() == null || reference.getName() == null || reference.getVersion() == null) {
                    throw new IllegalStateException("Invalid reference: " + reference);
                } else {
                    if (!resolvedReferences.containsKey(reference.getName())) {
                        //TODO improve exception handling
                        final ArtifactVersionMetaDataDto referencedArtifactMetaData = this.lookupForReference(reference);
                        final ContentWrapperDto referencedContent = getArtifactByContentId(referencedArtifactMetaData.getContentId());
                        resolveReferences(resolvedReferences, referencedContent.getReferences());
                        resolvedReferences.put(reference.getName(), referencedContent.getContent());
                    }
                }
            }
        }
    }

    private ArtifactVersionMetaDataDto lookupForReference(ArtifactReferenceDto reference) {
        return getArtifactVersionMetaDataInternal(reference.getGroupId(), reference.getArtifactId(), reference.getVersion());
    }

    protected void deleteAllOrphanedContent() {
        log.debug("Deleting all orphaned content");
        handles.withHandleNoException( handle -> {

            // Delete orphaned references
            String sql = sqlStatements.deleteOrphanedReferences();
            handle.createUpdate(sql)
                    .execute();

            // Delete orphaned content
            sql = sqlStatements.deleteAllOrphanedContent();
            handle.createUpdate(sql)
                .execute();

            return null;
        });
    }

    protected void resetGlobalId(Handle handle) {
        resetSequence(handle, GLOBAL_ID_SEQUENCE, sqlStatements.selectMaxGlobalId());
    }

    protected void resetContentId(Handle handle) {
        resetSequence(handle, CONTENT_ID_SEQUENCE, sqlStatements.selectMaxContentId());
    }

    private void resetSequence(Handle handle, String sequenceName, String sqlMaxIdFromTable) {
        Optional<Long> maxIdTable = handle.createQuery(sqlMaxIdFromTable)
                .bind(0, tenantContext.tenantId())
                .mapTo(Long.class)
                .findOne();

        Optional<Long> currentIdSeq = handle.createQuery(sqlStatements.selectCurrentSequenceValue())
                .bind(0, sequenceName)
                .bind(1, tenantContext.tenantId())
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
                    .bind(0, tenantContext.tenantId())
                    .bind(1, sequenceName)
                    .bind(2, id)
                    .bind(3, id)
                    .execute();
            } else {
                handle.createUpdate(sqlStatements.resetSequenceValue())
                    .bind(0, tenantContext.tenantId())
                    .bind(1, sequenceName)
                    .bind(2, id)
                    .execute();
            }

            log.info("Successfully reset {} to {}", sequenceName, id);
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
                log.info("Artifact rule imported successfully.");
            } catch (Exception e) {
                log.warn("Failed to import content entity (likely it already exists).", e);
            }
        } else {
            log.warn("Artifact rule import failed: artifact not found.");
        }
    }
    protected void importArtifactVersion(Handle handle, ArtifactVersionEntity entity) {
        if (!isArtifactExists(entity.groupId, entity.artifactId)) {
            try {
                String sql = sqlStatements.insertArtifact();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, normalizeGroupId(entity.groupId))
                    .bind(2, entity.artifactId)
                    .bind(3, entity.artifactType)
                    .bind(4, entity.createdBy)
                    .bind(5, new Date(entity.createdOn))
                    .execute();
                log.info("Artifact entity imported successfully.");
            } catch (Exception e) {
                log.warn("Failed to import artifact entity.", e);
            }

        }

        if (entity.globalId == -1 || !isGlobalIdExists(entity.globalId)) {
            long globalId = nextGlobalIdIfInvalid(handle, entity.globalId);
            try {
                String sql = sqlStatements.importArtifactVersion();
                handle.createUpdate(sql)
                    .bind(0, globalId)
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
                    .bind(11, SqlUtil.serializeLabels(entity.labels))
                    .bind(12, SqlUtil.serializeProperties(entity.properties))
                    .bind(13, entity.contentId)
                    .execute();
                log.info("Artifact version entity imported successfully.");

                // Insert labels into the "labels" table
                if (entity.labels != null && !entity.labels.isEmpty()) {
                    entity.labels.forEach(label -> {
                        String sqli = sqlStatements.insertLabel();
                        handle.createUpdate(sqli)
                                .bind(0, tenantContext.tenantId())
                                .bind(1, entity.globalId)
                                .bind(2, label.toLowerCase())
                                .execute();
                    });
                }

                // Insert properties into the "properties" table
                if (entity.properties != null && !entity.properties.isEmpty()) {
                    entity.properties.forEach((k,v) -> {
                        String sqli = sqlStatements.insertProperty();
                        handle.createUpdate(sqli)
                                .bind(0, tenantContext.tenantId())
                                .bind(1, entity.globalId)
                                .bind(2, k.toLowerCase())
                                .bind(3, v.toLowerCase())
                                .execute();
                    });
                }

                if (entity.isLatest) {
                    // Update the "latest" column in the artifacts table with the globalId of the new version
                    sql = sqlStatements.updateArtifactLatest();
                    handle.createUpdate(sql)
                          .bind(0, globalId)
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
            //TODO generate a new globalId and add the artifact, maybe only depending on a feature flag
        }
    }
    protected void importContent(Handle handle, ContentEntity entity) {
        try {
            List<ArtifactReferenceDto> references = SqlUtil.deserializeReferences(entity.serializedReferences);

            if (!isContentExists(entity.contentId)) {
                String sql = sqlStatements.importContent();
                handle.createUpdate(sql)
                    .bind(0, tenantContext.tenantId())
                    .bind(1, entity.contentId)
                    .bind(2, entity.canonicalHash)
                    .bind(3, entity.contentHash)
                    .bind(4, entity.contentBytes)
                    .bind(5, entity.serializedReferences)
                    .execute();

                insertReferences(handle, entity.contentId, references);

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
            log.warn("Failed to import content entity (likely it already exists).", e);
        }
    }
    protected void importGroup(Handle handle, GroupEntity entity) {
        try {
            String sql = sqlStatements.importGroup();
            handle.createUpdate(sql)
                .bind(0, tenantContext().tenantId())
                .bind(1, SqlUtil.normalizeGroupId(entity.groupId))
                .bind(2, entity.description)
                .bind(3, entity.artifactsType)
                .bind(4, entity.createdBy)
                .bind(5, new Date(entity.createdOn))
                .bind(6, entity.modifiedBy)
                .bind(7, new Date(entity.modifiedOn))
                .bind(8, SqlUtil.serializeProperties(entity.properties))
                .execute();
            log.info("Group entity imported successfully.");
        } catch (Exception e) {
            log.warn("Failed to import group entity (likely it already exists).", e);
        }
    }

    public boolean isContentExists(long contentId) throws RegistryStorageException {
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements().selectContentExists();
            return handle.createQuery(sql)
                    .bind(0, contentId)
                    .bind(1, tenantContext.tenantId())
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }

    protected boolean isGlobalIdExists(long globalId) throws RegistryStorageException {
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements().selectGlobalIdExists();
            return handle.createQuery(sql)
                    .bind(0, globalId)
                    .bind(1, tenantContext.tenantId())
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
    protected ContentHandle canonicalizeContent(String artifactType, ContentHandle content, List<ArtifactReferenceDto> references) {
        try {
            ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
            ContentCanonicalizer canonicalizer = provider.getContentCanonicalizer();
            ContentHandle canonicalContent = canonicalizer.canonicalize(content, resolveReferences(references));
            return canonicalContent;
        } catch (Exception e) {
            log.debug("Failed to canonicalize content of type: {}", artifactType);
            return content;
        }
    }

    protected EditableArtifactMetaDataDto extractMetaData(String artifactType, ContentHandle content) {
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

    protected long nextContentId(Handle handle) {
        return nextSequenceValue(handle, CONTENT_ID_SEQUENCE);
    }

    protected long nextGlobalId(Handle handle) {
        return nextSequenceValue(handle, GLOBAL_ID_SEQUENCE);
    }

    private long nextSequenceValue(Handle handle, String sequenceName) {
        if ("postgresql".equals(sqlStatements.dbType())) {
            return handle.createQuery(sqlStatements.getNextSequenceValue())
                    .bind(0, tenantContext.tenantId())
                    .bind(1, sequenceName)
                    .mapTo(Long.class)
                    .one();
        } else {
            // no way to automatically increment the sequence in h2 with just one query
            // we are incresing the sequence value in a way that it's not safe for concurrent executions
            // for kafkasql storage this method is not supposed to be executed concurrently
            // but for inmemory storage that's not guaranteed
            // that forces us to use an inmemory lock, should not cause any harm
            // caveat emptor , consider yourself as warned
            synchronized (inmemorySequencesMutex) {
                Optional<Long> seqExists = handle.createQuery(sqlStatements.selectCurrentSequenceValue())
                        .bind(0, sequenceName)
                        .bind(1, tenantContext.tenantId())
                        .mapTo(Long.class)
                        .findOne();

                if (seqExists.isPresent()) {
                    //
                    Long newValue = seqExists.get() + 1;
                    handle.createUpdate(sqlStatements.resetSequenceValue())
                        .bind(0, tenantContext.tenantId())
                        .bind(1, sequenceName)
                        .bind(2, newValue)
                        .execute();
                    return newValue;
                } else {
                    handle.createUpdate(sqlStatements.insertSequenceValue())
                        .bind(0, tenantContext.tenantId())
                        .bind(1, sequenceName)
                        .bind(2, 1)
                        .execute();
                    return 1;
                }
            }
        }
    }

    private static String limitStr(String value, int limit) {
        return limitStr(value, limit, false);
    }

    private static String limitStr(String value, int limit, boolean withEllipsis) {
        if (StringUtil.isEmpty(value)) {
            return value;
        }

        if (value.length() > limit) {
            if (withEllipsis) {
                return value.substring(0, limit - 3).concat("...");
            } else {
                return value.substring(0, limit);
            }
        } else {
            return value;
        }
    }

    private long nextGlobalIdIfInvalid(Handle handle, long globalId) {
        if (globalId == -1) {
            return nextGlobalId(handle);
        }
        return globalId;
    }


}