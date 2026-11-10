package io.apicurio.registry.resolver.strategy;

/**
 * Decides the group and artifact ID under which a schema referenced by another schema is auto-registered by
 * the serdes. The reference name stored on the parent is never changed. Implementations must be deterministic
 * and need a public no-arg constructor when configured by class name through
 * {@code apicurio.registry.reference-naming-strategy}.
 */
@FunctionalInterface
public interface ReferenceNamingStrategy {

    /**
     * Returns the coordinates for a referenced schema.
     *
     * @param context the referenced schema and its surroundings
     * @return the group (null means the default group) and the non-blank artifact ID
     */
    ReferenceCoordinates name(ReferenceNamingContext context);
}
