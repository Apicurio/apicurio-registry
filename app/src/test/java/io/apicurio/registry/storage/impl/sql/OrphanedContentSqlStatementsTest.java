package io.apicurio.registry.storage.impl.sql;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
}
