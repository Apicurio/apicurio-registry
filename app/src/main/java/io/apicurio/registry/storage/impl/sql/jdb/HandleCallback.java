package io.apicurio.registry.storage.impl.sql.jdb;


@FunctionalInterface
public interface HandleCallback<T, X extends Exception> {

    /**
     * Will be invoked with an open Handle. The handle may be closed when this callback returns.
     *
     * @param handle Handle to be used only within scope of this callback
     * @return The return value of the callback
     * @throws X optional exception thrown by the callback
     */
    T withHandle(Handle handle) throws X;

}