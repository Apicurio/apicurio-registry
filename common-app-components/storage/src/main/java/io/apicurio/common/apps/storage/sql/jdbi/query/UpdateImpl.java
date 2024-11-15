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

package io.apicurio.common.apps.storage.sql.jdbi.query;

import io.apicurio.common.apps.storage.exceptions.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @author eric.wittmann@gmail.com
 */
public class UpdateImpl extends SqlImpl<Update> implements Update {

    /**
     * Constructor.
     *
     * @param connection a DB connection
     * @param sql some SQL statement(s)
     */
    public UpdateImpl(Connection connection, String sql) {
        super(connection, sql);
    }

    public UpdateImpl(String sql) {
        super(sql);
    }

    /**
     * @see Update#execute()
     */
    @Override
    public int execute() throws StorageException {
        try (PreparedStatement statement = connection.orElseThrow(() -> new StorageException(
                "Handle not set. Use setHandleOnce(...) to set the Handle before executing the query.", null))
                .prepareStatement(sql)) {
            bindParametersTo(statement);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException(context, e);
        }
    }

    /**
     * @see Update#executeNoUpdate()
     */
    @Override
    public void executeNoUpdate() throws StorageException {
        try (PreparedStatement statement = connection.orElseThrow(() -> new StorageException(
                "Handle not set. Use setHandleOnce(...) to set the Handle before executing the query.", null))
                .prepareStatement(sql)) {
            bindParametersTo(statement);
            statement.execute();
        } catch (SQLException e) {
            throw new StorageException(context, e);
        }
    }
}
