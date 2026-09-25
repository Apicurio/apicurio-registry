package io.apicurio.registry.storage.impl.gitops;

import java.util.UUID;

/**
 * Type-safe wrapper for a validation task identifier.
 * The ID is a 12-character hex string derived from a random UUID.
 */
public record ValidationTaskId(String value) {

    public static ValidationTaskId random() {
        return new ValidationTaskId(
                UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    }

    @Override
    public String toString() {
        return value;
    }
}
