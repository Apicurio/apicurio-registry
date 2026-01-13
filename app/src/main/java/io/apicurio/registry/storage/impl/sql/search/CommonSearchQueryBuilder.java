package io.apicurio.registry.storage.impl.sql.search;

import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.impl.sql.SqlStatementVariableBinder;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;

/**
 * Common implementation of SearchQueryBuilder that uses JOIN-based label filtering.
 * <p>
 * This implementation uses INNER JOIN for label filters and GROUP BY to eliminate
 * duplicate rows. The GROUP BY approach works across all databases and avoids
 * the DISTINCT limitation with SQL Server TEXT columns.
 */
public class CommonSearchQueryBuilder implements SearchQueryBuilder {

    protected final SqlStatements sqlStatements;

    public CommonSearchQueryBuilder(SqlStatements sqlStatements) {
        this.sqlStatements = sqlStatements;
    }

    @Override
    public SearchQuery buildSearchQuery(EntityType entityType, Set<SearchFilter> filters,
            OrderBy orderBy, OrderDirection orderDirection) {

        List<SqlStatementVariableBinder> binders = new ArrayList<>();
        StringBuilder select = new StringBuilder();
        StringBuilder from = new StringBuilder();
        StringBuilder joins = new StringBuilder();
        StringBuilder where = new StringBuilder();
        StringBuilder groupBy = new StringBuilder();
        StringBuilder orderByClause = new StringBuilder();

        // Track label join count for unique aliases
        AtomicInteger labelJoinCounter = new AtomicInteger(0);
        boolean hasLabelJoin = false;

        switch (entityType) {
            case ARTIFACT -> {
                select.append("SELECT a.groupId, a.artifactId, a.type, a.owner, a.createdOn, ")
                      .append("a.modifiedBy, a.modifiedOn, a.name, a.description, a.labels, ")
                      .append("COUNT(*) OVER() AS total_count");
                from.append(" FROM ").append(getArtifactsTable()).append(" a");
                groupBy.append(" GROUP BY a.groupId, a.artifactId, a.type, a.owner, a.createdOn, ")
                       .append("a.modifiedBy, a.modifiedOn, a.name, a.description, a.labels");

                // Build WHERE clause
                boolean first = true;
                for (SearchFilter filter : filters) {
                    if (first) {
                        where.append(" WHERE (");
                        first = false;
                    } else {
                        where.append(" AND (");
                    }

                    hasLabelJoin = buildArtifactFilterClause(filter, where, joins, binders, labelJoinCounter) || hasLabelJoin;
                    where.append(")");
                }

                // Build ORDER BY clause
                buildArtifactOrderBy(orderBy, orderByClause);
            }
            case VERSION -> {
                select.append("SELECT v.globalId, v.groupId, v.artifactId, v.version, v.versionOrder, ")
                      .append("v.state, v.name, v.description, v.owner, v.createdOn, v.modifiedBy, ")
                      .append("v.modifiedOn, v.labels, v.contentId, a.type, ")
                      .append("COUNT(*) OVER() AS total_count");
                from.append(" FROM versions v JOIN ").append(getArtifactsTable())
                    .append(" a ON v.groupId = a.groupId AND v.artifactId = a.artifactId");
                groupBy.append(" GROUP BY v.globalId, v.groupId, v.artifactId, v.version, v.versionOrder, ")
                       .append("v.state, v.name, v.description, v.owner, v.createdOn, v.modifiedBy, ")
                       .append("v.modifiedOn, v.labels, v.contentId, a.type");

                // Build WHERE clause
                where.append(" WHERE (1 = 1)");
                for (SearchFilter filter : filters) {
                    where.append(" AND (");
                    hasLabelJoin = buildVersionFilterClause(filter, where, joins, binders, labelJoinCounter) || hasLabelJoin;
                    where.append(")");
                }

                // Build ORDER BY clause
                buildVersionOrderBy(orderBy, orderByClause);
            }
            case GROUP -> {
                select.append("SELECT g.groupId, g.description, g.artifactsType, g.owner, g.createdOn, ")
                      .append("g.modifiedBy, g.modifiedOn, g.labels, ")
                      .append("COUNT(*) OVER() AS total_count");
                from.append(" FROM ").append(getGroupsTable()).append(" g");
                groupBy.append(" GROUP BY g.groupId, g.description, g.artifactsType, g.owner, g.createdOn, ")
                       .append("g.modifiedBy, g.modifiedOn, g.labels");

                // Build WHERE clause
                where.append(" WHERE (1 = 1)");
                for (SearchFilter filter : filters) {
                    where.append(" AND (");
                    hasLabelJoin = buildGroupFilterClause(filter, where, joins, binders, labelJoinCounter) || hasLabelJoin;
                    where.append(")");
                }

                // Build ORDER BY clause
                buildGroupOrderBy(orderBy, orderByClause);
            }
        }

        orderByClause.append(" ").append(orderDirection.name());

        // Build final query
        StringBuilder sql = new StringBuilder();
        sql.append(select).append(from).append(joins).append(where);

        // Only add GROUP BY if we have label JOINs (to eliminate duplicates)
        if (hasLabelJoin) {
            sql.append(groupBy);
        }

        sql.append(orderByClause);
        sql.append(getLimitOffsetClause());

        return new SearchQuery(sql.toString(), binders);
    }

