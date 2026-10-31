package io.apicurio.registry.storage.impl.gitops;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Type-safe wrapper for a validation task identifier.
 * The ID is a 12-character hex string derived from a random UUID.
 */
public record ValidationTaskId(String value) {

    private static final Pattern CANONICAL_PATTERN = Pattern.compile("^[0-9a-f]{12}$");

    public ValidationTaskId {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid validation task ID: " + value);
        }
    }

    public static boolean isValid(String id) {
        return id != null && CANONICAL_PATTERN.matcher(id).matches();
    }

    public boolean isValid() {
        return isValid(this.value);
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
