package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class LongMapper implements RowMapper<Long> {

    public static final LongMapper instance = new LongMapper();

    /**
     * Constructor.
     */
    private LongMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public Long map(ResultSet rs) throws SQLException {
        return rs.getLong(1);
    }

}