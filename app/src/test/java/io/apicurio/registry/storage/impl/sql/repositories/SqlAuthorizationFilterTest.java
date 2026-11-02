package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.AuthorizationFilter;
import io.apicurio.registry.storage.impl.sql.jdb.Query;
import io.apicurio.registry.storage.impl.sql.SqlStatementVariableBinder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * The SQL each dialect emits for the search authorization condition. Results are checked against
 * real databases by {@code AuthorizationFilterStorage*Test}; this pins the shape: on MySQL and SQL
 * Server, every {@code =} and {@code LIKE} pairs the plain comparison (usable by the ID indexes)
 * with the binary one (which keeps matching case-sensitive), while {@code <>} stays binary only.
 */
class SqlAuthorizationFilterTest {

    private static final String STORED_DEFAULT = "__$GROUPID$__";

    /** One exact allow and one prefix deny, so each clause type appears once. */
    private static final AuthorizationFilter ARTIFACT_FILTER = new AuthorizationFilter(false,
            Set.of("team-a/schema"), Set.of(), Set.of(), Set.of("team-a/internal-"), "alice");

    /** A group prefix allow, whose condition excludes the stored default group with {@code <>}. */
    private static final AuthorizationFilter GROUP_FILTER = new AuthorizationFilter(false,
            Set.of(), Set.of("team-"), Set.of(), Set.of(), null);

    private record Emitted(String sql, List<String> parameters) {
    }

    private static Emitted artifactCondition(String dbType) {
        StringBuilder where = new StringBuilder();
        List<SqlStatementVariableBinder> binders = new ArrayList<>();
        SqlAuthorizationFilter.appendArtifactCondition(where, binders, ARTIFACT_FILTER, dbType, "a.groupId",
                "a.artifactId", "a.owner");
        return new Emitted(where.toString(), bound(binders));
    }

    private static Emitted groupCondition(String dbType) {
        StringBuilder where = new StringBuilder();
        List<SqlStatementVariableBinder> binders = new ArrayList<>();
        SqlAuthorizationFilter.appendGroupCondition(where, binders, GROUP_FILTER, dbType, "g.groupId", "g.owner");
        return new Emitted(where.toString(), bound(binders));
    }

    /** Runs the binders against a query that records the values, in parameter order. */
    private static List<String> bound(List<SqlStatementVariableBinder> binders) {
        List<String> values = new ArrayList<>();
        Query query = mock(Query.class);
        doAnswer(invocation -> {
            values.add(invocation.getArgument(1));
            return query;
        }).when(query).bind(anyInt(), anyString());
        for (int i = 0; i < binders.size(); i++) {
            binders.get(i).bind(query, i);
        }
        return values;
    }

    @Test
    void postgresqlComparesColumnsDirectly() {
        assertEquals(new Emitted("((((a.groupId = ? AND a.artifactId = ?))) AND NOT (((a.groupId = ? AND "
                + "a.artifactId LIKE ? ESCAPE '!')))) OR a.owner = ?",
                List.of("team-a", "schema", "team-a", "internal-%", "alice")), artifactCondition("postgresql"));
        assertEquals(new Emitted("((((g.groupId LIKE ? ESCAPE '!' AND g.groupId <> ?))) AND NOT (1 = 0))",
                List.of("team-%", STORED_DEFAULT)), groupCondition("postgresql"));
    }

    @Test
    void mysqlPairsEachComparisonWithABinaryOne() {
        assertEquals(new Emitted("(((((a.groupId = ? AND CAST(a.groupId AS BINARY) = CAST(? AS BINARY)) AND "
                + "(a.artifactId = ? AND CAST(a.artifactId AS BINARY) = CAST(? AS BINARY))))) AND NOT "
                + "((((a.groupId = ? AND CAST(a.groupId AS BINARY) = CAST(? AS BINARY)) AND "
                + "(a.artifactId LIKE ? ESCAPE '!' AND CAST(a.artifactId AS BINARY) LIKE CAST(? AS BINARY) "
                + "ESCAPE '!'))))) OR (a.owner = ? AND CAST(a.owner AS BINARY) = CAST(? AS BINARY))",
                List.of("team-a", "team-a", "schema", "schema", "team-a", "team-a", "internal-%", "internal-%",
                        "alice", "alice")),
                artifactCondition("mysql"));
        // <> stays binary only: a plain <> would also exclude other spellings of the default group
        assertEquals(new Emitted("(((((g.groupId LIKE ? ESCAPE '!' AND CAST(g.groupId AS BINARY) LIKE "
                + "CAST(? AS BINARY) ESCAPE '!') AND CAST(g.groupId AS BINARY) <> CAST(? AS BINARY)))) "
                + "AND NOT (1 = 0))",
                List.of("team-%", "team-%", STORED_DEFAULT)), groupCondition("mysql"));
    }

    @Test
    void mssqlPairsEachComparisonWithABinaryCollation() {
        assertEquals(new Emitted("(((((a.groupId = ? AND a.groupId COLLATE Latin1_General_100_BIN2 = ?) AND "
                + "(a.artifactId = ? AND a.artifactId COLLATE Latin1_General_100_BIN2 = ?)))) AND NOT "
                + "((((a.groupId = ? AND a.groupId COLLATE Latin1_General_100_BIN2 = ?) AND "
                + "(a.artifactId LIKE ? ESCAPE '!' AND a.artifactId COLLATE Latin1_General_100_BIN2 LIKE ? "
                + "ESCAPE '!'))))) OR (a.owner = ? AND a.owner COLLATE Latin1_General_100_BIN2 = ?)",
                List.of("team-a", "team-a", "schema", "schema", "team-a", "team-a", "internal-%", "internal-%",
                        "alice", "alice")),
                artifactCondition("mssql"));
        assertEquals(new Emitted("(((((g.groupId LIKE ? ESCAPE '!' AND g.groupId COLLATE "
                + "Latin1_General_100_BIN2 LIKE ? ESCAPE '!') AND g.groupId COLLATE Latin1_General_100_BIN2 "
                + "<> ?))) AND NOT (1 = 0))",
                List.of("team-%", "team-%", STORED_DEFAULT)), groupCondition("mssql"));
    }

    @Test
    void parametersMatchPlaceholders() {
        for (String dbType : List.of("postgresql", "h2", "mysql", "mssql")) {
            for (Emitted emitted : List.of(artifactCondition(dbType), groupCondition(dbType))) {
                long placeholders = emitted.sql().chars().filter(c -> c == '?').count();
                assertEquals(placeholders, emitted.parameters().size(), dbType + ": " + emitted.sql());
            }
        }
    }
}
