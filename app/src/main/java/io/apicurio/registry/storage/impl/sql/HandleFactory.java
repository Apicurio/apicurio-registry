package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.impl.sql.jdb.HandleAction;
import io.apicurio.registry.storage.impl.sql.jdb.HandleCallback;

import java.util.function.Supplier;

public interface HandleFactory {

    /**
     * Execute an operation using a database handle.
     * <p>
     * The outermost call represents a transaction boundary, and the handle factory performs commit or
     * rollback automatically. Auto-commit in JDBC has been turned off.
     * <p>
     * Rollback is performed when an exception is thrown to the outside of this method call.
     * <p>
     * WARNING: This method must not be executed within an existing transaction.
     */
    <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X;

    /**
     * This method extends {@see HandleFactory#withHandle(HandleCallback)} to automatically wrap checked
     * exceptions.
     */
    <R, X extends Exception> R withHandleNoException(HandleCallback<R, X> callback);

    /**
     * This method extends {@see HandleFactory#withHandle(HandleCallback)} to automatically wrap checked
     * exceptions, and is suitable when no value is being returned.
     */
    <X extends Exception> void withHandleNoException(HandleAction<X> callback);

    /**
     * Runs the work as one unit of work: one transaction that every handle call made by the work, on this
     * thread, joins.
     * <ul>
     * <li>Each handle call made directly inside the unit runs under a savepoint (see
     * {@link io.apicurio.registry.storage.impl.sql.jdb.Handle#inSavepoint(HandleCallback)}), so a call that
     * fails, and whose exception the work catches, has its own changes rolled back without rolling back the
     * unit or leaving the transaction aborted. Calls nested deeper keep the usual behaviour.</li>
     * <li>An exception escaping the outermost unit rolls back everything; otherwise everything commits
     * together.</li>
     * <li>A unit inside another unit is one call of the outer unit: it runs under a savepoint, and its own
     * calls run under savepoints too.</li>
     * </ul>
     * Outside units, handle calls behave as before. The work must stay on the calling thread.
     */
    <R> R inUnitOfWork(Supplier<R> work);
}
