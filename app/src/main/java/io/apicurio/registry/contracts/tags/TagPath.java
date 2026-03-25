package io.apicurio.registry.contracts.tags;

import java.util.Objects;

public record TagPath(String path) {
    public TagPath {
        Objects.requireNonNull(path, "path must not be null");
    }

    /**
     * Path format: "field", "record.field", "record.nested.field"
     * Supports wildcards: "*" (single level), "**" (any depth)
     */
    public boolean matches(String fieldPath) {
        if (fieldPath == null) {
            return false;
        }
        String[] patternSegments = path.split("\\.");
        String[] fieldSegments = fieldPath.split("\\.");
        Boolean[][] memo = new Boolean[patternSegments.length + 1][fieldSegments.length + 1];
        return matches(patternSegments, fieldSegments, 0, 0, memo);
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
            int fieldIndex, Boolean[][] memo) {
        if (memo[patternIndex][fieldIndex] != null) {
            return memo[patternIndex][fieldIndex];
        }
        boolean result;
        if (patternIndex == patternSegments.length) {
            result = fieldIndex == fieldSegments.length;
        } else {
            String pattern = patternSegments[patternIndex];
            if ("**".equals(pattern)) {
                result = false;
                for (int i = fieldIndex; i <= fieldSegments.length; i++) {
                    if (matches(patternSegments, fieldSegments, patternIndex + 1, i, memo)) {
                        result = true;
                        break;
                    }
                }
            } else if (fieldIndex >= fieldSegments.length) {
                result = false;
            } else if ("*".equals(pattern) || pattern.equals(fieldSegments[fieldIndex])) {
                result = matches(patternSegments, fieldSegments, patternIndex + 1, fieldIndex + 1, memo);
            } else {
                result = false;
            }
        }
        memo[patternIndex][fieldIndex] = result;
        return result;
    }
}
