package io.apicurio.registry.noprofile.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.dto.WebhookDeliveryLogDto;
import io.apicurio.registry.storage.dto.WebhookDeliveryStatus;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.mappers.WebhookDeliveryLogDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.WebhookSubscriptionDtoMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the DDL for the "webhook_subscriptions" and "webhook_delivery_logs" tables: column types,
 * constraints (PK, FK with cascade, unique deduplication key) and the DTO/row-mapper round trip.
 */
@QuarkusTest
public class WebhookSchemaTest extends AbstractResourceTestBase {

    private static final String INSERT_SUBSCRIPTION = "INSERT INTO webhook_subscriptions "
            + "(subscriptionId, name, endpointUrl, eventTypes, groupFilter, artifactIdFilter, enabled, "
            + "secret, createdBy, createdOn, modifiedOn) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_DELIVERY_LOG = "INSERT INTO webhook_delivery_logs "
            + "(deliveryId, subscriptionId, eventId, eventType, status, attemptCount, lastAttemptAt, "
            + "nextRetryAt, errorMessage, httpStatusCode, createdOn) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    HandleFactory handles;

    @Test
    void testSubscriptionRoundTrip() throws Exception {
        String subscriptionId = UUID.randomUUID().toString();
        Date createdOn = new Date(1700000000000L);
        Date modifiedOn = new Date(1700000060000L);

        insertSubscription(subscriptionId, "My Subscription", "https://example.com/hooks/registry",
                Set.of(StorageEventType.ARTIFACT_CREATED, StorageEventType.ARTIFACT_DELETED), "^dev-.*",
                "^order-.*", true, "s3cr3t", "alice", createdOn, modifiedOn);

        WebhookSubscriptionDto dto = getSubscription(subscriptionId);

        assertEquals(subscriptionId, dto.getSubscriptionId());
        assertEquals("My Subscription", dto.getName());
        assertEquals("https://example.com/hooks/registry", dto.getEndpointUrl());
        assertEquals(Set.of(StorageEventType.ARTIFACT_CREATED, StorageEventType.ARTIFACT_DELETED),
                dto.getEventTypes());
        assertEquals("^dev-.*", dto.getGroupFilter());
        assertEquals("^order-.*", dto.getArtifactIdFilter());
        assertTrue(dto.isEnabled());
        assertEquals("s3cr3t", dto.getSecret());
        assertEquals("alice", dto.getCreatedBy());
        assertEquals(createdOn, dto.getCreatedOn());
        assertEquals(modifiedOn, dto.getModifiedOn());
    }

    @Test
    void testSubscriptionNullableColumns() throws Exception {
        String subscriptionId = UUID.randomUUID().toString();
        insertSubscription(subscriptionId, null, "https://example.com/minimal",
                Set.of(StorageEventType.GROUP_CREATED), null, null, false, null, null, new Date(),
                new Date());

        WebhookSubscriptionDto dto = getSubscription(subscriptionId);

        assertNull(dto.getName());
        assertNull(dto.getGroupFilter());
        assertNull(dto.getArtifactIdFilter());
        assertNull(dto.getSecret());
        assertNull(dto.getCreatedBy());
        assertFalse(dto.isEnabled());
        assertEquals(Set.of(StorageEventType.GROUP_CREATED), dto.getEventTypes());
    }

    @Test
    void testSecretIsNotIncludedInToString() {
        WebhookSubscriptionDto dto = WebhookSubscriptionDto.builder().subscriptionId("sub-1")
                .endpointUrl("https://example.com/hook").secret("super-secret-hmac-key").build();

        assertFalse(dto.toString().contains("super-secret-hmac-key"),
                "The HMAC secret must never appear in toString() output");
        assertTrue(dto.toString().contains("sub-1"));
    }

    @Test
    void testDeliveryLogRoundTrip() throws Exception {
        String subscriptionId = UUID.randomUUID().toString();
        insertSubscription(subscriptionId, "log-test", "https://example.com/hook",
                Set.of(StorageEventType.ARTIFACT_CREATED), null, null, true, null, null, new Date(),
                new Date());

        String deliveryId = UUID.randomUUID().toString();
        Date lastAttemptAt = new Date(1700000100000L);
        Date nextRetryAt = new Date(1700000200000L);
        Date createdOn = new Date(1700000000000L);

        insertDeliveryLog(deliveryId, subscriptionId, "event-abc-123", StorageEventType.ARTIFACT_CREATED,
                WebhookDeliveryStatus.RETRYING, 3, lastAttemptAt, nextRetryAt, "Connection reset by peer",
                503, createdOn);

        WebhookDeliveryLogDto dto = getDeliveryLog(deliveryId);

        assertEquals(deliveryId, dto.getDeliveryId());
        assertEquals(subscriptionId, dto.getSubscriptionId());
        assertEquals("event-abc-123", dto.getEventId());
        assertEquals(StorageEventType.ARTIFACT_CREATED.name(), dto.getEventType());
        assertEquals(WebhookDeliveryStatus.RETRYING, dto.getStatus());
        assertEquals(3, dto.getAttemptCount());
        assertEquals(lastAttemptAt, dto.getLastAttemptAt());
        assertEquals(nextRetryAt, dto.getNextRetryAt());
        assertEquals("Connection reset by peer", dto.getErrorMessage());
        assertEquals(Integer.valueOf(503), dto.getHttpStatusCode());
        assertEquals(createdOn, dto.getCreatedOn());
    }

