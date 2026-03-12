package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.storage.dto.BranchMetaDataDto;
import io.apicurio.registry.storage.dto.BranchSearchResultsDto;
import io.apicurio.registry.storage.dto.EditableBranchMetaDataDto;
import io.apicurio.registry.storage.dto.SearchedBranchDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.BranchAlreadyExistsException;
import io.apicurio.registry.storage.error.BranchNotFoundException;
import io.apicurio.registry.storage.error.NotAllowedException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsOnBranchException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.SqlStatementVariableBinder;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.Query;
import io.apicurio.registry.storage.impl.sql.mappers.BranchMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GAVMapper;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedBranchMapper;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedVersionMapper;
import io.apicurio.registry.storage.impl.sql.mappers.StringMapper;
import io.apicurio.registry.exception.UnreachableCodeException;
import io.apicurio.registry.semver.SemVerConfigProperties;
import io.apicurio.registry.types.VersionState;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ValidationException;
import org.semver4j.Semver;
import org.slf4j.Logger;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Repository handling branch operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 */
@ApplicationScoped
public class SqlBranchRepository {

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
    SecurityIdentity securityIdentity;

    @Inject
    SqlArtifactRepository artifactRepository;

    @Inject
    SemVerConfigProperties semVerConfigProps;

    @Inject
    RestConfig restConfig;

