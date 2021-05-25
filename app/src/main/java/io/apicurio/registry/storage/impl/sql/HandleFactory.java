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

import java.sql.Connection;
import java.sql.SQLException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.agroal.api.AgroalDataSource;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.HandleCallback;
import io.apicurio.registry.storage.impl.sql.jdb.HandleImpl;
import io.apicurio.registry.types.RegistryException;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class HandleFactory {

    @Inject
    AgroalDataSource dataSource;

    public <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X {
        try (Connection connection = dataSource.getConnection()) {
            Handle handleImpl = new HandleImpl(connection);
            return callback.withHandle(handleImpl);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public <R, X extends Exception> R withHandleNoException(HandleCallback<R, X> callback)  throws RegistryStorageException {
        try {
            return withHandle(callback);
        } catch (RegistryException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

}
