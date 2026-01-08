package io.apicurio.registry.storage.impl.sql.search;

import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.impl.sql.SqlStatementVariableBinder;
import io.apicurio.registry.storage.impl.sql.SqlStatements;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SQL Server-specific search query builder.
 * <p>
 * SQL Server has limitations with TEXT columns:
 * - TEXT columns cannot be used with GROUP BY or DISTINCT directly
 * - TEXT columns must be cast to NVARCHAR(MAX) for these operations
 * <p>
 * This implementation casts the 'labels' TEXT column to NVARCHAR(MAX)
 * in both SELECT and GROUP BY clauses to enable JOIN-based label filtering.
 */
public class SQLServerSearchQueryBuilder extends CommonSearchQueryBuilder {

    public SQLServerSearchQueryBuilder(SqlStatements sqlStatements) {
        super(sqlStatements);
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

        AtomicInteger labelJoinCounter = new AtomicInteger(0);
        boolean hasLabelJoin = false;

        switch (entityType) {
            case ARTIFACT -> {
                // Cast labels TEXT column to NVARCHAR(MAX) for GROUP BY compatibility
                select.append("SELECT a.groupId, a.artifactId, a.type, a.owner, a.createdOn, ")
                      .append("a.modifiedBy, a.modifiedOn, a.name, a.description, ")
                      .append("CAST(a.labels AS NVARCHAR(MAX)) AS labels, ")
                      .append("COUNT(*) OVER() AS total_count");
                from.append(" FROM ").append(getArtifactsTable()).append(" a");
                groupBy.append(" GROUP BY a.groupId, a.artifactId, a.type, a.owner, a.createdOn, ")
                       .append("a.modifiedBy, a.modifiedOn, a.name, a.description, ")
                       .append("CAST(a.labels AS NVARCHAR(MAX))");

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

                buildArtifactOrderBy(orderBy, orderByClause);
            }
            case VERSION -> {
                // Cast labels TEXT column to NVARCHAR(MAX) for GROUP BY compatibility
                select.append("SELECT v.globalId, v.groupId, v.artifactId, v.version, v.versionOrder, ")
                      .append("v.state, v.name, v.description, v.owner, v.createdOn, v.modifiedBy, ")
                      .append("v.modifiedOn, CAST(v.labels AS NVARCHAR(MAX)) AS labels, v.contentId, a.type, ")
                      .append("COUNT(*) OVER() AS total_count");
                from.append(" FROM versions v JOIN ").append(getArtifactsTable())
                    .append(" a ON v.groupId = a.groupId AND v.artifactId = a.artifactId");
                groupBy.append(" GROUP BY v.globalId, v.groupId, v.artifactId, v.version, v.versionOrder, ")
                       .append("v.state, v.name, v.description, v.owner, v.createdOn, v.modifiedBy, ")
                       .append("v.modifiedOn, CAST(v.labels AS NVARCHAR(MAX)), v.contentId, a.type");

                where.append(" WHERE (1 = 1)");
                for (SearchFilter filter : filters) {
                    where.append(" AND (");
                    hasLabelJoin = buildVersionFilterClause(filter, where, joins, binders, labelJoinCounter) || hasLabelJoin;
                    where.append(")");
                }

                buildVersionOrderBy(orderBy, orderByClause);
            }
            case GROUP -> {
                // Cast labels TEXT column to NVARCHAR(MAX) for GROUP BY compatibility
                select.append("SELECT g.groupId, g.description, g.artifactsType, g.owner, g.createdOn, ")
                      .append("g.modifiedBy, g.modifiedOn, CAST(g.labels AS NVARCHAR(MAX)) AS labels, ")
                      .append("COUNT(*) OVER() AS total_count");
                from.append(" FROM ").append(getGroupsTable()).append(" g");
                groupBy.append(" GROUP BY g.groupId, g.description, g.artifactsType, g.owner, g.createdOn, ")
                       .append("g.modifiedBy, g.modifiedOn, CAST(g.labels AS NVARCHAR(MAX))");

                where.append(" WHERE (1 = 1)");
                for (SearchFilter filter : filters) {
                    where.append(" AND (");
                    hasLabelJoin = buildGroupFilterClause(filter, where, joins, binders, labelJoinCounter) || hasLabelJoin;
                    where.append(")");
                }

                buildGroupOrderBy(orderBy, orderByClause);
            }
        }

        orderByClause.append(" ").append(orderDirection.name());

        StringBuilder sql = new StringBuilder();
        sql.append(select).append(from).append(joins).append(where);

        if (hasLabelJoin) {
            sql.append(groupBy);
        }

        sql.append(orderByClause);
        sql.append(getLimitOffsetClause());

        return new SearchQuery(sql.toString(), binders);
    }
}
