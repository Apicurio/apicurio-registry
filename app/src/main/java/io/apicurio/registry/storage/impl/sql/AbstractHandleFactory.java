package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.jdb.HandleAction;
import io.apicurio.registry.storage.impl.sql.jdb.HandleCallback;
import io.apicurio.registry.storage.impl.sql.jdb.HandleImpl;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractHandleFactory implements HandleFactory {

    private static final ThreadLocal<Map<String, LocalState>> local = ThreadLocal.withInitial(HashMap::new);

    private AgroalDataSource dataSource;

    private String dataSourceId;

    private Logger log;

    protected void initialize(AgroalDataSource dataSource, String dataSourceId, Logger log) {
        // CDI error if there is no no-args constructor
        this.dataSource = dataSource;
        this.dataSourceId = dataSourceId;
        this.log = log;
    }

    @Override
    public <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X {
        LocalState state = state();
        try {
            // Create a new handle if necessary. Increment the "level" if a handle already exists.
            if (state.handle == null) {
                state.handle = new HandleImpl(dataSource.getConnection());
                state.level = 0;
            } else {
                state.level++;
            }

            // Invoke the callback with the handle. This will either return a value (success)
            // or throw some sort of exception.
            return callback.withHandle(state.handle);
        } catch (SQLException e) {
            // If a SQL exception is thrown, set the handle to rollback.
            if (state.handle != null) {
                state.handle.setRollback(true);
            }
            // Wrap the SQL exception.
            throw new RegistryStorageException(e);
        } catch (Exception e) {
            // If any other exception is thrown, also set the handle to rollback.
            if (state.handle != null) {
                state.handle.setRollback(true);
            }
            throw e;
        } finally {
            if (state.level > 0) {
                log.trace("Exiting nested call (level {}): {} #{}", state().level,
                        state().handle.getConnection(), state().handle.getConnection().hashCode());
                state.level--;
            } else {
                // Commit or rollback the transaction
                try {
                    if (state.handle != null) {
                        if (state.handle.isRollback()) {
                            log.trace("Rollback: {} #{}", state.handle.getConnection(),
                                    state.handle.getConnection().hashCode());
                            state.handle.getConnection().rollback();
                        } else {
                            log.trace("Commit: {} #{}", state.handle.getConnection(),
                                    state.handle.getConnection().hashCode());
                            state().handle.getConnection().commit();
                        }
                    }
                } catch (Exception e) {
                    log.error("Could not release database connection/transaction", e);
                }

                // Close the connection
                try {
                    if (state.handle != null) {
                        state.handle.close();
                        state.handle = null;
                        state.level = 0;
                    }
                } catch (Exception ex) {
                    // Nothing we can do
                    log.error("Could not close a database connection.", ex);
                }
            }
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
