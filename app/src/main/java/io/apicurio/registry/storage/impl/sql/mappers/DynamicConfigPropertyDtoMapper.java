package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DynamicConfigPropertyDtoMapper implements RowMapper<DynamicConfigPropertyDto> {

    public static final DynamicConfigPropertyDtoMapper instance = new DynamicConfigPropertyDtoMapper();

    /**
     * Constructor.
     */
    private DynamicConfigPropertyDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public DynamicConfigPropertyDto map(ResultSet rs) throws SQLException {
        String name = rs.getString("pname");
        String value = rs.getString("pvalue");
        return new DynamicConfigPropertyDto(name, value);
    }

}