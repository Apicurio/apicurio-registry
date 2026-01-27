package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.RuleAlreadyExistsException;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlOutboxEvent;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.storage.impl.sql.mappers.RuleConfigurationDtoMapper;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.events.ArtifactRuleConfigured;
import io.apicurio.registry.events.GlobalRuleConfigured;
import io.apicurio.registry.events.GroupRuleConfigured;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;

/**
 * Repository handling rule operations in the SQL storage layer.
 * Includes artifact rules, group rules, and global rules.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 */
@ApplicationScoped
public class SqlRuleRepository {

    @Inject
    Logger log;

    @Inject
    SqlStatements sqlStatements;

    @Inject
    HandleFactory handles;

    /**
     * Set the HandleFactory to use for database operations.
     * This allows storage implementations to override the default injected HandleFactory.
     */
    public void setHandleFactory(HandleFactory handleFactory) {
        this.handles = handleFactory;
    }

    @Inject
    Event<SqlOutboxEvent> outboxEvent;

    @Inject
    SqlArtifactRepository artifactRepository;

    @Inject
    SqlGroupRepository groupRepository;

    // ==================== ARTIFACT RULES ====================

    /**
     * Get artifact rules.
     */
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
                if (!artifactRepository.isArtifactExistsRaw(handle, groupId, artifactId)) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
            }
            return rules;
        });
    }

    /**
     * Create an artifact rule.
     */
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

    /**
     * Get an artifact rule.
     */
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact rule for artifact: {} {} and rule: {}", groupId, artifactId,
                rule.name());
        return handles.withHandle(handle -> {
            Optional<RuleConfigurationDto> res = handle.createQuery(sqlStatements.selectArtifactRuleByType())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, rule.name())
                    .map(RuleConfigurationDtoMapper.instance).findOne();
            return res.orElseThrow(() -> {
                if (!artifactRepository.isArtifactExistsRaw(handle, groupId, artifactId)) {
                    return new ArtifactNotFoundException(groupId, artifactId);
                }
                return new RuleNotFoundException(rule);
            });
        });
    }

    /**
     * Update an artifact rule.
     */
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
                if (!artifactRepository.isArtifactExistsRaw(handle, groupId, artifactId)) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
                throw new RuleNotFoundException(rule);
            }

            outboxEvent.fire(SqlOutboxEvent.of(ArtifactRuleConfigured.of(groupId, artifactId, rule, config)));

            return null;
        });
    }

    /**
     * Delete an artifact rule.
     */
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        log.debug("Deleting an artifact rule for artifact: {} {} and rule: {}", groupId, artifactId,
                rule.name());
        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.deleteArtifactRule())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, rule.name()).execute();
            if (rowCount == 0) {
                if (!artifactRepository.isArtifactExistsRaw(handle, groupId, artifactId)) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
                throw new RuleNotFoundException(rule);
            }

            fireRuleDeletedEvent(rule, groupId, artifactId);

            return null;
        });
    }

    /**
     * Delete all artifact rules.
     */
    public void deleteArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Deleting all artifact rules for artifact: {} {}", groupId, artifactId);
        handles.withHandle(handle -> {
            int count = handle.createUpdate(sqlStatements.deleteArtifactRules())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).execute();
            if (count == 0) {
                if (!artifactRepository.isArtifactExistsRaw(handle, groupId, artifactId)) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
            }
            return null;
        });
    }

    /**
     * Check if artifact rule exists.
     */
    public boolean isArtifactRuleExists(String groupId, String artifactId, RuleType rule)
            throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectArtifactRuleCountByType())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, rule.name())
                    .mapTo(Integer.class).one() > 0;
        });
    }

    // ==================== GROUP RULES ====================

    /**
     * Get group rules.
     */
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
                if (!groupRepository.isGroupExistsRaw(handle, groupId)) {
                    throw new GroupNotFoundException(groupId);
                }
            }
            return rules;
        });
    }

    /**
     * Create a group rule.
     */
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

    /**
     * Get a group rule.
     */
    public RuleConfigurationDto getGroupRule(String groupId, RuleType rule) throws RegistryStorageException {
        log.debug("Selecting a single group rule for group: {} and rule: {}", groupId, rule.name());
        return handles.withHandle(handle -> {
            Optional<RuleConfigurationDto> res = handle.createQuery(sqlStatements.selectGroupRuleByType())
                    .bind(0, normalizeGroupId(groupId)).bind(1, rule.name())
                    .map(RuleConfigurationDtoMapper.instance).findOne();
            return res.orElseThrow(() -> {
                if (!groupRepository.isGroupExistsRaw(handle, groupId)) {
                    return new GroupNotFoundException(groupId);
                }
                return new RuleNotFoundException(rule);
            });
        });
    }

    /**
     * Update a group rule.
     */
    public void updateGroupRule(String groupId, RuleType rule, RuleConfigurationDto config)
            throws RegistryStorageException {
        log.debug("Updating a group rule for group: {} and rule: {}::{}", groupId, rule.name(),
                config.getConfiguration());
        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.updateGroupRule())
                    .bind(0, config.getConfiguration()).bind(1, normalizeGroupId(groupId))
                    .bind(2, rule.name()).execute();
            if (rowCount == 0) {
                if (!groupRepository.isGroupExistsRaw(handle, groupId)) {
                    throw new GroupNotFoundException(groupId);
                }
                throw new RuleNotFoundException(rule);
            }

            outboxEvent.fire(SqlOutboxEvent.of(GroupRuleConfigured.of(groupId, rule, config)));

            return null;
        });
    }

    /**
     * Delete a group rule.
     */
    public void deleteGroupRule(String groupId, RuleType rule) throws RegistryStorageException {
        log.debug("Deleting an group rule for group: {} and rule: {}", groupId, rule.name());
        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.deleteGroupRule())
                    .bind(0, normalizeGroupId(groupId)).bind(1, rule.name()).execute();
            if (rowCount == 0) {
                if (!groupRepository.isGroupExistsRaw(handle, groupId)) {
                    throw new GroupNotFoundException(groupId);
                }
                throw new RuleNotFoundException(rule);
            }

            fireGroupRuleDeletedEvent(rule, groupId);

            return null;
        });
    }

    /**
     * Delete all group rules.
     */
    public void deleteGroupRules(String groupId) throws RegistryStorageException {
        log.debug("Deleting all group rules for group: {}", groupId);
        handles.withHandle(handle -> {
            int count = handle.createUpdate(sqlStatements.deleteGroupRules())
                    .bind(0, normalizeGroupId(groupId)).execute();
            if (count == 0) {
                if (!groupRepository.isGroupExistsRaw(handle, groupId)) {
                    throw new GroupNotFoundException(groupId);
                }
            }
            return null;
        });
    }

    // ==================== GLOBAL RULES ====================

    /**
     * Get global rules.
     */
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectGlobalRules())
                    .map(rs -> RuleType.fromValue(rs.getString("type"))).list();
        });
    }

    /**
     * Create a global rule.
     */
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

    /**
     * Get a global rule.
     */
    public RuleConfigurationDto getGlobalRule(RuleType rule)
            throws RuleNotFoundException, RegistryStorageException {
        log.debug("Selecting a single global rule: {}", rule.name());
        return handles.withHandle(handle -> {
            Optional<RuleConfigurationDto> res = handle.createQuery(sqlStatements.selectGlobalRuleByType())
                    .bind(0, rule.name()).map(RuleConfigurationDtoMapper.instance).findOne();
            return res.orElseThrow(() -> new RuleNotFoundException(rule));
        });
    }

    /**
     * Update a global rule.
     */
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

    /**
     * Delete a global rule.
     */
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        log.debug("Deleting a global rule: {}", rule.name());
        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.deleteGlobalRule()).bind(0, rule.name())
                    .execute();
            if (rowCount == 0) {
                throw new RuleNotFoundException(rule);
            }

            fireGlobalRuleDeletedEvent(rule);

            return null;
        });
    }

    /**
     * Delete all global rules.
     */
    public void deleteGlobalRules() throws RegistryStorageException {
        log.debug("Deleting all Global Rules");
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.deleteGlobalRules()).execute();
            return null;
        });
    }

    /**
     * Check if global rule exists.
     */
    public boolean isGlobalRuleExists(RuleType rule) throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectGlobalRuleCountByType()).bind(0, rule.name())
                    .mapTo(Integer.class).one() > 0;
        });
    }

    // ==================== HELPER METHODS ====================

    private void fireRuleDeletedEvent(RuleType rule, String groupId, String artifactId) {
        switch (rule) {
            case VALIDITY -> outboxEvent.fire(SqlOutboxEvent.of(ArtifactRuleConfigured.of(groupId,
                    artifactId, rule,
                    RuleConfigurationDto.builder().configuration(ValidityLevel.NONE.name()).build())));
            case COMPATIBILITY -> outboxEvent.fire(SqlOutboxEvent.of(ArtifactRuleConfigured.of(groupId,
                    artifactId, rule,
                    RuleConfigurationDto.builder().configuration(CompatibilityLevel.NONE.name()).build())));
            case INTEGRITY -> outboxEvent.fire(SqlOutboxEvent.of(ArtifactRuleConfigured.of(groupId,
                    artifactId, rule,
                    RuleConfigurationDto.builder().configuration(IntegrityLevel.NONE.name()).build())));
        }
    }

    private void fireGroupRuleDeletedEvent(RuleType rule, String groupId) {
        switch (rule) {
            case VALIDITY -> outboxEvent.fire(SqlOutboxEvent.of(GroupRuleConfigured.of(groupId, rule,
                    RuleConfigurationDto.builder().configuration(ValidityLevel.NONE.name()).build())));
            case COMPATIBILITY -> outboxEvent.fire(SqlOutboxEvent.of(GroupRuleConfigured.of(groupId, rule,
                    RuleConfigurationDto.builder().configuration(CompatibilityLevel.NONE.name()).build())));
            case INTEGRITY -> outboxEvent.fire(SqlOutboxEvent.of(GroupRuleConfigured.of(groupId, rule,
                    RuleConfigurationDto.builder().configuration(IntegrityLevel.NONE.name()).build())));
        }
    }

    private void fireGlobalRuleDeletedEvent(RuleType rule) {
        switch (rule) {
            case VALIDITY -> outboxEvent.fire(SqlOutboxEvent.of(GlobalRuleConfigured.of(rule,
                    RuleConfigurationDto.builder().configuration(ValidityLevel.NONE.name()).build())));
            case COMPATIBILITY -> outboxEvent.fire(SqlOutboxEvent.of(GlobalRuleConfigured.of(rule,
                    RuleConfigurationDto.builder().configuration(CompatibilityLevel.NONE.name()).build())));
            case INTEGRITY -> outboxEvent.fire(SqlOutboxEvent.of(GlobalRuleConfigured.of(rule,
                    RuleConfigurationDto.builder().configuration(IntegrityLevel.NONE.name()).build())));
        }
    }

    // ==================== IMPORT OPERATIONS ====================

    /**
     * Import a group rule entity (used for data import/migration).
     */
    public void importGroupRule(GroupRuleEntity entity) {
        handles.withHandleNoException(handle -> {
            if (groupRepository.isGroupExistsRaw(handle, entity.groupId)) {
                handle.createUpdate(sqlStatements.importGroupRule())
                        .bind(0, normalizeGroupId(entity.groupId))
                        .bind(1, entity.type.name())
                        .bind(2, entity.configuration)
                        .execute();
            } else {
                throw new GroupNotFoundException(entity.groupId);
            }
            return null;
        });
    }

    /**
     * Import an artifact rule entity (used for data import/migration).
     */
    public void importArtifactRule(ArtifactRuleEntity entity) {
        handles.withHandleNoException(handle -> {
            if (artifactRepository.isArtifactExistsRaw(handle, entity.groupId, entity.artifactId)) {
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

    /**
     * Import a global rule entity (used for data import/migration).
     */
    public void importGlobalRule(GlobalRuleEntity entity) {
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.importGlobalRule())
                    .bind(0, entity.ruleType.name())
                    .bind(1, entity.configuration)
                    .execute();
            return null;
        });
    }
}
