package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.impl.sql.jdb.HandleAction;
import io.apicurio.registry.storage.impl.sql.jdb.HandleCallback;

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
}
