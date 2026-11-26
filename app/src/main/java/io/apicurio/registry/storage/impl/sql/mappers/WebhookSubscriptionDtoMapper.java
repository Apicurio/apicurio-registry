package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WebhookSubscriptionDtoMapper implements RowMapper<WebhookSubscriptionDto> {

    public static final WebhookSubscriptionDtoMapper instance = new WebhookSubscriptionDtoMapper();

    private WebhookSubscriptionDtoMapper() {
    }

    @Override
    public WebhookSubscriptionDto map(ResultSet rs) throws SQLException {
        WebhookSubscriptionDto dto = new WebhookSubscriptionDto();
        dto.setSubscriptionId(rs.getString("subscriptionId"));
        dto.setName(rs.getString("name"));
        dto.setOwnerId(rs.getString("ownerId"));
        dto.setEndpointUrl(rs.getString("endpointUrl"));
        dto.setEventTypes(rs.getString("eventTypes"));
        dto.setGroupFilter(rs.getString("groupFilter"));
        dto.setArtifactIdFilter(rs.getString("artifactIdFilter"));
        dto.setArtifactTypeFilter(rs.getString("artifactTypeFilter"));
        dto.setEnabled(rs.getBoolean("enabled"));
        long deletedOn = rs.getLong("deletedOn");
        dto.setDeletedOn(rs.wasNull() ? null : deletedOn);
        dto.setRevision(rs.getLong("revision"));
        dto.setSigningSecretRef(rs.getString("signingSecretRef"));
        dto.setCreatedOn(rs.getLong("createdOn"));
        dto.setModifiedOn(rs.getLong("modifiedOn"));
        return dto;
    }

}