    /**
     * Builds filter clause for artifact search. Returns true if a label JOIN was added.
     */
    protected boolean buildArtifactFilterClause(SearchFilter filter, StringBuilder where,
            StringBuilder joins, List<SqlStatementVariableBinder> binders, AtomicInteger labelJoinCounter) {

        String op;
        switch (filter.getType()) {
            case description -> {
                op = filter.isNot() ? "NOT LIKE" : "LIKE";
                where.append("a.description ").append(op).append(" ?");
                binders.add((query, idx) -> query.bind(idx, "%" + filter.getStringValue() + "%"));
            }
            case name -> {
                buildNameFilter(filter, where, binders, "a.name", "a.artifactId");
            }
            case groupId -> {
                op = filter.isNot() ? "!=" : "=";
                where.append("a.groupId ").append(op).append(" ?");
                binders.add((query, idx) -> query.bind(idx, normalizeGroupId(filter.getStringValue())));
            }
            case artifactId -> {
                op = filter.isNot() ? "!=" : "=";
                where.append("a.artifactId ").append(op).append(" ?");
                binders.add((query, idx) -> query.bind(idx, filter.getStringValue()));
            }
            case artifactType -> {
                op = filter.isNot() ? "!=" : "=";
                where.append("a.type ").append(op).append(" ?");
                binders.add((query, idx) -> query.bind(idx, filter.getStringValue()));
            }
            case contentHash -> {
                op = filter.isNot() ? "!=" : "=";
                where.append("EXISTS(SELECT c.* FROM content c JOIN versions v ON c.contentId = v.contentId ")
                     .append("WHERE v.groupId = a.groupId AND v.artifactId = a.artifactId AND ")
                     .append("c.contentHash ").append(op).append(" ?)");
                binders.add((query, idx) -> query.bind(idx, filter.getStringValue()));
            }
            case canonicalHash -> {
                op = filter.isNot() ? "!=" : "=";
                where.append("EXISTS(SELECT c.* FROM content c JOIN versions v ON c.contentId = v.contentId ")
                     .append("WHERE v.groupId = a.groupId AND v.artifactId = a.artifactId AND ")
                     .append("c.canonicalHash ").append(op).append(" ?)");
                binders.add((query, idx) -> query.bind(idx, filter.getStringValue()));
            }
            case labels -> {
                return buildLabelJoinFilter(filter, where, joins, binders, labelJoinCounter,
                        "artifact_labels", "l.groupId = a.groupId AND l.artifactId = a.artifactId");
            }
            case globalId -> {
                op = filter.isNot() ? "!=" : "=";
                where.append("EXISTS(SELECT v.* FROM versions v WHERE v.groupId = a.groupId ")
                     .append("AND v.artifactId = a.artifactId AND v.globalId ").append(op).append(" ?)");
                binders.add((query, idx) -> query.bind(idx, filter.getNumberValue().longValue()));
            }
            case contentId -> {
                op = filter.isNot() ? "!=" : "=";
                where.append("EXISTS(SELECT c.* FROM content c JOIN versions v ON c.contentId = v.contentId ")
                     .append("WHERE v.groupId = a.groupId AND v.artifactId = a.artifactId AND ")
                     .append("v.contentId ").append(op).append(" ?)");
                binders.add((query, idx) -> query.bind(idx, filter.getNumberValue().longValue()));
            }
            case state -> {
                op = filter.isNot() ? "!=" : "=";
                where.append("EXISTS(SELECT v.* FROM versions v WHERE v.groupId = a.groupId ")
                     .append("AND v.artifactId = a.artifactId AND v.state ").append(op).append(" ?)");
                binders.add((query, idx) -> query.bind(idx, filter.getStringValue()));
            }
            default -> throw new RuntimeException("Filter type not supported: " + filter.getType());
        }
        return false;
    }

