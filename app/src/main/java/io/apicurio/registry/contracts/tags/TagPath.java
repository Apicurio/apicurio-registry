package io.apicurio.registry.contracts.tags;

import java.util.Objects;

public record TagPath(String path) {
    public TagPath {
        Objects.requireNonNull(path, "path must not be null");
    }

    /**
     * Path format: "field", "record.field", "record.nested.field"
     * Supports wildcards: "*" (single level), "**" (zero or more levels, including itself)
     */
    public boolean matches(String fieldPath) {
        if (fieldPath == null) {
            return false;
        }
        String[] patternSegments = path.split("\\.");
        String[] fieldSegments = fieldPath.split("\\.");
        return matches(patternSegments, fieldSegments, 0, 0);
    }

    public static TagPath parse(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Tag path must not be null");
        }
        String trimmed = path.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Tag path must not be blank");
        }
        return new TagPath(trimmed);
    }

    private static boolean matches(String[] patternSegments, String[] fieldSegments, int patternIndex,
            int fieldIndex) {
        if (patternIndex == patternSegments.length) {
            return fieldIndex == fieldSegments.length;
        }
        String pattern = patternSegments[patternIndex];
        if ("**".equals(pattern)) {
            for (int i = fieldIndex; i <= fieldSegments.length; i++) {
                if (matches(patternSegments, fieldSegments, patternIndex + 1, i)) {
                    return true;
                }
            }
            return false;
        }
        if (fieldIndex >= fieldSegments.length) {
            return false;
        }
        if ("*".equals(pattern) || pattern.equals(fieldSegments[fieldIndex])) {
            return matches(patternSegments, fieldSegments, patternIndex + 1, fieldIndex + 1);
        }
        return false;
    }
}
