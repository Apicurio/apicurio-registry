package io.apicurio.registry.storage.impl.gitops;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Type-safe wrapper for a validation task identifier.
 * The ID is a 12-character hex string derived from a random UUID.
 * Input is validated before any filesystem path construction to prevent path traversal.
 */
public record ValidationTaskId(String value) {

    private static final Pattern VALID_FORMAT = Pattern.compile("^[a-f0-9]{12}$");

    public ValidationTaskId {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid task ID format: " + value);
        }
    }

    public static boolean isValid(String value) {
        return value != null && VALID_FORMAT.matcher(value).matches();
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
