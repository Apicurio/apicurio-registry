package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import org.slf4j.Logger;

/**
 * Repository handling cleanup and bulk delete operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 */
public class SqlCleanupRepository {

    private final Logger log;
    private final SqlStatements sqlStatements;
    private final HandleFactory handles;
    private final SqlRuleRepository ruleRepository;

    public SqlCleanupRepository(HandleFactory handles, SqlStatements sqlStatements, Logger log,
            SqlRuleRepository ruleRepository) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
        this.log = log;
        this.ruleRepository = ruleRepository;
    }

    /**
     * Delete all user data from the registry.
     * This is a destructive operation that removes all artifacts, groups, rules, and content.
     */
    public void deleteAllUserData() {
        log.debug("Deleting all user data");

        ruleRepository.deleteGlobalRules();

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

            return null;
        });
    }
}
