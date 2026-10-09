package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.AuthorizationFilter;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.SqlStatementVariableBinder;

import java.util.List;
import java.util.Set;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;

/**
 * Translates an {@link AuthorizationFilter} into a SQL condition. This is the only place where
 * per-resource authorization reaches SQL, for artifact, version and group searches on every
 * SQL-backed storage variant.
 *
 * <p>Grants name artifacts {@code groupId/artifactId}. Group IDs may themselves contain '/', so a
 * name pattern is never split at a single slash; it is decomposed exactly:</p>
 * <ul>
 * <li>{@code "g/a"} equals pattern {@code n} iff, for some '/' at position k in {@code n},
 * {@code g = n[0,k)} and {@code a = n[k+1..]}</li>
 * <li>{@code "g/a"} starts with {@code p} iff {@code g} starts with {@code p}, or for some '/' at
 * position k in {@code p}, {@code g = p[0,k)} and {@code a} starts with {@code p[k+1..]}</li>
 * </ul>
 * <p>The default group is named {@code default} in grants but stored as
 * {@link RegistryContentUtils#normalizeGroupId(String) a reserved ID}, which is handled explicitly.
 * Matching follows the database collation (case-insensitive on some MySQL and SQL Server setups).</p>
 */
final class SqlAuthorizationFilter {

    private static final String DEFAULT_GROUP = "default";
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
        boolean first = true;
        for (int k = name.indexOf('/'); k >= 0; k = name.indexOf('/', k + 1)) {
            sql.append(first ? "" : " OR ");
            sql.append("(").append(groupColumn).append(" = ? AND ").append(artifactColumn).append(" = ?)");
            bind(binders, normalizeGroupId(name.substring(0, k)));
            bind(binders, name.substring(k + 1));
            first = false;
        }
        if (first) {
            // No '/': cannot name an artifact
            sql.append("1 = 0");
        }
    }

    private static void appendArtifactPrefix(StringBuilder sql, List<SqlStatementVariableBinder> binders,
            String prefix, String groupColumn, String artifactColumn) {
        // Either the group alone already starts with the prefix...
        appendGroupPrefix(sql, binders, prefix, groupColumn);
        // ...or the prefix spans the whole group plus the start of the artifact ID
        for (int k = prefix.indexOf('/'); k >= 0; k = prefix.indexOf('/', k + 1)) {
            sql.append(" OR (").append(groupColumn).append(" = ? AND ").append(artifactColumn).append(LIKE)
                    .append(")");
            bind(binders, normalizeGroupId(prefix.substring(0, k)));
            bind(binders, SqlSearchRepository.escapeLikePattern(prefix.substring(k + 1)) + "%");
        }
    }

    private static void appendGroupPrefix(StringBuilder sql, List<SqlStatementVariableBinder> binders,
            String prefix, String groupColumn) {
        sql.append("(").append(groupColumn).append(LIKE).append(" AND ").append(groupColumn).append(" <> ?)");
        bind(binders, SqlSearchRepository.escapeLikePattern(prefix) + "%");
        bind(binders, STORED_DEFAULT_GROUP);
        if (DEFAULT_GROUP.startsWith(prefix)) {
            sql.append(" OR ").append(groupColumn).append(" = ?");
            bind(binders, STORED_DEFAULT_GROUP);
        }
    }

    private static void bind(List<SqlStatementVariableBinder> binders, String value) {
        binders.add((query, idx) -> query.bind(idx, value));
    }
}
