package io.apicurio.registry.storage.impl.sql.mappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;

public class WebhookSubscriptionDtoMapper implements RowMapper<WebhookSubscriptionDto> {

    public static final WebhookSubscriptionDtoMapper instance = new WebhookSubscriptionDtoMapper();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private WebhookSubscriptionDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public WebhookSubscriptionDto map(ResultSet rs) throws SQLException {
        return WebhookSubscriptionDto.builder().subscriptionId(rs.getString("subscriptionId"))
                .name(rs.getString("name")).endpointUrl(rs.getString("endpointUrl"))
                .eventTypes(deserializeEventTypes(rs.getString("eventTypes")))
                .groupFilter(rs.getString("groupFilter")).artifactIdFilter(rs.getString("artifactIdFilter"))
                .enabled(rs.getBoolean("enabled")).secret(rs.getString("secret"))
                .createdBy(rs.getString("createdBy")).createdOn(rs.getTimestamp("createdOn"))
                .modifiedOn(rs.getTimestamp("modifiedOn")).build();
    }

    private Set<StorageEventType> deserializeEventTypes(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new RegistryStorageException("Failed to deserialize webhook subscription event types", e);
        }
    }
}