    /**
     * Create a new branch.
     */
    public BranchMetaDataDto createBranch(GA ga, BranchId branchId, String description,
            List<String> versions) {
        try {
            String user = securityIdentity.getPrincipal().getName();
            Date now = new Date();

            handles.withHandle(handle -> {
                handle.createUpdate(sqlStatements.insertBranch()).bind(0, ga.getRawGroupId())
                        .bind(1, ga.getRawArtifactId()).bind(2, branchId.getRawBranchId())
                        .bind(3, description).bind(4, false).bind(5, user).bind(6, now).bind(7, user)
                        .bind(8, now).execute();

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

    /**
     * Update branch metadata.
     */
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

    /**
     * Get branch metadata.
     */
    public BranchMetaDataDto getBranchMetaData(GA ga, BranchId branchId) {
        return handles.withHandle(handle -> {
            Optional<BranchMetaDataDto> res = handle.createQuery(sqlStatements.selectBranch())
                    .bind(0, ga.getRawGroupId()).bind(1, ga.getRawArtifactId())
                    .bind(2, branchId.getRawBranchId()).map(BranchMetaDataDtoMapper.instance).findOne();
            return res.orElseThrow(() -> new BranchNotFoundException(ga.getRawGroupIdWithDefaultString(),
                    ga.getRawArtifactId(), branchId.getRawBranchId()));
        });
    }

    /**
     * Get branches for an artifact.
     */
    public BranchSearchResultsDto getBranches(GA ga, int offset, int limit) {
        return handles.withHandleNoException(handle -> {
            List<SqlStatementVariableBinder> binders = new LinkedList<>();

            StringBuilder where = new StringBuilder();
            StringBuilder orderByQuery = new StringBuilder();

            where.append(" WHERE b.groupId = ? AND b.artifactId = ?");
            binders.add((query, idx) -> query.bind(idx, ga.getRawGroupId()));
            binders.add((query, idx) -> query.bind(idx, ga.getRawArtifactId()));

            orderByQuery.append(" ORDER BY b.branchId ASC");

            String branchesQuerySql = sqlStatements.selectTableTemplate("*", "branches", "b",
                    where.toString(), orderByQuery.toString());
            Query branchesQuery = handle.createQuery(branchesQuerySql);

            String countQuerySql = sqlStatements.selectCountTableTemplate("b.branchId", "branches", "b",
                    where.toString());
            Query countQuery = handle.createQuery(countQuerySql);

            int idx = 0;
            for (SqlStatementVariableBinder binder : binders) {
                binder.bind(branchesQuery, idx);
                binder.bind(countQuery, idx);
                idx++;
            }

            if ("mssql".equals(sqlStatements.dbType())) {
                branchesQuery.bind(idx++, offset);
                branchesQuery.bind(idx++, limit);
            } else {
                branchesQuery.bind(idx++, limit);
                branchesQuery.bind(idx++, offset);
            }

            List<SearchedBranchDto> branches = branchesQuery.map(SearchedBranchMapper.instance).list();
            Integer count = countQuery.mapTo(Integer.class).one();

            BranchSearchResultsDto results = new BranchSearchResultsDto();
            results.setBranches(branches);
            results.setCount(count);
            return results;
        });
    }

    /**
     * Delete a branch.
     */
    public void deleteBranch(GA ga, BranchId branchId) {
        BranchMetaDataDto bmd = getBranchMetaData(ga, branchId);
        if (bmd.isSystemDefined()) {
            throw new NotAllowedException("System generated branches cannot be deleted.");
        }

        handles.withHandleNoException(handle -> {
            if ("mssql".equals(sqlStatements.dbType())) {
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

    /**
     * Append a version to a branch.
     */
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

    /**
     * Append a version to a branch using an existing handle.
     */
    public void appendVersionToBranchRaw(Handle handle, GA ga, BranchId branchId, VersionId version) {
        try {
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

    /**
     * Replace all versions on a branch.
     */
    public void replaceBranchVersions(GA ga, BranchId branchId, List<VersionId> versions) {
        BranchMetaDataDto bmd = getBranchMetaData(ga, branchId);
        if (bmd.isSystemDefined()) {
            throw new NotAllowedException("System generated branches cannot be modified.");
        }

        handles.withHandle(handle -> {
            handle.createUpdate(sqlStatements.deleteBranchVersions()).bind(0, ga.getRawGroupId())
                    .bind(1, ga.getRawArtifactId()).bind(2, branchId.getRawBranchId()).execute();

            int branchOrder = 0;
            for (VersionId version : versions) {
                handle.createUpdate(sqlStatements.insertBranchVersion()).bind(0, ga.getRawGroupId())
                        .bind(1, ga.getRawArtifactId()).bind(2, branchId.getRawBranchId())
                        .bind(3, branchOrder++).bind(4, version.getRawVersionId()).execute();
            }

            updateBranchModifiedTimeRaw(handle, ga, branchId);

            return null;
        });
    }

    /**
     * Get the tip of a branch.
     */
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

    /**
     * Get versions for a branch.
     */
    public VersionSearchResultsDto getBranchVersions(GA ga, BranchId branchId, int offset, int limit) {
        return handles.withHandleNoException(handle -> {
            List<SqlStatementVariableBinder> binders = new LinkedList<>();

            StringBuilder selectTemplate = new StringBuilder();
            StringBuilder where = new StringBuilder();
            StringBuilder orderByQuery = new StringBuilder();
            StringBuilder limitOffset = new StringBuilder();

            selectTemplate.append("SELECT {{selectColumns}} FROM branch_versions bv "
                    + "JOIN versions v ON bv.groupId = v.groupId AND bv.artifactId = v.artifactId AND bv.version = v.version "
                    + "JOIN artifacts a ON a.groupId = v.groupId AND a.artifactId = v.artifactId ");

            where.append(" WHERE bv.groupId = ? AND bv.artifactId = ? AND bv.branchId = ?");
            binders.add((query, idx) -> query.bind(idx, ga.getRawGroupId()));
            binders.add((query, idx) -> query.bind(idx, ga.getRawArtifactId()));
            binders.add((query, idx) -> query.bind(idx, branchId.getRawBranchId()));

            orderByQuery.append(" ORDER BY bv.branchOrder DESC");

            if ("mssql".equals(sqlStatements.dbType())) {
                limitOffset.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            } else {
                limitOffset.append(" LIMIT ? OFFSET ?");
            }

            String versionsQuerySql = new StringBuilder(selectTemplate).append(where).append(orderByQuery)
                    .append(limitOffset).toString().replace("{{selectColumns}}", "v.*, a.type");
            Query versionsQuery = handle.createQuery(versionsQuerySql);

            String countQuerySql = new StringBuilder(selectTemplate).append(where).toString()
                    .replace("{{selectColumns}}", "count(v.globalId)");
            Query countQuery = handle.createQuery(countQuerySql);

            int idx = 0;
            for (SqlStatementVariableBinder binder : binders) {
                binder.bind(versionsQuery, idx);
                binder.bind(countQuery, idx);
                idx++;
            }

            if ("mssql".equals(sqlStatements.dbType())) {
                versionsQuery.bind(idx++, offset);
                versionsQuery.bind(idx, limit);
            } else {
                versionsQuery.bind(idx++, limit);
                versionsQuery.bind(idx, offset);
            }

            List<SearchedVersionDto> versions = versionsQuery.map(SearchedVersionMapper.instance).list();
            Integer count = countQuery.mapTo(Integer.class).one();

            VersionSearchResultsDto results = new VersionSearchResultsDto();
            results.setVersions(versions);
            results.setCount(count);
            limitReturnedLabelsInVersions(results.getVersions());
            return results;
        });
    }

    /**
     * Create or update a branch (for system-defined branches like LATEST, DRAFTS).
     */
    public void createOrUpdateBranchRaw(Handle handle, GAV gav, BranchId branchId, boolean systemDefined) {
        try {
            String user = securityIdentity.getPrincipal().getName();
            Date now = new Date();

            handle.createUpdate(sqlStatements.upsertBranch()).bind(0, gav.getRawGroupId())
                    .bind(1, gav.getRawArtifactId()).bind(2, branchId.getRawBranchId()).bind(3, (String) null)
                    .bind(4, systemDefined).bind(5, user).bind(6, now).bind(7, user).bind(8, now).execute();
        } catch (Exception ex) {
            if (!sqlStatements.isPrimaryKeyViolation(ex)) {
                throw ex;
            }
        }

        appendVersionToBranchRaw(handle, gav, branchId, gav.getVersionId());
    }

    /**
     * If SemVer support is enabled, create (or update) the automatic system generated semantic versioning
     * branches.
     */
    public void createOrUpdateSemverBranchesRaw(Handle handle, GAV gav) {
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
     * Remove a version from a branch.
     */
    public void removeVersionFromBranchRaw(Handle handle, GAV gav, BranchId branchId) {
        handle.createUpdate(sqlStatements.deleteVersionFromBranch()).bind(0, gav.getRawGroupIdWithNull())
                .bind(1, gav.getRawArtifactId()).bind(2, branchId.getRawBranchId())
                .bind(3, gav.getRawVersionId()).execute();
    }

    /**
     * Get branch version numbers.
     */
    public List<String> getBranchVersionNumbersRaw(Handle handle, GA ga, BranchId branchId) {
        return handle.createQuery(sqlStatements.selectBranchVersionNumbers()).bind(0, ga.getRawGroupId())
                .bind(1, ga.getRawArtifactId()).bind(2, branchId.getRawBranchId()).map(StringMapper.instance)
                .list();
    }

    /**
     * Update branch modified time.
     */
    public void updateBranchModifiedTimeRaw(Handle handle, GA ga, BranchId branchId) {
        String user = securityIdentity.getPrincipal().getName();
        Date now = new Date();

        handle.createUpdate(sqlStatements.updateBranchModifiedTime()).bind(0, user).bind(1, now)
                .bind(2, ga.getRawGroupId()).bind(3, ga.getRawArtifactId()).bind(4, branchId.getRawBranchId())
                .execute();
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

    private void limitReturnedLabelsInVersions(List<SearchedVersionDto> versions) {
        versions.forEach(version -> {
            Map<String, String> labels = version.getLabels();
            Map<String, String> cappedLabels = limitReturnedLabels(labels);
            version.setLabels(cappedLabels);
        });
    }


    // ==================== IMPORT OPERATIONS ====================

    /**
     * Import a branch entity (used for data import/migration).
     */
    public void importBranch(BranchEntity entity) {
        var ga = entity.toGA();
        var branchId = entity.toBranchId();

        handles.withHandleNoException(handle -> {
            if (!artifactRepository.isArtifactExistsRaw(handle, entity.groupId, entity.artifactId)) {
                throw new ArtifactNotFoundException(ga.getRawGroupIdWithDefaultString(), ga.getRawArtifactId());
            }

            handle.createUpdate(sqlStatements.importBranch())
                    .bind(0, ga.getRawGroupId())
                    .bind(1, ga.getRawArtifactId())
                    .bind(2, branchId.getRawBranchId())
                    .bind(3, entity.description)
                    .bind(4, entity.systemDefined)
                    .bind(5, entity.owner)
                    .bind(6, new Date(entity.createdOn))
                    .bind(7, entity.modifiedBy)
                    .bind(8, new Date(entity.modifiedOn))
                    .execute();

            // Append each of the versions onto the branch
            if (entity.versions != null) {
                entity.versions.forEach(version -> {
                    appendVersionToBranchRaw(handle, ga, branchId, new VersionId(version));
                });
            }

            return null;
        });
    }
}
