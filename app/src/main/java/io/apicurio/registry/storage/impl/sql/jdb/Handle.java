package io.apicurio.registry.storage.impl.sql.jdb;

import java.io.Closeable;

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
}
