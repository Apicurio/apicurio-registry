package io.apicurio.registry.storage.dto;

/**
 * Enumeration of content hash types supported by the registry.
 * Each hash type represents a different way of calculating a hash for artifact content,
 * allowing for multiple lookup strategies.
 */
public enum ContentHashType {

    /**
     * Standard SHA-256 hash of the raw content plus serialized references.
     * This is the exact byte-for-byte hash without any normalization.
     */
    CONTENT_SHA256("content-sha256"),

    /**
     * SHA-256 hash of canonicalized content plus serialized references.
     * Content is normalized (e.g., JSON fields reordered, whitespace normalized)
     * before hashing, allowing functionally equivalent content to match.
     */
    CANONICAL_SHA256("canonical-sha256"),

    /**
     * SHA-256 hash of canonicalized content without references.
     * Useful for finding content that is functionally equivalent regardless
     * of how references are mapped.
     */
    CANONICAL_NO_REFS_SHA256("canonical-no-refs-sha256");

    private final String value;

    ContentHashType(String value) {
        this.value = value;
    }

    /**
     * Gets the string representation of this hash type for database storage.
     *
     * @return the hash type value
     */
    public String value() {
        return value;
    }

    /**
     * Converts a string value to a ContentHashType enum.
     *
     * @param value the string value
     * @return the corresponding ContentHashType
     * @throws IllegalArgumentException if the value does not match any hash type
     */
    public static ContentHashType fromValue(String value) {
        for (ContentHashType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown content hash type: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
