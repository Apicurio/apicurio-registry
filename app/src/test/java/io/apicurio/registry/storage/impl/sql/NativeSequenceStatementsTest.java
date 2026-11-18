package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.impl.sql.repositories.SqlSequenceRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class NativeSequenceStatementsTest {

    @Test
    void mapsLogicalNamesToPhysicalNames() {
        Assertions.assertEquals("globalid_seq",
                CommonSqlStatements.nativeSequenceName(SqlSequenceRepository.GLOBAL_ID_SEQUENCE));
        Assertions.assertEquals("contentid_seq",
                CommonSqlStatements.nativeSequenceName(SqlSequenceRepository.CONTENT_ID_SEQUENCE));
        Assertions.assertEquals("commentid_seq",
                CommonSqlStatements.nativeSequenceName(SqlSequenceRepository.COMMENT_ID_SEQUENCE));
    }

    @Test
    void rejectsUnknownSequenceNames() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> CommonSqlStatements.nativeSequenceName("globalId; DROP TABLE versions"));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new PostgreSQLSqlStatements().nextSequenceValue("other"));
    }

    @Test
    void postgresqlStatements() {
        SqlStatements s = new PostgreSQLSqlStatements();
        Assertions.assertEquals("SELECT nextval('globalid_seq')", s.nextSequenceValue("globalId"));
        Assertions.assertEquals("SELECT CASE WHEN is_called THEN last_value ELSE 0 END FROM globalid_seq",
                s.selectSequenceLastValue("globalId"));
        Assertions.assertEquals("SELECT setval('globalid_seq', GREATEST(?, (SELECT last_value FROM globalid_seq)), "
                + "true)", s.advanceSequenceValue("globalId"));
    }

    @Test
    void sqlServerStatements() {
        SqlStatements s = new SQLServerSqlStatements();
        Assertions.assertEquals("SELECT NEXT VALUE FOR contentid_seq", s.nextSequenceValue("contentId"));
        Assertions.assertEquals("SELECT COALESCE(CAST(last_used_value AS BIGINT), 0) FROM sys.sequences "
                + "WHERE name = 'contentid_seq'", s.selectSequenceLastValue("contentId"));
        Assertions.assertEquals("DECLARE @range_first_value SQL_VARIANT; EXEC sp_sequence_get_range "
                + "@sequence_name = N'contentid_seq', @range_size = ?, "
                + "@range_first_value = @range_first_value OUTPUT", s.advanceSequenceValue("contentId"));
    }

    @Test
    void mysqlStatements() {
        SqlStatements s = new MySQLSqlStatements();
        Assertions.assertEquals("INSERT INTO commentid_seq () VALUES ()", s.nextSequenceValue("commentId"));
        Assertions.assertEquals("SELECT LAST_INSERT_ID()", s.selectLastInsertedSequenceValue());
        Assertions.assertEquals("SELECT COALESCE(MAX(id), 0) FROM commentid_seq",
                s.selectSequenceLastValue("commentId"));
        Assertions.assertEquals("INSERT IGNORE INTO commentid_seq (id) VALUES (?)",
                s.advanceSequenceValue("commentId"));
        Assertions.assertEquals("DELETE FROM commentid_seq WHERE id < ?",
                s.deleteSequenceTicketsBelow("commentId"));
    }

    @Test
    void h2AndNonMysqlRejectUnsupportedStatements() {
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> new H2SqlStatements().nextSequenceValue("globalId"));
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> new PostgreSQLSqlStatements().selectLastInsertedSequenceValue());
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> new SQLServerSqlStatements().deleteSequenceTicketsBelow("globalId"));
    }
}
