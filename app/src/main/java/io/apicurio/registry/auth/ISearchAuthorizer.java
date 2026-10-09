package io.apicurio.registry.auth;

import java.util.Set;

import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;

/**
 * Entry point for client-facing search and list endpoints. When per-resource authorization is
 * enabled, the implementation restricts results to resources the caller may read, inside the
 * storage query so pagination and counts stay accurate. Otherwise it delegates to storage.
 *
 * <p>Endpoints that return registry resources must use this instead of calling
 * {@code RegistryStorage.search*} directly.</p>
 */
public interface ISearchAuthorizer {

    ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit, boolean skipCount);

    GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit);

    VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit, boolean skipCount);

    /**
     * For results that cannot be filtered in the storage query (e.g. lists resolved from a content
     * ID or a reference graph).
     *
     * @return true if the caller may read the artifact
     */
    default boolean canReadArtifact(String groupId, String artifactId) {
        return true;
    }
}
