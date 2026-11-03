package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.jdb.HandleCallback;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractHandleFactoryTest {

    @Test
    void shouldPropagateCommitFailureAfterClosingConnection() throws Exception {
        AgroalDataSource dataSource = mock(AgroalDataSource.class);
        Connection connection = mock(Connection.class);
        SQLException commitFailure = new SQLException("commit failed");
        when(dataSource.getConnection()).thenReturn(connection);
        doThrow(commitFailure).when(connection).commit();
        AbstractHandleFactory factory = factory(dataSource);
        HandleCallback<Void, RuntimeException> callback = handle -> null;

        RegistryStorageException thrown = assertThrows(RegistryStorageException.class,
                () -> factory.withHandle(callback));

        assertSame(commitFailure, thrown.getCause());
        verify(connection).commit();
        verify(connection).close();
    }

    @Test
    void shouldPreserveCallbackFailureWhenRollbackFails() throws Exception {
        AgroalDataSource dataSource = mock(AgroalDataSource.class);
        Connection connection = mock(Connection.class);
        SQLException rollbackFailure = new SQLException("rollback failed");
        RuntimeException callbackFailure = new IllegalStateException("callback failed");
        when(dataSource.getConnection()).thenReturn(connection);
        doThrow(rollbackFailure).when(connection).rollback();
        AbstractHandleFactory factory = factory(dataSource);
        HandleCallback<Void, RuntimeException> callback = handle -> {
            throw callbackFailure;
        };

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> factory.withHandle(callback));

        assertSame(callbackFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(rollbackFailure, thrown.getSuppressed()[0].getCause());
        verify(connection).rollback();
        verify(connection).close();
    }

    @Test
    void shouldPropagateRollbackFailureWhenCallbackSucceeds() throws Exception {
        AgroalDataSource dataSource = mock(AgroalDataSource.class);
        Connection connection = mock(Connection.class);
        SQLException rollbackFailure = new SQLException("rollback failed");
        when(dataSource.getConnection()).thenReturn(connection);
        doThrow(rollbackFailure).when(connection).rollback();
        AbstractHandleFactory factory = factory(dataSource);
        HandleCallback<String, RuntimeException> dryRun = handle -> {
            handle.setRollback(true);
            return "dry-run";
        };

        RegistryStorageException thrown = assertThrows(RegistryStorageException.class,
                () -> factory.withHandle(dryRun));

        assertSame(rollbackFailure, thrown.getCause());
        verify(connection, never()).commit();
        verify(connection).close();
    }

    @Test
    void shouldPropagateCheckedCallbackFailureAfterRollback() throws Exception {
        AgroalDataSource dataSource = mock(AgroalDataSource.class);
        Connection connection = mock(Connection.class);
        Exception callbackFailure = new Exception("callback failed");
        when(dataSource.getConnection()).thenReturn(connection);

        Exception thrown = assertThrows(Exception.class,
                () -> factory(dataSource).withHandle((HandleCallback<Void, Exception>) handle -> {
                    throw callbackFailure;
                }));

        assertSame(callbackFailure, thrown);
        verify(connection).rollback();
        verify(connection).close();
    }

    @Test
    void shouldResetHandleAfterConnectionCloseFailure() throws Exception {
        AgroalDataSource dataSource = mock(AgroalDataSource.class);
        Connection firstConnection = mock(Connection.class);
        Connection secondConnection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(firstConnection, secondConnection);
        doThrow(new SQLException("close failed")).when(firstConnection).close();

        AbstractHandleFactory factory = factory(dataSource);

        assertEquals("first", factory.withHandle((HandleCallback<String, RuntimeException>) handle -> "first"));
        assertEquals("second", factory.withHandle((HandleCallback<String, RuntimeException>) handle -> "second"));

        verify(dataSource, times(2)).getConnection();
        verify(firstConnection).close();
        verify(secondConnection).close();
    }

    @Test
    void shouldRollbackWhenCallbackThrowsError() throws Exception {
        AgroalDataSource dataSource = mock(AgroalDataSource.class);
        Connection connection = mock(Connection.class);
        StackOverflowError callbackFailure = new StackOverflowError("callback failed");
        when(dataSource.getConnection()).thenReturn(connection);
        AbstractHandleFactory factory = factory(dataSource);
        HandleCallback<Void, RuntimeException> callback = handle -> {
            throw callbackFailure;
        };

        StackOverflowError thrown = assertThrows(StackOverflowError.class,
                () -> factory.withHandle(callback));

        assertSame(callbackFailure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection).close();
    }

    @Test
    void shouldAttachRollbackFailureToCallbackError() throws Exception {
        AgroalDataSource dataSource = mock(AgroalDataSource.class);
        Connection connection = mock(Connection.class);
        SQLException rollbackFailure = new SQLException("rollback failed");
        StackOverflowError callbackFailure = new StackOverflowError("callback failed");
        when(dataSource.getConnection()).thenReturn(connection);
        doThrow(rollbackFailure).when(connection).rollback();
        AbstractHandleFactory factory = factory(dataSource);
        HandleCallback<Void, RuntimeException> callback = handle -> {
            throw callbackFailure;
        };

        StackOverflowError thrown = assertThrows(StackOverflowError.class,
                () -> factory.withHandle(callback));

        assertSame(callbackFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertInstanceOf(RegistryStorageException.class, thrown.getSuppressed()[0]);
        assertSame(rollbackFailure, thrown.getSuppressed()[0].getCause());
        verify(connection, never()).commit();
        verify(connection).close();
    }

    @Test
    void shouldRollbackOuterTransactionWhenNestedCallbackThrowsError() throws Exception {
        AgroalDataSource dataSource = mock(AgroalDataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        AbstractHandleFactory factory = factory(dataSource);
        HandleCallback<Void, RuntimeException> nested = handle -> {
            throw new StackOverflowError("nested callback failed");
        };
        // The outer callback swallows the nested failure and returns normally, so only the nested
        // call can have marked the shared handle for rollback.
        HandleCallback<String, RuntimeException> outer = handle -> {
            try {
                factory.withHandle(nested);
            } catch (StackOverflowError e) {
                return "nested call failed";
            }
            return "nested call succeeded";
        };

        assertEquals("nested call failed", factory.withHandle(outer));

        verify(dataSource, times(1)).getConnection();
        verify(connection, times(1)).rollback();
        verify(connection, never()).commit();
        verify(connection, times(1)).close();
    }

    @Test
    void shouldCloseConnectionWhenDisablingAutoCommitFails() throws Exception {
        AgroalDataSource dataSource = mock(AgroalDataSource.class);
        Connection failedConnection = mock(Connection.class);
        Connection nextConnection = mock(Connection.class);
        SQLException setupFailure = new SQLException("setAutoCommit failed");
        when(dataSource.getConnection()).thenReturn(failedConnection, nextConnection);
        doThrow(setupFailure).when(failedConnection).setAutoCommit(false);
        AbstractHandleFactory factory = factory(dataSource);
        HandleCallback<String, RuntimeException> callback = handle -> "done";

        RegistryStorageException thrown = assertThrows(RegistryStorageException.class,
                () -> factory.withHandle(callback));

        assertSame(setupFailure, thrown.getCause());
        verify(failedConnection).close();
        verify(failedConnection, never()).rollback();
        verify(failedConnection, never()).commit();
        assertEquals("done", factory.withHandle(callback));
        verify(nextConnection).commit();
        verify(nextConnection).close();
    }

    @Test
    void shouldKeepAutoCommitFailureWhenCloseAlsoFails() throws Exception {
        AgroalDataSource dataSource = mock(AgroalDataSource.class);
        Connection connection = mock(Connection.class);
        SQLException setupFailure = new SQLException("setAutoCommit failed");
        SQLException closeFailure = new SQLException("close failed");
        when(dataSource.getConnection()).thenReturn(connection);
        doThrow(setupFailure).when(connection).setAutoCommit(false);
        doThrow(closeFailure).when(connection).close();
        AbstractHandleFactory factory = factory(dataSource);
        HandleCallback<String, RuntimeException> callback = handle -> "done";

        RegistryStorageException thrown = assertThrows(RegistryStorageException.class,
                () -> factory.withHandle(callback));

        assertSame(setupFailure, thrown.getCause());
        assertEquals(1, setupFailure.getSuppressed().length);
        assertSame(closeFailure, setupFailure.getSuppressed()[0]);
    }

    @Test
    void shouldCloseConnectionAndResetHandleWhenCommitThrowsError() throws Exception {
        AgroalDataSource dataSource = mock(AgroalDataSource.class);
        Connection firstConnection = mock(Connection.class);
        Connection secondConnection = mock(Connection.class);
        StackOverflowError commitFailure = new StackOverflowError("commit failed");
        when(dataSource.getConnection()).thenReturn(firstConnection, secondConnection);
        doThrow(commitFailure).when(firstConnection).commit();
        AbstractHandleFactory factory = factory(dataSource);
        HandleCallback<String, RuntimeException> callback = handle -> "done";

        StackOverflowError thrown = assertThrows(StackOverflowError.class,
                () -> factory.withHandle(callback));

        assertSame(commitFailure, thrown);
        verify(firstConnection).close();
        assertEquals("done", factory.withHandle(callback));
        verify(dataSource, times(2)).getConnection();
        verify(secondConnection).commit();
        verify(secondConnection).close();
    }

    private AbstractHandleFactory factory(AgroalDataSource dataSource) {
        ConnectionRetryConfig config = new ConnectionRetryConfig();
        config.enabled = false;
        // The handle state is a static ThreadLocal keyed by data source id. A unique id keeps a handle
        // that one test leaves behind from leaking into the next test on the same thread.
        String dataSourceId = UUID.randomUUID().toString();
        return new AbstractHandleFactory() {
            {
                initialize(dataSource, dataSourceId, mock(Logger.class), config);
            }
        };
    }
}
