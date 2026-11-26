package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.jdb.HandleAction;
import io.apicurio.registry.storage.impl.sql.jdb.HandleCallback;
import io.apicurio.registry.storage.impl.sql.jdb.HandleImpl;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractHandleFactory implements HandleFactory {

    private static final ThreadLocal<Map<String, LocalState>> local = ThreadLocal.withInitial(HashMap::new);

    private AgroalDataSource dataSource;

    private String dataSourceId;

    private Logger log;

    private ConnectionRetryConfig retryConfig;

    protected void initialize(AgroalDataSource dataSource, String dataSourceId, Logger log,
            ConnectionRetryConfig retryConfig) {
        // CDI error if there is no no-args constructor
        this.dataSource = dataSource;
        this.dataSourceId = dataSourceId;
        this.log = log;
        this.retryConfig = retryConfig;
    }

    @Override
    public <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X {
        LocalState state = state();
        R result = null;
        Throwable callbackFailure = null;
        RuntimeException transactionFailure;
        try {
            // Create a new handle if necessary. Increment the "level" if a handle already exists.
            if (state.handle == null) {
                state.handle = new HandleImpl(openConnection());
                state.level = 0;
            } else {
                state.level++;
            }

            // Invoke the callback with the handle. This will either return a value (success)
            // or throw some sort of exception.
            result = callback.withHandle(state.handle);
        } catch (SQLException e) {
            // If a SQL exception is thrown, set the handle to rollback.
            if (state.handle != null) {
                state.handle.setRollback(true);
                log.debug("Transaction will rollback.", e);
            }
            // Wrap the SQL exception.
            RegistryStorageException wrapped = new RegistryStorageException(e);
            callbackFailure = wrapped;
        } catch (Exception | Error e) {
            // If any other exception, or an Error such as StackOverflowError, is thrown, also set the
            // handle to rollback. The callback did not finish, so its work must not be committed.
            if (state.handle != null) {
                state.handle.setRollback(true);
                log.debug("Transaction will rollback.", e);
            }
            callbackFailure = e;
        } finally {
            transactionFailure = releaseHandle(state);
        }

        if (transactionFailure != null) {
            if (callbackFailure != null) {
                callbackFailure.addSuppressed(transactionFailure);
            } else {
                throw transactionFailure;
            }
        }
        if (callbackFailure instanceof Error error) {
            throw error;
        }
        if (callbackFailure != null) {
            @SuppressWarnings("unchecked")
            X failure = (X) callbackFailure;
            throw failure;
        }
        return result;
    }

    private Connection openConnection() throws SQLException {
        Connection connection = ConnectionRetryUtil.getConnectionWithRetry(dataSource, retryConfig, log);
        try {
            // We must disable autocommit since we're managing the transactions ourselves.
            connection.setAutoCommit(false);
            return connection;
        } catch (Throwable e) {
            // No handle holds this connection yet, so releaseHandle cannot close it.
            try {
                connection.close();
            } catch (Exception closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private RuntimeException releaseHandle(LocalState state) {
        if (state.level > 0) {
            log.trace("Exiting nested call (level {}): {} #{}", state.level,
                    state.handle.getConnection(), state.handle.getConnection().hashCode());
            state.level--;
            return null;
        }

        RuntimeException transactionFailure = null;
        try {
            if (state.handle != null) {
                if (state.handle.isRollback()) {
                    log.trace("Rollback: {} #{}", state.handle.getConnection(),
                            state.handle.getConnection().hashCode());
                    state.handle.getConnection().rollback();
                } else {
                    log.trace("Commit: {} #{}", state.handle.getConnection(),
                            state.handle.getConnection().hashCode());
                    state.handle.getConnection().commit();
                }
            }
        } catch (Exception e) {
            log.error("Could not release database connection/transaction", e);
            transactionFailure = new RegistryStorageException(
                    "Could not release database connection/transaction.", e);
        } finally {
            // Also runs when commit or rollback throws an Error. A handle left in place would make
            // every later call on this thread join it as a nested call, which never commits.
            closeHandle(state);
        }
        return transactionFailure;
    }

    private void closeHandle(LocalState state) {
        try {
            if (state.handle != null) {
                state.handle.close();
            }
        } catch (Exception ex) {
            // Nothing we can do
            log.error("Could not close a database connection.", ex);
        } finally {
            state.handle = null;
            state.level = 0;
        }
    }

    @Override
    public <R, X extends Exception> R withHandleNoException(HandleCallback<R, X> callback) {
        try {
            return withHandle(callback);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    @Override
    public <X extends Exception> void withHandleNoException(HandleAction<X> action) {
        withHandleNoException(handle -> {
            action.withHandle(handle);
            return null;
        });
    }

    private LocalState state() {
        return local.get().computeIfAbsent(dataSourceId, k -> new LocalState());
    }

    private static class LocalState {
        HandleImpl handle;
        int level = 0;
    }
}
