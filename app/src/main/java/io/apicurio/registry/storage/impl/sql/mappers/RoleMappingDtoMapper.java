package io.apicurio.registry.storage.impl.sql.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.apicurio.registry.storage.dto.RoleMappingDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;


public class RoleMappingDtoMapper implements RowMapper<RoleMappingDto> {

    public static final RoleMappingDtoMapper instance = new RoleMappingDtoMapper();

    /**
     * Constructor.
     */
    private RoleMappingDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public RoleMappingDto map(ResultSet rs) throws SQLException {
        RoleMappingDto dto = new RoleMappingDto();
        dto.setPrincipalId(rs.getString("principalId"));
        dto.setRole(rs.getString("role"));
        dto.setPrincipalName(rs.getString("principalName"));
        return dto;
    }

}