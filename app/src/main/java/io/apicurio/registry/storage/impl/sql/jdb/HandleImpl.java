package io.apicurio.registry.storage.impl.sql.jdb;

import lombok.Getter;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Savepoint;

public class HandleImpl implements Handle {

    @Getter
    private final Connection connection;
    @Getter
    private boolean rollback;

    /**
     * Constructor.
     * 
     * @param connection
     */
    public HandleImpl(Connection connection) {
        this.connection = connection;
    }

    /**
     * @see java.io.Closeable#close()
     */
    @Override
    public void close() throws IOException {
        try {
            this.connection.close();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.Handle#createQuery(java.lang.String)
     */
    @Override
    public Query createQuery(String sql) {
        QueryImpl query = new QueryImpl(connection, sql);
        return query;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.Handle#createUpdate(java.lang.String)
     */
    @Override
    public Update createUpdate(String sql) {
        UpdateImpl update = new UpdateImpl(connection, sql);
        return update;
    }

    @Override
    public void setRollback(boolean rollback) {
        this.rollback = rollback;
    }

    @Override
    public <R, X extends Exception> R inSavepoint(HandleCallback<R, X> callback) throws X {
        Savepoint savepoint;
        try {
            savepoint = connection.setSavepoint();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
        boolean rollbackBefore = rollback;
        R result;
        try {
            result = callback.withHandle(this);
        } catch (Exception e) {
            try {
                connection.rollback(savepoint);
            } catch (SQLException rollbackFailure) {
                // The transaction is in an unknown state, so it must not be committed.
                rollback = true;
                e.addSuppressed(rollbackFailure);
                throw e;
            }
            rollback = rollbackBefore;
            throw e;
        }
        try {
            connection.releaseSavepoint(savepoint);
        } catch (SQLFeatureNotSupportedException e) {
            // SQL Server does not release savepoints; they end with the transaction.
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
        return result;
    }
}
