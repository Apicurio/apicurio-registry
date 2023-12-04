package io.apicurio.registry.storage.impl.sql.jdb;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;


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
