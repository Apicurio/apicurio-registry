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
     * True when the transaction could not be rolled back to a savepoint (e.g. the database already rolled
     * the whole transaction back after a deadlock). Work that continued after that point is lost, so the
     * outermost call must fail rather than end quietly.
     */
    @Getter
    private boolean savepointLost;

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
            // Without a savepoint a failure here could not be undone on its own.
            rollback = true;
            throw new RuntimeSqlException(e);
        }
        boolean rollbackBefore = rollback;
        // Cleared so that a request to roll back made inside the scope (a failed nested call, or a dry
        // run) can be told apart from one made before it.
        rollback = false;
        R result;
        try {
            result = callback.withHandle(this);
        } catch (Exception e) {
            if (rollbackTo(savepoint, e)) {
                rollback = rollbackBefore;
            }
            throw e;
        } catch (Error e) {
            rollback = true;
            throw e;
        }
        if (rollback) {
            // The work asked to be rolled back (e.g. a dry run): undo only this scope.
            rollbackTo(savepoint, null);
            rollback = rollbackBefore;
            return result;
        }
        rollback = rollbackBefore;
        try {
            connection.releaseSavepoint(savepoint);
        } catch (SQLFeatureNotSupportedException e) {
            // SQL Server does not release savepoints; they end with the transaction.
        } catch (SQLException e) {
            rollback = true;
            throw new RuntimeSqlException(e);
        }
        return result;
    }

    /**
     * Rolls back to the savepoint. On failure the transaction is marked for rollback; the failure is
     * added to the given exception, or thrown when there is none. Returns true when the rollback worked.
     */
    private boolean rollbackTo(Savepoint savepoint, Exception failure) {
        try {
            connection.rollback(savepoint);
            return true;
        } catch (SQLException rollbackFailure) {
            // The transaction is in an unknown state, so it must not be committed.
            rollback = true;
            savepointLost = true;
            if (failure != null) {
                failure.addSuppressed(rollbackFailure);
                return false;
            }
            throw new RuntimeSqlException(rollbackFailure);
        }
    }
}
