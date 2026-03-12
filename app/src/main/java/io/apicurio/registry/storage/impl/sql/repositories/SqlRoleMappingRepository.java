package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.RoleMappingDto;
import io.apicurio.registry.storage.dto.RoleMappingSearchResultsDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.RoleMappingAlreadyExistsException;
import io.apicurio.registry.storage.error.RoleMappingNotFoundException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.mappers.RoleMappingDtoMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Repository handling role mapping operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 */
@ApplicationScoped
public class SqlRoleMappingRepository {

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

    /**
     * Create a new role mapping.
     */
    public void createRoleMapping(String principalId, String role, String principalName)
            throws RegistryStorageException {
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
        } catch (Exception ex) {
            if (sqlStatements.isPrimaryKeyViolation(ex)) {
                throw new RoleMappingAlreadyExistsException(principalId, role);
            }
            throw ex;
        }
    }

    /**
     * Delete a role mapping.
     */
    public void deleteRoleMapping(String principalId) throws RegistryStorageException {
        log.debug("Deleting a role mapping row for: {}", principalId);
        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.deleteRoleMapping())
                    .bind(0, principalId)
                    .execute();
            if (rowCount == 0) {
                throw new RoleMappingNotFoundException(principalId);
            }
            return null;
        });
    }

    /**
     * Get a role mapping by principal ID.
     */
    public RoleMappingDto getRoleMapping(String principalId) throws RegistryStorageException {
        log.debug("Selecting a single role mapping for: {}", principalId);
        return handles.withHandle(handle -> {
            Optional<RoleMappingDto> res = handle.createQuery(sqlStatements.selectRoleMappingByPrincipalId())
                    .bind(0, principalId)
                    .map(RoleMappingDtoMapper.instance)
                    .findOne();
            return res.orElseThrow(() -> new RoleMappingNotFoundException(principalId));
        });
    }

    /**
     * Get the role for a principal.
     */
    public String getRoleForPrincipal(String principalId) throws RegistryStorageException {
        log.debug("Selecting the role for: {}", principalId);
        return handles.withHandle(handle -> {
            Optional<String> res = handle.createQuery(sqlStatements.selectRoleByPrincipalId())
                    .bind(0, principalId)
                    .mapTo(String.class)
                    .findOne();
            return res.orElse(null);
        });
    }

    /**
     * Get all role mappings.
     */
    public List<RoleMappingDto> getRoleMappings() throws RegistryStorageException {
        log.debug("Getting a list of all role mappings.");
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectRoleMappings())
                    .map(RoleMappingDtoMapper.instance)
                    .list();
        });
    }

    /**
     * Search role mappings with pagination.
     */
    public RoleMappingSearchResultsDto searchRoleMappings(int offset, int limit)
            throws RegistryStorageException {
        log.debug("Searching role mappings.");
        return handles.withHandleNoException(handle -> {
            String query = sqlStatements.selectRoleMappings() + " LIMIT ? OFFSET ?";
            String countQuery = sqlStatements.countRoleMappings();
            List<RoleMappingDto> mappings = handle.createQuery(query)
                    .bind(0, limit)
                    .bind(1, offset)
                    .map(RoleMappingDtoMapper.instance)
                    .list();
            Integer count = handle.createQuery(countQuery)
                    .mapTo(Integer.class)
                    .one();
            return RoleMappingSearchResultsDto.builder()
                    .count(count)
                    .roleMappings(mappings)
                    .build();
        });
    }

    /**
     * Update a role mapping.
     */
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException {
        log.debug("Updating a role mapping: {}::{}", principalId, role);
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
    }

    /**
     * Check if a role mapping exists.
     */
    public boolean isRoleMappingExists(String principalId) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectRoleMappingCountByPrincipal())
                    .bind(0, principalId)
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }

    /**
     * Delete all role mappings.
     */
    public void deleteAllRoleMappings() {
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.deleteAllRoleMappings()).execute();
            return null;
        });
    }
}
