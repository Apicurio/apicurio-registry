package io.apicurio.registry.types;

/**
 * @author Ales Justin
 */
public enum CompatibilityLevel {
    BACKWARD,
    BACKWARD_TRANSITIVE,
    FORWARD,
    FORWARD_TRANSITIVE,
    FULL,
    FULL_TRANSITIVE
}
