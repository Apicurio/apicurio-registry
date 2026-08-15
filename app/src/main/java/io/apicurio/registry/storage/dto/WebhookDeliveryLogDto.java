package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Date;

/**
 * Read-only data transfer object representing the delivery record for a single CloudEvent sent to a single
 * webhook subscription. Exactly one row exists per (subscription, event) pair - the unique constraint on
 * those two columns is what provides deduplication for at-least-once delivery semantics.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class WebhookDeliveryLogDto {

    private String deliveryId;
    private String subscriptionId;
    private String eventId;
    private String eventType;
    private WebhookDeliveryStatus status;
    private int attemptCount;
    private Date lastAttemptAt;
    private Date nextRetryAt;
    private String errorMessage;
    private Integer httpStatusCode;
    private String lockedBy;
    private Date leaseUntil;
    private Date createdOn;
}
