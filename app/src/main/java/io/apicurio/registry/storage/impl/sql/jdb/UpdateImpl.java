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

package io.apicurio.registry.storage.impl.sql.jdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @author eric.wittmann@gmail.com
 */
public class UpdateImpl extends SqlImpl<Update> implements Update {

    /**
     * Constructor.
     * @param connection
     * @param sql
     */
    public UpdateImpl(Connection connection, String sql) {
        super(connection, sql);
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.Update#execute()
     */
    @Override
    public int execute() {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParametersTo(statement);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.Update#executeNoUpdate()
     */
    @Override
    public void executeNoUpdate() {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParametersTo(statement);
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

}
