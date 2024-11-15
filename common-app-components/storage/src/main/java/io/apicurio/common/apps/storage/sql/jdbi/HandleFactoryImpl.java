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

package io.apicurio.common.apps.storage.sql.jdbi;

import io.agroal.api.AgroalDataSource;
import io.apicurio.common.apps.storage.exceptions.AlreadyExistsException;
import io.apicurio.common.apps.storage.exceptions.StorageException;
import io.apicurio.common.apps.storage.exceptions.StorageExceptionMapper;
import io.apicurio.common.apps.storage.sql.SqlStatements;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class HandleFactoryImpl implements HandleFactory {

    @Inject
    @Named("apicurioDatasource")
    AgroalDataSource dataSource;

    @Inject
    @Named("apicurioSqlStatements")
    SqlStatements sqlStatements;

    @Inject
    StorageExceptionMapper exceptionMapper;

    private <R, X extends Exception> R withHandleRaw(HandleCallback<R, X> callback)
            throws X, StorageException {
        try (Connection connection = dataSource.getConnection()) {
            Handle handleImpl = new HandleImpl(connection);
            return callback.withHandle(handleImpl);
        } catch (SQLException e) {
            throw new StorageException(null, e);
        }
    }

    @Override
    public <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X, StorageException {
        try {
            return withHandleRaw(callback);
        } catch (StorageException ex) {
            if (ex.isRoot() && ex.getCause() instanceof SQLException) {
                var sqlEx = (SQLException) ex.getCause();
                if (sqlStatements.isPrimaryKeyViolation(sqlEx)
                        || sqlStatements.isForeignKeyViolation(sqlEx)) {
                    throw new AlreadyExistsException(ex.getContext().orElse(null), sqlEx);
                } else {
                    throw ex;
                }
            } else {
                throw ex;
            }
        }
    }

    @Override
    public <R, X extends Exception> R withHandleNoExceptionMapped(HandleCallback<R, X> callback) {
        try {
            return withHandle(callback);
        } catch (StorageException ex) {
            if (ex.isRoot() && ex.getCause() instanceof SQLException) {
                var sqlEx = (SQLException) ex.getCause();
                if (sqlStatements.isPrimaryKeyViolation(sqlEx)
                        || sqlStatements.isForeignKeyViolation(sqlEx)) {
                    throw exceptionMapper
                            .map(new AlreadyExistsException(ex.getContext().orElse(null), sqlEx));
                } else {
                    throw exceptionMapper.map(ex);
                }
            } else {
                throw exceptionMapper.map(ex);
            }
        } catch (Exception ex) {
            throw exceptionMapper.map(new StorageException(null, ex));
        }
    }
}
