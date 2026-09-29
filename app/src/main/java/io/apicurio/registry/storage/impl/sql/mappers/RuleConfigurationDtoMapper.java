package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.RuleAction;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

public class RuleConfigurationDtoMapper implements RowMapper<RuleConfigurationDto> {

    public static final RuleConfigurationDtoMapper instance = new RuleConfigurationDtoMapper();

    private static final Logger log = LoggerFactory.getLogger(RuleConfigurationDtoMapper.class);

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
        String onFailure = rs.getString("onFailure");
        RuleAction action = RuleConfigurationDto.parseOnFailure(onFailure);
        if (onFailure != null && !action.name().equals(onFailure)) {
            log.warn("Invalid rule onFailure value '{}'; defaulting to ERROR", onFailure);
        }
        dto.setOnFailure(action);
        return dto;
    }

}
