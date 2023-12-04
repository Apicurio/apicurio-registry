package io.apicurio.registry.storage.impl.sql.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;


public class StringMapper implements RowMapper<String> {

    public static final StringMapper instance = new StringMapper();

    /**
     * Constructor.
     */
    private StringMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public String map(ResultSet rs) throws SQLException {
        return rs.getString(1);
    }

}