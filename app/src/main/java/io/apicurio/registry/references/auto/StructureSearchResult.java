package io.apicurio.registry.references.auto;

import java.util.List;

/**
 * Result of {@link ArtifactLookup#searchByStructure}.
 *
 * @param artifactIds the readable matching artifact ids, sorted
 * @param truncated true if the search stopped before seeing every candidate, so {@code artifactIds} may be
 *        incomplete and must not be used to decide that a match is unique
 */
public record StructureSearchResult(List<String> artifactIds, boolean truncated) {

    /**
     * Creates a complete (non-truncated) result.
     *
     * @param artifactIds the matching artifact ids
     * @return the result
     */
    public static StructureSearchResult complete(List<String> artifactIds) {
        return new StructureSearchResult(artifactIds, false);
    }
}
