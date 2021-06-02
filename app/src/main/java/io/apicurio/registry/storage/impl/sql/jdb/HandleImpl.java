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
     * @param connection
     */
    public HandleImpl(Connection connection) {
        this.connection = connection;
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
     * @see io.apicurio.registry.storage.impl.sql.jdb.Handle#createQuery(java.lang.String)
     */
    @Override
    public Query createQuery(String sql) {
        QueryImpl query = new QueryImpl(connection, sql);
        return query;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.Handle#createUpdate(java.lang.String)
     */
    @Override
    public Update createUpdate(String sql) {
        UpdateImpl update = new UpdateImpl(connection, sql);
        return update;
    }

}
