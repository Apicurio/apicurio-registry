package io.apicurio.registry.rules.compatibility;

/**
 * Defines the level of compatibility enforced by the compatibility rule when new content is added to an
 * artifact. Compatibility checking ensures that new versions of an artifact do not break consumers of previous
 * versions.
 */
public enum CompatibilityLevel {

    /**
     * New content must be backward compatible with the latest version. Consumers using the new schema can read
     * data produced with the previous version.
     */
    BACKWARD,

    /**
     * New content must be backward compatible with all previous versions, not just the latest.
     */
    BACKWARD_TRANSITIVE,

    /**
     * New content must be forward compatible with the latest version. Consumers using the previous schema can
     * read data produced with the new version.
     */
    FORWARD,

    /**
     * New content must be forward compatible with all previous versions, not just the latest.
     */
    FORWARD_TRANSITIVE,

    /**
     * New content must be both backward and forward compatible with the latest version.
     */
    FULL,

    /**
     * New content must be both backward and forward compatible with all previous versions.
     */
    FULL_TRANSITIVE,

    /**
     * No compatibility checking is performed.
     */
    NONE
}
