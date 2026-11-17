package io.apicurio.registry.references.auto;

import io.apicurio.registry.content.ContentHandle;

import java.util.List;
import java.util.Optional;

/**
 * Read-only view of registry artifacts used by {@link ReferenceMatcher} to resolve references.
 */
public interface ArtifactLookup {

    /**
     * Latest version on the "latest" branch, empty if the artifact is missing, unreadable, or has none.
     *
     * @param groupId the group id
     * @param artifactId the artifact id
     * @return the latest version, if any
     */
    Optional<String> latestVersion(String groupId, String artifactId);

    /**
     * Readable artifactIds in the group whose structure index has every given "type:kind:value" entry.
     *
     * @param groupId the group id
     * @param structureFilters the structure filters, all of which must match
     * @return the matching artifact ids, flagged as truncated if there were too many to collect them all
     */
    StructureSearchResult searchByStructure(String groupId, List<String> structureFilters);

    /**
     * Content of a specific version, empty if missing/unreadable.
     *
     * @param groupId the group id
     * @param artifactId the artifact id
     * @param version the version, as returned by {@link #latestVersion}
     * @return the content, if any
     */
    Optional<ContentHandle> content(String groupId, String artifactId, String version);
}
