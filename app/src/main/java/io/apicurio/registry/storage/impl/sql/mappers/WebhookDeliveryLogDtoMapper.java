package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.WebhookDeliveryLogDto;
import io.apicurio.registry.storage.dto.WebhookDeliveryStatus;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WebhookDeliveryLogDtoMapper implements RowMapper<WebhookDeliveryLogDto> {

    public static final WebhookDeliveryLogDtoMapper instance = new WebhookDeliveryLogDtoMapper();

    private WebhookDeliveryLogDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public WebhookDeliveryLogDto map(ResultSet rs) throws SQLException {
        // wasNull() reflects the most recent column read, so it must be captured immediately.
        int httpStatusCodeValue = rs.getInt("httpStatusCode");
        Integer httpStatusCode = rs.wasNull() ? null : httpStatusCodeValue;
        return WebhookDeliveryLogDto.builder().deliveryId(rs.getString("deliveryId"))
                .subscriptionId(rs.getString("subscriptionId")).eventId(rs.getString("eventId"))
                .eventType(rs.getString("eventType"))
                .status(WebhookDeliveryStatus.valueOf(rs.getString("status")))
                .attemptCount(rs.getInt("attemptCount")).lastAttemptAt(rs.getTimestamp("lastAttemptAt"))
                .nextRetryAt(rs.getTimestamp("nextRetryAt")).errorMessage(rs.getString("errorMessage"))
                .httpStatusCode(httpStatusCode).lockedBy(rs.getString("lockedBy"))
                .leaseUntil(rs.getTimestamp("leaseUntil")).createdOn(rs.getTimestamp("createdOn")).build();
    }
}
