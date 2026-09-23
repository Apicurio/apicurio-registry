package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.VersionState;

import java.sql.ResultSet;
import java.sql.SQLException;

public class VersionStateMapper implements RowMapper<VersionState> {

    public static final VersionStateMapper instance = new VersionStateMapper();

    /**
     * Constructor.
     */
    private VersionStateMapper() {
    }

    /**
     * @see RowMapper#map(ResultSet)
     */
    @Override
    public VersionState map(ResultSet rs) throws SQLException {
        return VersionState.fromValue(rs.getString("state"));
    }

}