    /**
     * Builds filter clause for version search. Returns true if a label JOIN was added.
     */
    protected boolean buildVersionFilterClause(SearchFilter filter, StringBuilder where,
            StringBuilder joins, List<SqlStatementVariableBinder> binders, AtomicInteger labelJoinCounter) {

        String op;
        switch (filter.getType()) {
            case groupId -> {
                op = filter.isNot() ? "!=" : "=";
                where.append("a.groupId ").append(op).append(" ?");
                binders.add((query, idx) -> query.bind(idx, normalizeGroupId(filter.getStringValue())));
            }
            case artifactType -> {
                op = filter.isNot() ? "!=" : "=";
                where.append("a.type ").append(op).append(" ?");
                binders.add((query, idx) -> query.bind(idx, filter.getStringValue()));
            }
            case artifactId, state, version -> {
                op = filter.isNot() ? "!=" : "=";
                where.append("v.").append(filter.getType().name()).append(" ").append(op).append(" ?");
                binders.add((query, idx) -> query.bind(idx, filter.getStringValue()));
            }
            case contentId, globalId -> {
                op = filter.isNot() ? "!=" : "=";
                where.append("v.").append(filter.getType().name()).append(" ").append(op).append(" ?");
                binders.add((query, idx) -> query.bind(idx, filter.getNumberValue().longValue()));
            }
            case name, description -> {
                op = filter.isNot() ? "NOT LIKE" : "LIKE";
                where.append("v.").append(filter.getType().name()).append(" ").append(op).append(" ?");
                binders.add((query, idx) -> query.bind(idx, "%" + filter.getStringValue() + "%"));
            }
            case labels -> {
                return buildLabelJoinFilter(filter, where, joins, binders, labelJoinCounter,
                        "version_labels", "l.globalId = v.globalId");
            }
            case contentHash -> {
                op = filter.isNot() ? "!=" : "=";
                where.append("EXISTS(SELECT c.* FROM content c WHERE c.contentId = v.contentId AND ")
                     .append("c.contentHash ").append(op).append(" ?)");
                binders.add((query, idx) -> query.bind(idx, filter.getStringValue()));
            }
            case canonicalHash -> {
                op = filter.isNot() ? "!=" : "=";
                where.append("EXISTS(SELECT c.* FROM content c WHERE c.contentId = v.contentId AND ")
                     .append("c.canonicalHash ").append(op).append(" ?)");
                binders.add((query, idx) -> query.bind(idx, filter.getStringValue()));
            }
            default -> throw new RuntimeException("Filter type not supported: " + filter.getType());
        }
        return false;
    }

    /**
     * Builds filter clause for group search. Returns true if a label JOIN was added.
     */
    protected boolean buildGroupFilterClause(SearchFilter filter, StringBuilder where,
            StringBuilder joins, List<SqlStatementVariableBinder> binders, AtomicInteger labelJoinCounter) {

        String op;
        switch (filter.getType()) {
            case description -> {
                op = filter.isNot() ? "NOT LIKE" : "LIKE";
                where.append("g.description ").append(op).append(" ?");
                binders.add((query, idx) -> query.bind(idx, "%" + filter.getStringValue() + "%"));
            }
            case groupId -> {
                op = filter.isNot() ? "!=" : "=";
                where.append("g.groupId ").append(op).append(" ?");
                binders.add((query, idx) -> query.bind(idx, filter.getStringValue()));
            }
            case labels -> {
                return buildLabelJoinFilter(filter, where, joins, binders, labelJoinCounter,
                        "group_labels", "l.groupId = g.groupId");
            }
            default -> throw new RuntimeException("Filter type not supported: " + filter.getType());
        }
        return false;
    }

