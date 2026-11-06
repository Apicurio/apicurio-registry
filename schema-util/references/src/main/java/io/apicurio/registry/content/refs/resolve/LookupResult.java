package io.apicurio.registry.content.refs.resolve;

/**
 * Result of resolving a reference: exactly one of {@code source} or {@code existing} is set.
 *
 * @param source the matching local source, or null
 * @param existing the matching existing artifact, or null
 */
public record LookupResult(IndexedSource source, ExistingArtifact existing) {

    /**
     * Indicates whether the reference resolved to an existing registry artifact.
     *
     * @return true when {@code existing} is set
     */
    public boolean isExisting() {
        return existing != null;
    }
}
