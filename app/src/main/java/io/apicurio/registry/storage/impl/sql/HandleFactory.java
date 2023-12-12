package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.jdb.HandleCallback;

public interface HandleFactory {

    /**
     * Execute an operation using a database handle.
     * <p>
     * Handles are cached and reused if calls to this method are nested. Make sure that all nested uses of a
     * handle are either within a transaction context, or without one. Starting a transaction with a nested
     * handle will cause an exception.
     */
    <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X;

    <R, X extends Exception> R withHandleNoException(HandleCallback<R, X> callback)
            throws RegistryStorageException;

    // TODO Add "action" version of these that do not return a value
}
