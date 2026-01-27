package io.apicurio.registry.flink.state;

/**
 * Compatibility mode for schema evolution validation.
 */
public enum CompatibilityMode {

    /**
     * New schema can read old data (add optional fields only).
     */
    BACKWARD,

    /**
     * Old schema can read new data (delete optional fields only).
     */
    FORWARD,

    /**
     * Bidirectional compatibility (both backward and forward).
     */
    FULL,

    /**
     * No compatibility check (any change allowed).
     */
    NONE
}
