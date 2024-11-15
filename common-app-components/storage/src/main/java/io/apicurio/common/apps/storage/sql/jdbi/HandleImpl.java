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

import io.apicurio.common.apps.storage.sql.jdbi.query.Query;
import io.apicurio.common.apps.storage.sql.jdbi.query.QueryImpl;
import io.apicurio.common.apps.storage.sql.jdbi.query.Update;
import io.apicurio.common.apps.storage.sql.jdbi.query.UpdateImpl;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author eric.wittmann@gmail.com
 */
public class HandleImpl implements Handle {

    private final Connection connection;

    /**
     * Constructor.
     *
     * @param connection a DB connection
     */
    public HandleImpl(Connection connection) {
        this.connection = connection;
    }

    public Connection getConnection() {
        return connection;
    }

    /**
     * @see java.io.Closeable#close()
     */
    @Override
    public void close() throws IOException {
        try {
            this.connection.close();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * @see io.apicurio.common.apps.storage.sql.jdbi.Handle#createQuery(java.lang.String)
     */
    @Override
    public Query createQuery(String sql) {
        return new QueryImpl(connection, sql);
    }

    /**
     * @see io.apicurio.common.apps.storage.sql.jdbi.Handle#createUpdate(java.lang.String)
     */
    @Override
    public Update createUpdate(String sql) {
        return new UpdateImpl(connection, sql);
    }
}
