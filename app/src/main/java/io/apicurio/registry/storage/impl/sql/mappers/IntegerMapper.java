package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class IntegerMapper implements RowMapper<Integer> {

    public static final IntegerMapper instance = new IntegerMapper();

    /**
     * Constructor.
     */
    private IntegerMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public Integer map(ResultSet rs) throws SQLException {
        return rs.getInt(1);
    }

}