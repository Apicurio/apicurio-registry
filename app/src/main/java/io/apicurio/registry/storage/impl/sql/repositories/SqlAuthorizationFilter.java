package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.AuthorizationFilter;
import io.apicurio.registry.storage.dto.AuthorizationNames;
import io.apicurio.registry.storage.impl.sql.SqlStatementVariableBinder;

import java.util.List;
import java.util.Set;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;

/**
 * Translates an {@link AuthorizationFilter} into a SQL condition. This is the only place where
 * per-resource authorization reaches SQL, for artifact, version and group searches on every
 * SQL-backed storage variant.
 *
 * <p>Artifact name patterns are decomposed into group/artifact clauses by
 * {@link AuthorizationNames}, the same code point access uses to name artifacts. The default
 * group, named {@code default}, maps to its stored ID. Matching follows the database collation
 * (case-insensitive on some MySQL and SQL Server setups).</p>
 */
final class SqlAuthorizationFilter {

    private static final String DEFAULT_GROUP = AuthorizationNames.DEFAULT_GROUP;
    private static final String STORED_DEFAULT_GROUP = normalizeGroupId(null);
    private static final String LIKE = " LIKE ? ESCAPE '" + SqlSearchRepository.LIKE_ESCAPE_CHAR + "'";

    private SqlAuthorizationFilter() {
    }

    /**
     * Appends the condition for artifacts or versions, identified by a group and artifact column.
     */
    static void appendArtifactCondition(StringBuilder where, List<SqlStatementVariableBinder> binders,
            AuthorizationFilter filter, String groupColumn, String artifactColumn, String ownerColumn) {
        appendCondition(where, binders, filter, ownerColumn, new NameMatcher() {
            @Override
            public void exact(String name, StringBuilder sql, List<SqlStatementVariableBinder> b) {
                appendArtifactExact(sql, b, name, groupColumn, artifactColumn);
            }

            @Override
            public void prefix(String prefix, StringBuilder sql, List<SqlStatementVariableBinder> b) {
                appendArtifactPrefix(sql, b, prefix, groupColumn, artifactColumn);
            }
        });
    }

    /**
     * Appends the condition for groups, identified by a group column.
     */
    static void appendGroupCondition(StringBuilder where, List<SqlStatementVariableBinder> binders,
            AuthorizationFilter filter, String groupColumn, String ownerColumn) {
        appendCondition(where, binders, filter, ownerColumn, new NameMatcher() {
            @Override
            public void exact(String name, StringBuilder sql, List<SqlStatementVariableBinder> b) {
                sql.append(groupColumn).append(" = ?");
                bind(b, normalizeGroupId(name));
            }

            @Override
            public void prefix(String prefix, StringBuilder sql, List<SqlStatementVariableBinder> b) {
                appendGroupPrefix(sql, b, prefix, groupColumn);
            }
        });
    }

    private interface NameMatcher {
        void exact(String name, StringBuilder sql, List<SqlStatementVariableBinder> binders);

        void prefix(String prefix, StringBuilder sql, List<SqlStatementVariableBinder> binders);
    }

    /** {@code ((allowed) AND NOT (denied)) OR owner = ?} */
    private static void appendCondition(StringBuilder where, List<SqlStatementVariableBinder> binders,
            AuthorizationFilter filter, String ownerColumn, NameMatcher matcher) {
        where.append("((");
        if (filter.allowAll()) {
            where.append("1 = 1");
        } else {
            appendAnyOf(where, binders, filter.allowExact(), filter.allowPrefix(), matcher);
        }
        where.append(") AND NOT (");
        appendAnyOf(where, binders, filter.denyExact(), filter.denyPrefix(), matcher);
        where.append("))");
        if (filter.owner() != null) {
            where.append(" OR ").append(ownerColumn).append(" = ?");
            bind(binders, filter.owner());
        }
    }

    /** ORs one clause per pattern; no patterns matches nothing. */
    private static void appendAnyOf(StringBuilder where, List<SqlStatementVariableBinder> binders,
            Set<String> exact, Set<String> prefixes, NameMatcher matcher) {
        if (exact.isEmpty() && prefixes.isEmpty()) {
            where.append("1 = 0");
            return;
        }
        boolean first = true;
        for (String name : exact) {
            where.append(first ? "(" : " OR (");
            matcher.exact(name, where, binders);
            where.append(")");
            first = false;
        }
        for (String prefix : prefixes) {
            where.append(first ? "(" : " OR (");
            matcher.prefix(prefix, where, binders);
            where.append(")");
            first = false;
        }
    }

    private static void appendArtifactExact(StringBuilder sql, List<SqlStatementVariableBinder> binders,
            String name, String groupColumn, String artifactColumn) {
        appendClauses(sql, binders, AuthorizationNames.artifactsNamed(name), groupColumn, artifactColumn);
    }

    private static void appendArtifactPrefix(StringBuilder sql, List<SqlStatementVariableBinder> binders,
            String prefix, String groupColumn, String artifactColumn) {
        appendClauses(sql, binders, AuthorizationNames.artifactsWithPrefix(prefix), groupColumn, artifactColumn);
    }

    /** ORs the clauses; no clauses matches nothing. */
    private static void appendClauses(StringBuilder sql, List<SqlStatementVariableBinder> binders,
            List<AuthorizationNames.Clause> clauses, String groupColumn, String artifactColumn) {
        if (clauses.isEmpty()) {
            sql.append("1 = 0");
            return;
        }
        boolean first = true;
        for (AuthorizationNames.Clause clause : clauses) {
            sql.append(first ? "(" : " OR (");
            if (clause.groupPrefix()) {
                appendGroupPrefixRaw(sql, binders, clause.group(), groupColumn);
            } else {
                sql.append(groupColumn).append(" = ?");
                bind(binders, normalizeGroupId(clause.group()));
            }
            if (clause.artifact() != null) {
                sql.append(" AND ").append(artifactColumn);
                if (clause.artifactPrefix()) {
                    sql.append(LIKE);
                    bind(binders, SqlSearchRepository.escapeLikePattern(clause.artifact()) + "%");
                } else {
                    sql.append(" = ?");
                    bind(binders, clause.artifact());
                }
            }
            sql.append(")");
            first = false;
        }
    }

    /** Raw (stored) group IDs starting with {@code prefix}, excluding the stored default group. */
    private static void appendGroupPrefixRaw(StringBuilder sql, List<SqlStatementVariableBinder> binders,
            String prefix, String groupColumn) {
        sql.append("(").append(groupColumn).append(LIKE).append(" AND ").append(groupColumn).append(" <> ?)");
        bind(binders, SqlSearchRepository.escapeLikePattern(prefix) + "%");
        bind(binders, STORED_DEFAULT_GROUP);
    }

    private static void appendGroupPrefix(StringBuilder sql, List<SqlStatementVariableBinder> binders,
            String prefix, String groupColumn) {
        appendGroupPrefixRaw(sql, binders, prefix, groupColumn);
        if (DEFAULT_GROUP.startsWith(prefix)) {
            sql.append(" OR ").append(groupColumn).append(" = ?");
            bind(binders, STORED_DEFAULT_GROUP);
        }
    }

    private static void bind(List<SqlStatementVariableBinder> binders, String value) {
        binders.add((query, idx) -> query.bind(idx, value));
    }
}
