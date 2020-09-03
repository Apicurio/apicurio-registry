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
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.CompletionStage;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.result.ResultIterable;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.agroal.api.AgroalDataSource;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.rest.beans.VersionSearchResults;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
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
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;

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
    ISqlStatements sqlStatements;

    @ConfigProperty(name = "registry.sql.init", defaultValue = "true")
    boolean initDB;

    private Jdbi jdbi;

    /**
     * Constructor.
     */
    public SqlRegistryStorage() {
    }
    
    @PostConstruct
    protected void initialize() {
        log.info("SqlRegistryStorage constructed successfully.");
        
        jdbi = Jdbi.create(dataSource);

        if (initDB) {
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
        log.info("---");
        
        this.jdbi.withHandle( handle -> {
            statements.forEach( statement -> {
                log.info(statement);
                handle.createUpdate(statement).execute();
            });
            return null;
        });
        log.info("---");
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
        log.info("---");
        this.jdbi.withHandle( handle -> {
            statements.forEach( statement -> {
                log.info(statement);
                
                if (statement.startsWith("UPGRADER:")) {
                    String cname = statement.substring(9).trim();
                    applyUpgrader(handle, cname);
                } else {
                    handle.createUpdate(statement).execute();
                }
            });
            return null;
        });
        log.info("---");
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
    @Override
    public void updateArtifactState(String artifactId, ArtifactState state) {
        log.info("TBD - Please implement me!");
        
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactState(java.lang.String, io.apicurio.registry.types.ArtifactState, java.lang.Integer)
     */
    @Override
    public void updateArtifactState(String artifactId, ArtifactState state, Integer version) {
        log.info("TBD - Please implement me!");
        
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifact(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String artifactId, ArtifactType artifactType,
            ContentHandle content) throws ArtifactAlreadyExistsException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactWithMetadata(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String artifactId,
            ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifact(java.lang.String)
     */
    @Override
    public SortedSet<Long> deleteArtifact(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifact(java.lang.String)
     */
    @Override
    public StoredArtifact getArtifact(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifact(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String artifactId, ArtifactType artifactType,
            ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactWithMetadata(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String artifactId,
            ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactIds(java.lang.Integer)
     */
    @Override
    public Set<String> getArtifactIds(Integer limit) {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#searchArtifacts(java.lang.String, int, int, io.apicurio.registry.rest.beans.SearchOver, io.apicurio.registry.rest.beans.SortOrder)
     */
    @Override
    public ArtifactSearchResults searchArtifacts(String search, int offset, int limit, SearchOver searchOver,
            SortOrder sortOrder) {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(java.lang.String)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(java.lang.String, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId, ContentHandle content)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(long)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(long id)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactMetaData(java.lang.String, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactMetaData(String artifactId, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRules(java.lang.String)
     */
    @Override
    public List<RuleType> getArtifactRules(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactRuleAsync(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public CompletionStage<Void> createArtifactRuleAsync(String artifactId, RuleType rule,
            RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRules(java.lang.String)
     */
    @Override
    public void deleteArtifactRules(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public RuleConfigurationDto getArtifactRule(String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void updateArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteArtifactRule(String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersions(java.lang.String)
     */
    @Override
    public SortedSet<Long> getArtifactVersions(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#searchVersions(java.lang.String, int, int)
     */
    @Override
    public VersionSearchResults searchVersions(String artifactId, int offset, int limit) {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(long)
     */
    @Override
    public StoredArtifact getArtifactVersion(long id)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(java.lang.String, long)
     */
    @Override
    public StoredArtifact getArtifactVersion(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersion(java.lang.String, long)
     */
    @Override
    public void deleteArtifactVersion(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, long)
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactVersionMetaData(java.lang.String, long, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactVersionMetaData(String artifactId, long version,
            EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersionMetaData(java.lang.String, long)
     */
    @Override
    public void deleteArtifactVersionMetaData(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.info("TBD - Please implement me!");
        
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRules()
     */
    @Override
    @Transactional
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        log.info("Getting a list of all Global Rules");
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
    @Override
    @Transactional
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        log.info("Inserting a global rule row for: {}", rule.name());
        try {
            this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.insertGlobalRule();
                log.info("SQL is: " + sql);
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
    @Override
    @Transactional
    public void deleteGlobalRules() throws RegistryStorageException {
        log.info("Deleting all Global Rules");
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
    @Override
    @Transactional
    public RuleConfigurationDto getGlobalRule(RuleType rule)
            throws RuleNotFoundException, RegistryStorageException {
        log.info("Selecting a single global rule: {}", rule.name());
        try {
            return this.jdbi.withHandle( handle -> {
                String sql = sqlStatements.selectGlobalRuleByType();
                log.info("SQL is: " + sql);
                return handle.createQuery(sql)
                        .bind(0, rule.name())
                        .map(new RowMapper<RuleConfigurationDto>() {
                            @Override
                            public RuleConfigurationDto map(ResultSet rs, StatementContext ctx)
                                    throws SQLException {
                                RuleConfigurationDto dto = new RuleConfigurationDto();
                                dto.setConfiguration(rs.getString("configuration"));
                                return dto;
                            }
                        })
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
    @Override
    @Transactional
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleNotFoundException, RegistryStorageException {
        log.info("Updating a global rule: {}::{}", rule.name(), config.getConfiguration());
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
    @Override
    @Transactional
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        log.info("Deleting a global rule: {}", rule.name());
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

}
