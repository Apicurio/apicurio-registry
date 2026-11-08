package io.apicurio.registry.content.refs.resolve;

/**
 * Strategy that decides the registry coordinates used when auto-registering a referenced schema.
 */
@FunctionalInterface
public interface ArtifactNamingStrategy {

    /**
     * Computes the coordinates for the referenced schema described by the given context.
     *
     * @param context the naming context
     * @return the coordinates to register the referenced schema under
     */
    ArtifactCoordinates name(NamingContext context);
}