    /**
     * Builds a JOIN-based label filter using INNER JOIN for positive filters
     * and LEFT JOIN + IS NULL for NOT filters.
     */
    protected boolean buildLabelJoinFilter(SearchFilter filter, StringBuilder where, StringBuilder joins,
            List<SqlStatementVariableBinder> binders, AtomicInteger labelJoinCounter,
            String labelTable, String joinCondition) {

        Pair<String, String> label = filter.getLabelFilterValue();
        String labelKey = label.getKey().toLowerCase();
        String alias = "l" + labelJoinCounter.getAndIncrement();

        if (filter.isNot()) {
            // NOT filter: Use LEFT JOIN and check for NULL
            joins.append(" LEFT JOIN ").append(labelTable).append(" ").append(alias)
                 .append(" ON ").append(joinCondition.replace("l.", alias + "."))
                 .append(" AND ").append(alias).append(".labelKey = ?");
            binders.add((query, idx) -> query.bind(idx, labelKey));

            if (label.getValue() != null) {
                String labelValue = label.getValue().toLowerCase();
                joins.append(" AND ").append(alias).append(".labelValue = ?");
                binders.add((query, idx) -> query.bind(idx, labelValue));
            }

            where.append(alias).append(".labelKey IS NULL");
        } else {
            // Positive filter: Use INNER JOIN
            joins.append(" INNER JOIN ").append(labelTable).append(" ").append(alias)
                 .append(" ON ").append(joinCondition.replace("l.", alias + "."))
                 .append(" AND ").append(alias).append(".labelKey = ?");
            binders.add((query, idx) -> query.bind(idx, labelKey));

            if (label.getValue() != null) {
                String labelValue = label.getValue().toLowerCase();
                joins.append(" AND ").append(alias).append(".labelValue = ?");
                binders.add((query, idx) -> query.bind(idx, labelValue));
            }

            where.append("1 = 1");  // Placeholder since the filter is in the JOIN condition
        }

        return true;  // Label JOIN was added
    }

    protected void buildNameFilter(SearchFilter filter, StringBuilder where,
            List<SqlStatementVariableBinder> binders, String nameColumn, String idColumn) {

        String nameValue = filter.getStringValue();
        boolean startsWithWildcard = nameValue.startsWith("*");
        boolean endsWithWildcard = nameValue.endsWith("*");

        String searchValue = nameValue;
        if (startsWithWildcard) {
            searchValue = searchValue.substring(1);
        }
        if (endsWithWildcard) {
            searchValue = searchValue.substring(0, searchValue.length() - 1);
        }

        String op;
        if (startsWithWildcard || endsWithWildcard) {
            op = filter.isNot() ? "NOT LIKE" : "LIKE";
            where.append(nameColumn).append(" ").append(op).append(" ? OR ")
                 .append(idColumn).append(" ").append(op).append(" ?");

            String finalSearchValue = searchValue;
            binders.add((query, idx) -> {
                String pattern = finalSearchValue;
                if (startsWithWildcard) pattern = "%" + pattern;
                if (endsWithWildcard) pattern = pattern + "%";
                query.bind(idx, pattern);
            });
            binders.add((query, idx) -> {
                String pattern = finalSearchValue;
                if (startsWithWildcard) pattern = "%" + pattern;
                if (endsWithWildcard) pattern = pattern + "%";
                query.bind(idx, pattern);
            });
        } else {
            op = filter.isNot() ? "!=" : "=";
            where.append("(").append(nameColumn).append(" ").append(op).append(" ? OR ")
                 .append(idColumn).append(" ").append(op).append(" ?)");
            String finalSearchValue = searchValue;
            binders.add((query, idx) -> query.bind(idx, finalSearchValue));
            binders.add((query, idx) -> query.bind(idx, finalSearchValue));
        }
    }

    protected void buildArtifactOrderBy(OrderBy orderBy, StringBuilder orderByClause) {
        switch (orderBy) {
            case name -> orderByClause.append(" ORDER BY COALESCE(a.name, a.artifactId)");
            case artifactType -> orderByClause.append(" ORDER BY a.type");
            case groupId, artifactId, createdOn, modifiedOn -> orderByClause.append(" ORDER BY a.").append(orderBy.name());
            default -> throw new RuntimeException("Sort by " + orderBy.name() + " not supported.");
        }
    }

    protected void buildVersionOrderBy(OrderBy orderBy, StringBuilder orderByClause) {
        switch (orderBy) {
            case name -> orderByClause.append(" ORDER BY COALESCE(v.name, v.version)");
            case groupId, artifactId, version, globalId, createdOn, modifiedOn ->
                    orderByClause.append(" ORDER BY v.").append(orderBy.name());
            default -> throw new RuntimeException("Sort by " + orderBy.name() + " not supported.");
        }
    }

    protected void buildGroupOrderBy(OrderBy orderBy, StringBuilder orderByClause) {
        switch (orderBy) {
            case groupId, createdOn, modifiedOn -> orderByClause.append(" ORDER BY g.").append(orderBy.name());
            default -> throw new RuntimeException("Sort by " + orderBy.name() + " not supported.");
        }
    }

    protected String getArtifactsTable() {
        return "artifacts";
    }

    protected String getGroupsTable() {
        return "groups";
    }

    protected String getLimitOffsetClause() {
        if ("mssql".equals(sqlStatements.dbType())) {
            return " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        }
        return " LIMIT ? OFFSET ?";
    }
}
