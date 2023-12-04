/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.HandleCallback;
import io.apicurio.registry.storage.impl.sql.jdb.HandleImpl;
import org.slf4j.Logger;

import java.io.IOException;
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
        /*
         * Handles are cached and reused if calls to this method are nested.
         * Make sure that all nested uses of a handle are either within a transaction context,
         * or without one. Starting a transaction with a nested handle will cause an exception.
         */
        try {
            if (get().handle == null) {
                get().handle = new HandleImpl(dataSource.getConnection());
            } else {
                get().level++;
            }
            return callback.withHandle(get().handle);
        } catch (SQLException e) {
            throw new RegistryStorageException(e);
        } finally {
            if (get().level > 0) {
                get().level--;
            } else {
                try {
                    LocalState partialState = get();
                    if (partialState.handle != null) {
                        partialState.handle.close();
                    }
                } catch (IOException ex) {
                    // Nothing we can do
                    log.error("Could not close a database handle", ex);
                } finally {
                    local.get().remove(dataSourceId);
                }
            }
        }
    }


    @Override
    public <R, X extends Exception> R withHandleNoException(HandleCallback<R, X> callback) throws RegistryStorageException {
        try {
            return withHandle(callback);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }


    private LocalState get() {
        return local.get().computeIfAbsent(dataSourceId, k -> new LocalState());
    }


    private static class LocalState {

        Handle handle;

        int level;
    }
}
