package io.apicurio.registry.storage.impl.sql.search;

import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.impl.sql.SqlStatementVariableBinder;
import io.apicurio.registry.storage.impl.sql.SqlStatements;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PostgreSQL-specific search query builder.
 * <p>
 * When trigram support is enabled, this builder uses ILIKE for case-insensitive
 * substring searches. PostgreSQL's pg_trgm extension provides GIN indexes that
 * optimize both LIKE and ILIKE queries with wildcards.
 * <p>
 * Benefits of ILIKE with trigram indexes:
 * - Case-insensitive search without performance penalty
 * - Trigram GIN indexes are used for '%pattern%' queries
 * - Better user experience for search functionality
 */
public class PostgreSQLSearchQueryBuilder extends CommonSearchQueryBuilder {

    private final boolean trigramEnabled;

    public PostgreSQLSearchQueryBuilder(SqlStatements sqlStatements, boolean trigramEnabled) {
        super(sqlStatements);
        this.trigramEnabled = trigramEnabled;
    }

    /**
     * Returns the LIKE operator to use for substring searches.
     * When trigram is enabled, uses ILIKE for case-insensitive matching.
     */
    protected String getLikeOperator(boolean negated) {
        if (trigramEnabled) {
            return negated ? "NOT ILIKE" : "ILIKE";
        }
        return negated ? "NOT LIKE" : "LIKE";
    }

    @Override
    protected boolean buildArtifactFilterClause(SearchFilter filter, StringBuilder where,
            StringBuilder joins, List<SqlStatementVariableBinder> joinBinders,
            List<SqlStatementVariableBinder> whereBinders, AtomicInteger labelJoinCounter) {

        switch (filter.getType()) {
            case description -> {
                String op = getLikeOperator(filter.isNot());
                where.append("a.description ").append(op).append(" ?");
                whereBinders.add((query, idx) -> query.bind(idx, "%" + filter.getStringValue() + "%"));
                return false;
            }
            case name -> {
                buildNameFilter(filter, where, whereBinders, "a.name", "a.artifactId");
                return false;
            }
            default -> {
                return super.buildArtifactFilterClause(filter, where, joins, joinBinders, whereBinders, labelJoinCounter);
            }
        }
    }

    @Override
    protected boolean buildVersionFilterClause(SearchFilter filter, StringBuilder where,
            StringBuilder joins, List<SqlStatementVariableBinder> joinBinders,
            List<SqlStatementVariableBinder> whereBinders, AtomicInteger labelJoinCounter) {

        switch (filter.getType()) {
            case name, description -> {
                String op = getLikeOperator(filter.isNot());
                where.append("v.").append(filter.getType().name()).append(" ").append(op).append(" ?");
                whereBinders.add((query, idx) -> query.bind(idx, "%" + filter.getStringValue() + "%"));
                return false;
            }
            default -> {
                return super.buildVersionFilterClause(filter, where, joins, joinBinders, whereBinders, labelJoinCounter);
            }
        }
    }

    @Override
    protected boolean buildGroupFilterClause(SearchFilter filter, StringBuilder where,
            StringBuilder joins, List<SqlStatementVariableBinder> joinBinders,
            List<SqlStatementVariableBinder> whereBinders, AtomicInteger labelJoinCounter) {

        switch (filter.getType()) {
            case description -> {
                String op = getLikeOperator(filter.isNot());
                where.append("g.description ").append(op).append(" ?");
                whereBinders.add((query, idx) -> query.bind(idx, "%" + filter.getStringValue() + "%"));
                return false;
            }
            default -> {
                return super.buildGroupFilterClause(filter, where, joins, joinBinders, whereBinders, labelJoinCounter);
            }
        }
    }

    @Override
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

        if (startsWithWildcard || endsWithWildcard) {
            String op = getLikeOperator(filter.isNot());
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
            // Exact match - use = operator (case-sensitive even with trigram)
            String op = filter.isNot() ? "!=" : "=";
            where.append("(").append(nameColumn).append(" ").append(op).append(" ? OR ")
                 .append(idColumn).append(" ").append(op).append(" ?)");
            String finalSearchValue = searchValue;
            binders.add((query, idx) -> query.bind(idx, finalSearchValue));
            binders.add((query, idx) -> query.bind(idx, finalSearchValue));
        }
    }
}
