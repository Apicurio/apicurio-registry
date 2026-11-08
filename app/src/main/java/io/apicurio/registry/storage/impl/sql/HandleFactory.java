package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.impl.sql.jdb.HandleAction;
import io.apicurio.registry.storage.impl.sql.jdb.HandleCallback;

import java.util.function.Predicate;

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
     * Runs the action in its own transaction, at most {@code maxAttempts} times while it fails with an error
     * the classifier accepts, with a short randomized pause before each retry (see
     * {@link AbstractHandleFactory#withRetryingHandle}), so a retried call can sleep for up to
     * {@code maxAttempts - 1} pauses. When the calling thread already holds a handle the action joins that
     * transaction and runs exactly once, because a failure has already marked that transaction rollback-only
     * and a repeat could not commit.
     */
    <X extends Exception> void withRetryingHandle(HandleAction<X> action, Predicate<RuntimeException> retryable,
            int maxAttempts);

    /**
     * Returns true when the calling thread already holds a handle, so a new action joins that transaction
     * instead of starting its own. A caller that splits work into several transactions uses it to stop at
     * the first failure, since a failure has already made the joined transaction rollback-only.
     */
    boolean isInHandle();
}
