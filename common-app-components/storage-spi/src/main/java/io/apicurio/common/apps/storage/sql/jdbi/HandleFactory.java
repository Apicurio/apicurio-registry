package io.apicurio.common.apps.storage.sql.jdbi;

import io.apicurio.common.apps.storage.exceptions.StorageException;

/**
 * @author eric.wittmann@gmail.com
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public interface HandleFactory {

    <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X, StorageException;

    <R, X extends Exception> R withHandleNoExceptionMapped(HandleCallback<R, X> callback);
}
