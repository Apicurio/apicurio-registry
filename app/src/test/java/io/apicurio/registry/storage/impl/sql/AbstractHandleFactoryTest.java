package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.jdb.HandleCallback;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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

        RegistryStorageException thrown = assertThrows(RegistryStorageException.class,
                () -> factory(dataSource).withHandle((HandleCallback<Void, RuntimeException>) handle -> null));

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

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> factory(dataSource).withHandle((HandleCallback<Void, RuntimeException>) handle -> {
                    throw callbackFailure;
                }));

        assertSame(callbackFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(rollbackFailure, thrown.getSuppressed()[0].getCause());
        verify(connection).rollback();
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

    private AbstractHandleFactory factory(AgroalDataSource dataSource) {
        ConnectionRetryConfig config = new ConnectionRetryConfig();
        config.enabled = false;
        return new AbstractHandleFactory() {
            {
                initialize(dataSource, "test", mock(Logger.class), config);
            }
        };
    }
}
