package io.apicurio.common.apps.storage.exceptions;

import io.apicurio.common.apps.storage.sql.jdbi.CallbackEx;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public interface StorageExceptionMapper {

    <E extends RuntimeException> E map(StorageException original);

    default <R, X extends Exception> R with(CallbackEx<R, X> callback) {
        try {
            return callback.call();
        } catch (Exception ex) {
            if (ex instanceof StorageException) {
                throw this.map((StorageException) ex);
            } else {
                throw this.map(new StorageException(null, ex));
            }
        }
    }
}
