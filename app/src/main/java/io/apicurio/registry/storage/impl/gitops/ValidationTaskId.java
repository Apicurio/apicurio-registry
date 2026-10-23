package io.apicurio.registry.storage.impl.gitops;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Type-safe wrapper for a validation task identifier.
 * The ID is a 12-character hex string derived from a random UUID.
 */
public record ValidationTaskId(String value) {

    private static final Pattern VALID_ID = Pattern.compile("^[a-f0-9]{12}$");

    public ValidationTaskId {
        if (value == null || !VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid task ID: must be 12 lowercase hex characters");
        }
    }

    public static boolean isValid(String value) {
        return value != null && VALID_ID.matcher(value).matches();
    }

    public static ValidationTaskId random() {
        return new ValidationTaskId(
                UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    }

    @Override
    public String toString() {
        return value;
    }
}
