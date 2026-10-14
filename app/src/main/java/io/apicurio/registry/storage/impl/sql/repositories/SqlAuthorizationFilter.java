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
 * group, named {@code default}, maps to its stored ID.</p>
 *
 * <p>Point access compares names case-sensitively, so every comparison here is forced to be
 * case-sensitive too, whatever the column collation: byte-wise on MySQL (whose ID columns use
 * {@code ascii_general_ci}) and with a binary collation on SQL Server. H2 and PostgreSQL compare
 * case-sensitively by default. The forced comparisons cannot use the ID indexes.</p>
 */
final class SqlAuthorizationFilter {

    private static final String DEFAULT_GROUP = AuthorizationNames.DEFAULT_GROUP;
    private static final String STORED_DEFAULT_GROUP = normalizeGroupId(null);
    private static final String ESCAPE = " ESCAPE '" + SqlSearchRepository.LIKE_ESCAPE_CHAR + "'";

    /** How to make a column comparison case-sensitive in a SQL dialect. */
    private record Dialect(String columnPrefix, String columnSuffix, String paramPrefix, String paramSuffix) {

        static Dialect of(String dbType) {
            return switch (dbType == null ? "" : dbType) {
                case "mysql" -> new Dialect("CAST(", " AS BINARY)", "CAST(", " AS BINARY)");
                case "mssql" -> new Dialect("", " COLLATE Latin1_General_100_BIN2", "", "");
                default -> new Dialect("", "", "", "");
            };
        }

        String column(String column) {
            return columnPrefix + column + columnSuffix;
        }

        String param() {
            return paramPrefix + "?" + paramSuffix;
        }

        String equals(String column) {
            return column(column) + " = " + param();
        }

        String notEquals(String column) {
            return column(column) + " <> " + param();
        }

        String like(String column) {
            return column(column) + " LIKE " + param() + ESCAPE;
        }
    }

    private SqlAuthorizationFilter() {
    }

    /**
     * Appends the condition for artifacts or versions, identified by a group and artifact column.
     *
     * @param dbType the SQL dialect, as returned by {@code SqlStatements.dbType()}
     */
    static void appendArtifactCondition(StringBuilder where, List<SqlStatementVariableBinder> binders,
            AuthorizationFilter filter, String dbType, String groupColumn, String artifactColumn,
            String ownerColumn) {
        Dialect dialect = Dialect.of(dbType);
        appendCondition(where, binders, filter, dialect, ownerColumn, new NameMatcher() {
            @Override
            public void exact(String name, StringBuilder sql, List<SqlStatementVariableBinder> b) {
                appendClauses(sql, b, dialect, AuthorizationNames.artifactsNamed(name), groupColumn,
                        artifactColumn);
            }

            @Override
            public void prefix(String prefix, StringBuilder sql, List<SqlStatementVariableBinder> b) {
                appendClauses(sql, b, dialect, AuthorizationNames.artifactsWithPrefix(prefix), groupColumn,
                        artifactColumn);
            }
        });
    }

    /**
     * Appends the condition for groups, identified by a group column.
     *
     * @param dbType the SQL dialect, as returned by {@code SqlStatements.dbType()}
     */
    static void appendGroupCondition(StringBuilder where, List<SqlStatementVariableBinder> binders,
            AuthorizationFilter filter, String dbType, String groupColumn, String ownerColumn) {
        Dialect dialect = Dialect.of(dbType);
        appendCondition(where, binders, filter, dialect, ownerColumn, new NameMatcher() {
            @Override
            public void exact(String name, StringBuilder sql, List<SqlStatementVariableBinder> b) {
                sql.append(dialect.equals(groupColumn));
                bind(b, normalizeGroupId(name));
            }

            @Override
            public void prefix(String prefix, StringBuilder sql, List<SqlStatementVariableBinder> b) {
                appendGroupPrefix(sql, b, dialect, prefix, groupColumn);
                if (DEFAULT_GROUP.startsWith(prefix)) {
                    sql.append(" OR ").append(dialect.equals(groupColumn));
                    bind(b, STORED_DEFAULT_GROUP);
                }
            }
        });
    }

    private interface NameMatcher {
        void exact(String name, StringBuilder sql, List<SqlStatementVariableBinder> binders);

        void prefix(String prefix, StringBuilder sql, List<SqlStatementVariableBinder> binders);
    }

    /** {@code ((allowed) AND NOT (denied)) OR owner = ?} */
    private static void appendCondition(StringBuilder where, List<SqlStatementVariableBinder> binders,
            AuthorizationFilter filter, Dialect dialect, String ownerColumn, NameMatcher matcher) {
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
            where.append(" OR ").append(dialect.equals(ownerColumn));
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

    /** ORs the clauses; no clauses matches nothing. */
    private static void appendClauses(StringBuilder sql, List<SqlStatementVariableBinder> binders,
            Dialect dialect, List<AuthorizationNames.Clause> clauses, String groupColumn, String artifactColumn) {
        if (clauses.isEmpty()) {
            sql.append("1 = 0");
            return;
        }
        boolean first = true;
        for (AuthorizationNames.Clause clause : clauses) {
            sql.append(first ? "(" : " OR (");
            if (clause.groupPrefix()) {
                appendGroupPrefix(sql, binders, dialect, clause.group(), groupColumn);
            } else {
                sql.append(dialect.equals(groupColumn));
                bind(binders, normalizeGroupId(clause.group()));
            }
            if (clause.artifact() != null) {
                sql.append(" AND ");
                if (clause.artifactPrefix()) {
                    sql.append(dialect.like(artifactColumn));
                    bind(binders, SqlSearchRepository.escapeLikePattern(clause.artifact()) + "%");
                } else {
                    sql.append(dialect.equals(artifactColumn));
                    bind(binders, clause.artifact());
                }
            }
            sql.append(")");
            first = false;
        }
    }

    /** Stored group IDs starting with {@code prefix}, excluding the stored default group. */
    private static void appendGroupPrefix(StringBuilder sql, List<SqlStatementVariableBinder> binders,
            Dialect dialect, String prefix, String groupColumn) {
        sql.append("(").append(dialect.like(groupColumn)).append(" AND ").append(dialect.notEquals(groupColumn))
                .append(")");
        bind(binders, SqlSearchRepository.escapeLikePattern(prefix) + "%");
        bind(binders, STORED_DEFAULT_GROUP);
    }

    private static void bind(List<SqlStatementVariableBinder> binders, String value) {
        binders.add((query, idx) -> query.bind(idx, value));
    }
}
