package io.apicurio.registry.storage.impl.sql;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * The orphan cleanup subqueries must correlate with the outer table. An unqualified {@code contentId} inside
 * the subquery resolves to {@code versions.contentId}, which makes the condition always true.
 */
class OrphanedContentSqlStatementsTest {

    @Test
    void testCommonDeleteOrphanedContentReferencesCorrelatesWithOuterTable() {
        String sql = new H2SqlStatements().deleteOrphanedContentReferences();
        Assertions.assertEquals(
                "DELETE FROM content_references WHERE NOT EXISTS (SELECT 1 FROM versions v WHERE v.contentId = content_references.contentId)",
                sql);
    }

    @Test
    void testCommonDeleteAllOrphanedContentCorrelatesWithOuterTable() {
        String sql = new H2SqlStatements().deleteAllOrphanedContent();
        Assertions.assertTrue(sql.contains("v.contentId = c.contentId"), sql);
    }

    @Test
    void testSqlServerDeleteAllOrphanedContentCorrelatesWithOuterTable() {
        String sql = new SQLServerSqlStatements().deleteAllOrphanedContent();
        Assertions.assertEquals(
                "DELETE FROM content WHERE NOT EXISTS (SELECT 1 FROM versions v WHERE v.contentId = content.contentId)",
                sql);
    }

    /**
     * PostgreSQL does not override {@code deleteAllOrphanedContent()}, so it runs the same
     * correlated query inherited from {@link CommonSqlStatements}. This is the path that matters
     * for PostgreSQL: {@code content_references} is cleaned up there by the {@code ON DELETE CASCADE}
     * foreign key rather than {@code deleteOrphanedContentReferences()}.
     */
    @Test
    void testPostgreSQLDeleteAllOrphanedContentCorrelatesWithOuterTable() {
        String sql = new PostgreSQLSqlStatements().deleteAllOrphanedContent();
        Assertions.assertEquals(
                "DELETE FROM content c WHERE NOT EXISTS (SELECT 1 FROM versions v WHERE v.contentId = c.contentId)",
                sql);
    }

    @Test
    void testH2ExecutionDeletesOrphanedContentReferencesAndOrphanedContent() throws SQLException {
        try (Connection conn = createH2Database()) {
            Assertions.assertEquals(3, countRows(conn, "content"));
            Assertions.assertEquals(2, countRows(conn, "content_references"));

            try (Statement stmt = conn.createStatement()) {
                int deletedRefs = stmt.executeUpdate(new H2SqlStatements().deleteOrphanedContentReferences());
                Assertions.assertEquals(1, deletedRefs);
            }

            Assertions.assertEquals(3, countRows(conn, "content"));
            Assertions.assertEquals(1, countRows(conn, "content_references"));

            try (Statement stmt = conn.createStatement()) {
                int deletedContent = stmt.executeUpdate(new H2SqlStatements().deleteAllOrphanedContent());
                Assertions.assertEquals(2, deletedContent);
            }

            Assertions.assertEquals(1, countRows(conn, "content"));
            Assertions.assertEquals(1, countRows(conn, "content_references"));
        }
    }

    @Test
    void testSqlServerStatementExecutionDeletesOrphanedContent() throws SQLException {
        try (Connection conn = createH2Database()) {
            Assertions.assertEquals(3, countRows(conn, "content"));
            Assertions.assertEquals(2, countRows(conn, "content_references"));

            try (Statement stmt = conn.createStatement()) {
                int deletedContent = stmt.executeUpdate(new SQLServerSqlStatements().deleteAllOrphanedContent());
                Assertions.assertEquals(2, deletedContent);
            }

            Assertions.assertEquals(1, countRows(conn, "content"));
            // FK with ON DELETE CASCADE removes reference 2 when content 2 is deleted
            Assertions.assertEquals(1, countRows(conn, "content_references"));
        }
    }

    private Connection createH2Database() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:h2:mem:orphantest_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE content (contentId BIGINT NOT NULL PRIMARY KEY)");
            stmt.execute("CREATE TABLE content_references (contentId BIGINT NOT NULL, name VARCHAR(512) NOT NULL, PRIMARY KEY (contentId, name), FOREIGN KEY (contentId) REFERENCES content(contentId) ON DELETE CASCADE)");
            stmt.execute("CREATE TABLE versions (globalId BIGINT NOT NULL PRIMARY KEY, contentId BIGINT NOT NULL, FOREIGN KEY (contentId) REFERENCES content(contentId))");

            stmt.execute("INSERT INTO content (contentId) VALUES (1), (2), (3)");
            stmt.execute("INSERT INTO content_references (contentId, name) VALUES (1, 'ref1'), (2, 'ref2')");
            stmt.execute("INSERT INTO versions (globalId, contentId) VALUES (1, 1)");
        }
        return conn;
    }

    private int countRows(Connection conn, String table) throws SQLException {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }
}

