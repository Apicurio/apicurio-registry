package io.apicurio.registry.storage.dto;

public enum RuleMode {
    WRITE,      // Execute on serialization
    READ,       // Execute on deserialization
    WRITEREAD,  // Execute on both
    UPGRADE,    // Schema upgrade migration
    DOWNGRADE   // Schema downgrade migration
}
