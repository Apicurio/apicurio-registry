package io.apicurio.registry.storage.impl.sql.jdb;

import java.io.Closeable;
import java.sql.SQLException;

public interface Handle extends Closeable {

    /**
     * Create a new Query from the given SQL.
     * 
     * @param sql
     */
    Query createQuery(String sql);

    /**
     * Create a new Update statement from the given SQL.
     * 
     * @param sql
     */
    Update createUpdate(String sql);

    /**
     * Set the rollback status of this handle. If set to true, the database transaction will be rolled back
     * instead of commited.
     * 
     * @param rollback
     */
    void setRollback(boolean rollback);

    /**
     * Commits the current database transaction immediately, without waiting for the handle to be released.
     * Used when work must be durable before a subsequent step on the same connection (for example, before
     * releasing a session-level lock).
     */
    void commit() throws SQLException;

    /**
     * Rolls back the current database transaction immediately, without waiting for the handle to be
     * released.
     */
    void rollback() throws SQLException;
}
