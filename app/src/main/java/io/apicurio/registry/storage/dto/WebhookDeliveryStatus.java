package io.apicurio.registry.storage.dto;

/**
 * The delivery state of a single webhook event delivery attempt sequence. Persisted as a string in the
 * "status" column of the "webhook_delivery_logs" table.
 */
public enum WebhookDeliveryStatus {

    /** The delivery has been recorded but not yet attempted. */
    PENDING,

    /** The endpoint accepted the delivery. This is a terminal state. */
    DELIVERED,

    /** All delivery attempts were exhausted without success. This is a terminal state. */
    FAILED,

    /** At least one attempt failed and another attempt is scheduled (see "nextRetryAt"). */
    RETRYING
}
