package io.apicurio.registry.storage.dto;

public enum RuleAction {
    NONE,   // No action
    ERROR,  // Throw exception
    DLQ     // Route to dead letter queue
}
