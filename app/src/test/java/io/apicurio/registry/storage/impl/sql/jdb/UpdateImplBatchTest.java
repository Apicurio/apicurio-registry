package io.apicurio.registry.storage.impl.sql.jdb;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateImplBatchTest {

    /**
     * The failed call discards the batch and the stray parameters, so the next batch on the same statement
     * holds only its own row.
     */
    @Test
    void parametersBoundAfterTheLastAddBatchFailFastAndTheNextBatchStartsClean() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        UpdateImpl update = new UpdateImpl(connection, "INSERT INTO t (a) VALUES (?)");
        update.bind(0, 1L).addBatch();
        update.bind(0, 2L);

        Assertions.assertThrows(IllegalStateException.class, update::executeBatch);
        verify(connection, never()).prepareStatement(anyString());

        update.bind(0, 3L).addBatch();
        update.executeBatch();
        verify(statement, times(1)).addBatch();
        verify(statement).setLong(1, 3L);
        verify(statement, never()).setLong(1, 1L);
        verify(statement, never()).setLong(1, 2L);
        verify(statement, times(1)).executeBatch();
    }

    @Test
    void theBatchIsClearedOnceItHasRun() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        UpdateImpl update = new UpdateImpl(connection, "INSERT INTO t (a) VALUES (?)");

        update.bind(0, 1L).addBatch().bind(0, 2L).addBatch();
        update.executeBatch();
        verify(statement, times(2)).addBatch();
        verify(statement).setLong(1, 1L);
        verify(statement).setLong(1, 2L);

        update.bind(0, 3L).addBatch();
        update.executeBatch();
        verify(statement, times(3)).addBatch();
        verify(statement, times(1)).setLong(1, 1L);
        verify(statement).setLong(1, 3L);
        verify(statement, times(2)).executeBatch();
    }

    /** A batch the database rejects is cleared too, so the next batch holds only its own row. */
    @Test
    void aBatchThatFailsInTheDatabaseIsClearedForTheNextOne() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement failing = mock(PreparedStatement.class);
        PreparedStatement next = mock(PreparedStatement.class);
        when(failing.executeBatch()).thenThrow(new SQLException("deadlock detected", "40P01"));
        when(connection.prepareStatement(anyString())).thenReturn(failing, next);
        UpdateImpl update = new UpdateImpl(connection, "INSERT INTO t (a) VALUES (?)");

        update.bind(0, 1L).addBatch();
        RuntimeSqlException thrown = Assertions.assertThrows(RuntimeSqlException.class, update::executeBatch);
        Assertions.assertEquals("40P01", ((SQLException) thrown.getCause()).getSQLState());

        update.bind(0, 2L).addBatch();
        update.executeBatch();
        verify(next, times(1)).addBatch();
        verify(next).setLong(1, 2L);
        verify(next, never()).setLong(1, 1L);
    }

    /**
     * A later row that skips a position would silently reuse the previous row's value, because a JDBC
     * statement keeps it. Each malformed row shape is rejected in addBatch with a message naming the row.
     */
    @Test
    void aLaterRowThatSkipsAPositionFails() {
        UpdateImpl update = twoColumnInsert();
        update.bind(0, 1L).bind(1, 10L).addBatch();
        update.bind(0, 2L);
        assertRejected(update, "Batch row 2 binds 1 parameters where the first row binds 2; the rows added before it were discarded too.");
    }

    @Test
    void aLaterRowLongerThanTheFirstFails() {
        UpdateImpl update = twoColumnInsert();
        update.bind(0, 1L).bind(1, 10L).addBatch();
        update.bind(0, 2L).bind(1, 20L).bind(2, 200L);
        assertRejected(update, "Batch row 2 binds 3 parameters where the first row binds 2; the rows added before it were discarded too.");
    }

    @Test
    void aFirstRowThatBindsAPositionTwiceFails() {
        UpdateImpl update = twoColumnInsert();
        update.bind(0, 1L).bind(0, 2L);
        assertRejected(update, "Batch row 1 binds position 0 twice.");
    }

    @Test
    void aRowWithAPositionOutsideItsWidthFails() {
        UpdateImpl update = twoColumnInsert();
        update.bind(0, 1L).bind(2, 10L);
        assertRejected(update, "Batch row 1 binds position 2, outside 0 to 1.");

        UpdateImpl negative = twoColumnInsert();
        negative.bind(-1, 1L).bind(0, 10L);
        assertRejected(negative, "Batch row 1 binds position -1, outside 0 to 1.");
    }

    @Test
    void aRowThatBindsAPositionTwiceFailsAndTheNextBatchStartsClean() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        UpdateImpl update = new UpdateImpl(connection, "INSERT INTO t (a, b) VALUES (?, ?)");
        update.bind(0, 1L).bind(1, 10L).addBatch();
        update.bind(0, 2L).bind(0, 3L);
        assertRejected(update, "Batch row 2 binds position 0 twice; the rows added before it were discarded too.");

        update.bind(0, 4L).bind(1, 40L).addBatch();
        update.executeBatch();
        verify(statement, times(1)).addBatch();
        verify(statement).setLong(1, 4L);
        verify(statement).setLong(2, 40L);
        verify(statement, never()).setLong(1, 1L);
        verify(statement, never()).setLong(2, 10L);
    }

    private static UpdateImpl twoColumnInsert() {
        return new UpdateImpl(mock(Connection.class), "INSERT INTO t (a, b) VALUES (?, ?)");
    }

    private static void assertRejected(UpdateImpl update, String message) {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, update::addBatch);
        Assertions.assertEquals(message, thrown.getMessage());
    }
}
