package io.apicurio.registry.auth;

import java.util.Set;

import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;

public interface ISearchAuthorizer {

    ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit);

    GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit);

    VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit);
}
