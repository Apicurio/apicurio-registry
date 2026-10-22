package io.apicurio.registry.rules.integrity;

/**
 * Defines the level of referential integrity checking performed by the integrity rule when new content is
 * added to an artifact. Integrity checking validates that artifact references are consistent and correct.
 */
public enum IntegrityLevel {

    /**
     * No integrity checking is performed.
     */
    NONE,

    /**
     * Validates that all referenced artifacts exist in the registry.
     */
    REFS_EXIST,

    /**
     * Validates that all references found in the content are mapped to artifact references in the registry.
     */
    ALL_REFS_MAPPED,

    /**
     * Validates that no duplicate artifact references are present.
     */
    NO_DUPLICATES,

    /**
     * Validates that no circular reference chains exist among artifacts.
     */
    NO_CIRCULAR_REFERENCES,

    /**
     * Performs all integrity checks: references exist, all references are mapped, no duplicates, and no
     * circular references.
     */
    FULL;

}