    @Test
    void testDeliveryLogNullableColumns() throws Exception {
        String subscriptionId = UUID.randomUUID().toString();
        insertSubscription(subscriptionId, "pending-test", "https://example.com/hook",
                Set.of(StorageEventType.ARTIFACT_CREATED), null, null, true, null, null, new Date(),
                new Date());

        String deliveryId = UUID.randomUUID().toString();
        insertDeliveryLog(deliveryId, subscriptionId, "event-pending", StorageEventType.ARTIFACT_CREATED,
                WebhookDeliveryStatus.PENDING, 0, null, null, null, null, new Date());

        WebhookDeliveryLogDto dto = getDeliveryLog(deliveryId);

        assertEquals(WebhookDeliveryStatus.PENDING, dto.getStatus());
        assertEquals(0, dto.getAttemptCount());
        assertNull(dto.getLastAttemptAt());
        assertNull(dto.getNextRetryAt());
        assertNull(dto.getErrorMessage());
        assertNull(dto.getHttpStatusCode(), "httpStatusCode must map to null, not 0, when the column is NULL");
    }

    @Test
    void testDuplicateEventForSameSubscriptionIsRejected() throws Exception {
        String subscriptionId = UUID.randomUUID().toString();
        insertSubscription(subscriptionId, "dedup-test", "https://example.com/hook",
                Set.of(StorageEventType.ARTIFACT_CREATED), null, null, true, null, null, new Date(),
                new Date());

        String eventId = "event-duplicate-1";
        insertDeliveryLog(UUID.randomUUID().toString(), subscriptionId, eventId,
                StorageEventType.ARTIFACT_CREATED, WebhookDeliveryStatus.DELIVERED, 1, new Date(), null,
                null, 200, new Date());

        // A second row for the same (subscriptionId, eventId) pair must violate UQ_webhook_delivery_logs_1.
        Exception error = assertThrows(Exception.class,
                () -> insertDeliveryLog(UUID.randomUUID().toString(), subscriptionId, eventId,
                        StorageEventType.ARTIFACT_CREATED, WebhookDeliveryStatus.PENDING, 0, null, null,
                        null, null, new Date()));

        assertTrue(rootCauseMessage(error).toUpperCase(Locale.ROOT)
                .contains("UQ_WEBHOOK_DELIVERY_LOGS_1"),
                "Expected a violation of UQ_webhook_delivery_logs_1 but got: " + rootCauseMessage(error));

        // The original row is untouched.
        assertEquals(1, countDeliveryLogs(subscriptionId));
    }

    @Test
    void testSameEventIdForDifferentSubscriptionsIsAllowed() throws Exception {
        String subscriptionA = UUID.randomUUID().toString();
        String subscriptionB = UUID.randomUUID().toString();
        insertSubscription(subscriptionA, "a", "https://example.com/a",
                Set.of(StorageEventType.ARTIFACT_CREATED), null, null, true, null, null, new Date(),
                new Date());
        insertSubscription(subscriptionB, "b", "https://example.com/b",
                Set.of(StorageEventType.ARTIFACT_CREATED), null, null, true, null, null, new Date(),
                new Date());

        String eventId = "event-fanout-1";
        insertDeliveryLog(UUID.randomUUID().toString(), subscriptionA, eventId,
                StorageEventType.ARTIFACT_CREATED, WebhookDeliveryStatus.PENDING, 0, null, null, null, null,
                new Date());
        insertDeliveryLog(UUID.randomUUID().toString(), subscriptionB, eventId,
                StorageEventType.ARTIFACT_CREATED, WebhookDeliveryStatus.PENDING, 0, null, null, null, null,
                new Date());

        assertEquals(1, countDeliveryLogs(subscriptionA));
        assertEquals(1, countDeliveryLogs(subscriptionB));
    }

