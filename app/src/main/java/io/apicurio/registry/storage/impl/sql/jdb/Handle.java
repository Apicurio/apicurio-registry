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

    /**
     * Runs the callback under a JDBC savepoint of this handle's transaction, as if it were a transaction of
     * its own:
     * <ul>
     * <li>If the callback throws an exception, the transaction is rolled back to the savepoint, the rollback
     * status is restored to its value before the callback ran, and the exception is rethrown. The caller can
     * then catch an expected failure (e.g. a unique constraint violation) and keep using the transaction,
     * which on PostgreSQL would otherwise be aborted by the failed statement. Nested storage calls mark the
     * handle for rollback when they fail; restoring the status keeps such a handled failure from rolling
     * back the whole transaction.</li>
     * <li>If the callback returns but marked the handle for rollback (e.g. a dry run), only its work is
     * rolled back, and the status is restored.</li>
     * <li>If the callback throws an {@link Error}, or the savepoint cannot be set, released or rolled back
     * to, the whole transaction is marked for rollback.</li>
     * </ul>
     *
     * @param callback the work to run under the savepoint
     */
    <R, X extends Exception> R inSavepoint(HandleCallback<R, X> callback) throws X;
}
