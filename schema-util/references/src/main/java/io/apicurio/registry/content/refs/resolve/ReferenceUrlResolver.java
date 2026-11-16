package io.apicurio.registry.content.refs.resolve;

import java.util.Optional;

/**
 * Resolves absolute reference URLs to artifacts that already exist in a registry.
 */
@FunctionalInterface
public interface ReferenceUrlResolver {

    /**
     * Attempts to resolve the given reference.
     *
     * @param fullReference the full reference string
     * @return the existing artifact, or empty when the reference is not handled by this resolver
     */
    Optional<ExistingArtifact> resolve(String fullReference);
}
