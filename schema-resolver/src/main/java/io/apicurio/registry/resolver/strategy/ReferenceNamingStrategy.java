package io.apicurio.registry.resolver.strategy;

/**
 * Decides the group and artifact ID under which a schema referenced by another schema is auto-registered by
 * the serdes. The reference name stored on the parent is never changed.
 *
 * <p>
 * Implementations must be deterministic: the result may depend only on the context, so the same referenced
 * schema always gets the same coordinates. The resolver caches registrations by schema content, so a schema
 * that is named differently on a later call can be given its earlier coordinates. A single instance is shared
 * by all threads using a serializer, so implementations must be thread-safe. A public no-arg constructor is
 * needed when the strategy is configured by class name through
 * {@code apicurio.registry.reference-naming-strategy}.
 * </p>
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
