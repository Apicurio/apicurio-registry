package io.apicurio.registry.storage.impl.sql.mappers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class WebhookSubscriptionDtoMapper implements RowMapper<WebhookSubscriptionDto> {

    public static final WebhookSubscriptionDtoMapper instance = new WebhookSubscriptionDtoMapper();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private WebhookSubscriptionDtoMapper() {
    }

    @Override
    public WebhookSubscriptionDto map(ResultSet rs) throws SQLException {
        WebhookSubscriptionDto dto = new WebhookSubscriptionDto();

        dto.setSubscriptionId(rs.getString("subscriptionId"));
        dto.setEndpointUrl(rs.getString("endpointUrl"));
        dto.setEventTypes(readEventTypes(rs.getString("eventTypes")));
        dto.setGroupFilter(rs.getString("groupFilter"));
        dto.setArtifactFilter(rs.getString("artifactFilter"));
        dto.setAuthType(rs.getString("authType"));
        dto.setAuthConfig(rs.getString("authConfig"));
        dto.setEnabled(rs.getBoolean("isEnabled"));
        dto.setOwner(rs.getString("owner"));
        dto.setCreatedOn(rs.getTimestamp("createdOn").getTime());
        dto.setModifiedBy(rs.getString("modifiedBy"));

        if (rs.getTimestamp("modifiedOn") != null) {
            dto.setModifiedOn(rs.getTimestamp("modifiedOn").getTime());
        }

        return dto;
    }

    private List<String> readEventTypes(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new SQLException("Failed to deserialize webhook eventTypes.", e);
        }
    }
}
