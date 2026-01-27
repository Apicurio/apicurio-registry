package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.SqlStatementVariableBinder;
import io.apicurio.registry.storage.impl.sql.jdb.Query;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedArtifactMapper;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedVersionMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;

/**
 * Repository handling search operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 */
@ApplicationScoped
public class SqlSearchRepository {

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
    RestConfig restConfig;

    /**
     * Search for artifacts based on filters.
     */
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit) {
        return handles.withHandleNoException(handle -> {
            List<SqlStatementVariableBinder> binders = new LinkedList<>();

            StringBuilder where = new StringBuilder();
            StringBuilder orderByQuery = new StringBuilder();

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
                        String nameValue = filter.getStringValue();
                        boolean startsWithWildcard = nameValue.startsWith("*");
                        boolean endsWithWildcard = nameValue.endsWith("*");

                        // Remove wildcards from the value
                        String searchValue = nameValue;
                        if (startsWithWildcard) {
                            searchValue = searchValue.substring(1);
                        }
                        if (endsWithWildcard) {
                            searchValue = searchValue.substring(0, searchValue.length() - 1);
                        }

                        // Determine operator based on wildcards
                        if (startsWithWildcard || endsWithWildcard) {
                            op = filter.isNot() ? "NOT LIKE" : "LIKE";
                            where.append("a.name " + op + " ? OR a.artifactId " + op + " ?");

                            // Add wildcards to SQL pattern based on user input
                            String finalSearchValue = searchValue;
                            binders.add((query, idx) -> {
                                String pattern = finalSearchValue;
                                if (startsWithWildcard) {
                                    pattern = "%" + pattern;
                                }
                                if (endsWithWildcard) {
                                    pattern = pattern + "%";
                                }
                                query.bind(idx, pattern);
                            });
                            binders.add((query, idx) -> {
                                String pattern = finalSearchValue;
                                if (startsWithWildcard) {
                                    pattern = "%" + pattern;
                                }
                                if (endsWithWildcard) {
                                    pattern = pattern + "%";
                                }
                                query.bind(idx, pattern);
                            });
                        } else {
                            // Exact match - no wildcards
                            op = filter.isNot() ? "!=" : "=";
                            where.append("(a.name " + op + " ? OR a.artifactId " + op + " ?)");
                            String finalSearchValue = searchValue;
                            binders.add((query, idx) -> {
                                query.bind(idx, finalSearchValue);
                            });
                            binders.add((query, idx) -> {
                                query.bind(idx, finalSearchValue);
                            });
                        }
                        break;
                    case groupId:
                        op = filter.isNot() ? "!=" : "=";
                        where.append("a.groupId " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, normalizeGroupId(filter.getStringValue()));
                        });
                        break;
                    case artifactId:
                        op = filter.isNot() ? "!=" : "=";
                        where.append("a.artifactId " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getStringValue());
                        });
                        break;
                    case artifactType:
                        op = filter.isNot() ? "!=" : "=";
                        where.append("a.type " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getStringValue());
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
                    default:
                        throw new RegistryStorageException("Filter type not supported: " + filter.getType());
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

            // Query for the artifacts
            String artifactsQuerySql = sqlStatements.selectTableTemplate("a.*", "artifacts", "a",
                    where.toString(), orderByQuery.toString());
            Query artifactsQuery = handle.createQuery(artifactsQuerySql);

            String countQuerySql = sqlStatements.selectCountTableTemplate("a.artifactId", "artifacts", "a",
                    where.toString());
            Query countQuery = handle.createQuery(countQuerySql);

            // Bind all query parameters
            int idx = 0;
            for (SqlStatementVariableBinder binder : binders) {
                binder.bind(artifactsQuery, idx);
                binder.bind(countQuery, idx);
                idx++;
            }
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

    /**
     * Search for versions based on filters.
     */
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
                    case artifactType:
                        op = filter.isNot() ? "!=" : "=";
                        where.append("a.type " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getStringValue());
                        });
                        break;
                    case artifactId:
                    case contentId:
                    case globalId:
                    case state:
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
                    default:
                        throw new RegistryStorageException("Filter type not supported: " + filter.getType());
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

    /**
     * Limit the size of labels returned in search results.
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
        return labels;
    }

    /**
     * Limit labels in artifact search results.
     */
    private void limitReturnedLabelsInArtifacts(List<SearchedArtifactDto> artifacts) {
        artifacts.forEach(artifact -> {
            Map<String, String> labels = artifact.getLabels();
            Map<String, String> cappedLabels = limitReturnedLabels(labels);
            artifact.setLabels(cappedLabels);
        });
    }

    /**
     * Limit labels in version search results.
     */
    public void limitReturnedLabelsInVersions(List<SearchedVersionDto> versions) {
        versions.forEach(version -> {
            Map<String, String> labels = version.getLabels();
            Map<String, String> cappedLabels = limitReturnedLabels(labels);
            version.setLabels(cappedLabels);
        });
    }
}
