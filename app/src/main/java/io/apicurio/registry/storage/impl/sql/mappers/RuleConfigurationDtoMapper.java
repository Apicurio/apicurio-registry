package io.apicurio.registry.storage.impl.sql.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;


public class RuleConfigurationDtoMapper implements RowMapper<RuleConfigurationDto> {

    public static final RuleConfigurationDtoMapper instance = new RuleConfigurationDtoMapper();

    /**
     * Constructor.
     */
    private RuleConfigurationDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public RuleConfigurationDto map(ResultSet rs) throws SQLException {
        RuleConfigurationDto dto = new RuleConfigurationDto();
        dto.setConfiguration(rs.getString("configuration"));
        return dto;
    }

}