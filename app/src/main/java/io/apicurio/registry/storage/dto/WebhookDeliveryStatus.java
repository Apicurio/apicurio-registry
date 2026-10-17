package io.apicurio.registry.storage.dto;

public enum WebhookDeliveryStatus {
    PENDING,
    IN_FLIGHT,
    RETRYING,
    DELIVERED,
    FAILED,
    CANCELLED
}