    @Test
    void testDeliveryLogRequiresExistingSubscription() {
        Exception error = assertThrows(Exception.class,
                () -> insertDeliveryLog(UUID.randomUUID().toString(), "no-such-subscription", "event-orphan",
                        StorageEventType.ARTIFACT_CREATED, WebhookDeliveryStatus.PENDING, 0, null, null,
                        null, null, new Date()));

        assertTrue(rootCauseMessage(error).toUpperCase(Locale.ROOT)
                .contains("FK_WEBHOOK_DELIVERY_LOGS_1"),
                "Expected a foreign key violation but got: " + rootCauseMessage(error));
    }

    @Test
    void testDeletingSubscriptionCascadesToDeliveryLogs() throws Exception {
        String subscriptionId = UUID.randomUUID().toString();
        insertSubscription(subscriptionId, "cascade-test", "https://example.com/hook",
                Set.of(StorageEventType.ARTIFACT_CREATED), null, null, true, null, null, new Date(),
                new Date());
        insertDeliveryLog(UUID.randomUUID().toString(), subscriptionId, "event-cascade-1",
                StorageEventType.ARTIFACT_CREATED, WebhookDeliveryStatus.DELIVERED, 1, new Date(), null,
                null, 200, new Date());
        insertDeliveryLog(UUID.randomUUID().toString(), subscriptionId, "event-cascade-2",
                StorageEventType.ARTIFACT_DELETED, WebhookDeliveryStatus.PENDING, 0, null, null, null, null,
                new Date());
        assertEquals(2, countDeliveryLogs(subscriptionId));

        handles.<Void, RuntimeException> withHandleNoException((Handle handle) -> {
            handle.createUpdate("DELETE FROM webhook_subscriptions WHERE subscriptionId = ?")
                    .bind(0, subscriptionId).execute();
            return null;
        });

        assertEquals(0, countDeliveryLogs(subscriptionId));
    }

    private void insertSubscription(String subscriptionId, String name, String endpointUrl,
            Set<StorageEventType> eventTypes, String groupFilter, String artifactIdFilter, boolean enabled,
            String secret, String createdBy, Date createdOn, Date modifiedOn) throws Exception {
        String eventTypesJson = objectMapper.writeValueAsString(eventTypes);
        handles.<Void, RuntimeException> withHandleNoException((Handle handle) -> {
            handle.createUpdate(INSERT_SUBSCRIPTION).bind(0, subscriptionId).bind(1, name)
                    .bind(2, endpointUrl).bind(3, eventTypesJson).bind(4, groupFilter)
                    .bind(5, artifactIdFilter).bind(6, enabled).bind(7, secret).bind(8, createdBy)
                    .bind(9, createdOn).bind(10, modifiedOn).execute();
            return null;
        });
    }

    private void insertDeliveryLog(String deliveryId, String subscriptionId, String eventId,
            StorageEventType eventType, WebhookDeliveryStatus status, int attemptCount, Date lastAttemptAt,
            Date nextRetryAt, String errorMessage, Integer httpStatusCode, Date createdOn) {
        handles.<Void, RuntimeException> withHandleNoException((Handle handle) -> {
            handle.createUpdate(INSERT_DELIVERY_LOG).bind(0, deliveryId).bind(1, subscriptionId)
                    .bind(2, eventId).bind(3, eventType).bind(4, status).bind(5, attemptCount)
                    .bind(6, lastAttemptAt).bind(7, nextRetryAt).bind(8, errorMessage)
                    .bind(9, httpStatusCode).bind(10, createdOn).execute();
            return null;
        });
    }

    private WebhookSubscriptionDto getSubscription(String subscriptionId) {
        return handles.withHandleNoException((Handle handle) -> handle
                .createQuery("SELECT * FROM webhook_subscriptions WHERE subscriptionId = ?")
                .bind(0, subscriptionId).map(WebhookSubscriptionDtoMapper.instance).one());
    }

    private WebhookDeliveryLogDto getDeliveryLog(String deliveryId) {
        return handles.withHandleNoException(
                (Handle handle) -> handle.createQuery("SELECT * FROM webhook_delivery_logs WHERE deliveryId = ?")
                        .bind(0, deliveryId).map(WebhookDeliveryLogDtoMapper.instance).one());
    }

    private int countDeliveryLogs(String subscriptionId) {
        List<WebhookDeliveryLogDto> logs = handles.withHandleNoException(
                (Handle handle) -> handle
                        .createQuery("SELECT * FROM webhook_delivery_logs WHERE subscriptionId = ?")
                        .bind(0, subscriptionId).map(WebhookDeliveryLogDtoMapper.instance).list());
        return logs.size();
    }

    private String rootCauseMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? "" : cause.getMessage();
    }
}
