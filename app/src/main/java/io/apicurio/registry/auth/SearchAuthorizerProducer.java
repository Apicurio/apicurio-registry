package io.apicurio.registry.auth;

import java.util.Set;

import io.apicurio.registry.auth.grants.GrantsAccessControllerConfig;
import io.apicurio.registry.auth.grants.GrantsSearchFilter;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Produces the {@link ISearchAuthorizer}: a grants-aware implementation when per-resource
 * authorization is enabled, otherwise a plain delegate to storage.
 */
@Singleton
public class SearchAuthorizerProducer {

    @Inject
    GrantsAccessControllerConfig grantsConfig;

    @Inject
    GrantsSearchFilter grantsFilter;

    @Inject
    ResourceAccessGuard guard;

    @Inject
    @Current
    RegistryStorage storage;

    @Produces
    @ApplicationScoped
    public ISearchAuthorizer searchAuthorizer() {
        if (grantsConfig.isEnabled()) {
            return new ISearchAuthorizer() {
                @Override
                public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
                        OrderDirection orderDir, int offset, int limit, boolean skipCount) {
                    return grantsFilter.searchArtifacts(filters, orderBy, orderDir, offset, limit, skipCount);
                }

                @Override
                public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
                        OrderDirection orderDir, int offset, int limit) {
                    return grantsFilter.searchGroups(filters, orderBy, orderDir, offset, limit);
                }

                @Override
                public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
                        OrderDirection orderDir, int offset, int limit, boolean skipCount) {
                    return grantsFilter.searchVersions(filters, orderBy, orderDir, offset, limit, skipCount);
                }

                @Override
                public boolean canReadArtifact(String groupId, String artifactId) {
                    return guard.canRead(groupId, artifactId);
                }
            };
        }
        return new ISearchAuthorizer() {
            @Override
            public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
                    OrderDirection orderDir, int offset, int limit, boolean skipCount) {
                return storage.searchArtifacts(filters, orderBy, orderDir, offset, limit, skipCount);
            }

            @Override
            public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
                    OrderDirection orderDir, int offset, int limit) {
                return storage.searchGroups(filters, orderBy, orderDir, offset, limit);
            }

            @Override
            public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
                    OrderDirection orderDir, int offset, int limit, boolean skipCount) {
                return storage.searchVersions(filters, orderBy, orderDir, offset, limit, skipCount);
            }
        };
    }
}
