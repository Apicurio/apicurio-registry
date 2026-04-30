package io.apicurio.registry.auth.grants;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.apicurio.authz.GrantsData;
import io.apicurio.authz.SearchFilterData;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class GrantsSearchFilter {

    private static final Logger LOG = LoggerFactory.getLogger(GrantsSearchFilter.class);

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    GrantsAccessController grantsAc;

    @Inject
    GrantsAccessControllerConfig config;

    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit) {
        Set<SearchFilter> augmented = addAuthorizationFilters(filters, "artifact");
        if (augmented == null) {
            return emptyArtifactResults();
        }
        return storage.searchArtifacts(augmented, orderBy, orderDir, offset, limit);
    }

    public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit) {
        Set<SearchFilter> augmented = addAuthorizationFilters(filters, "group");
        if (augmented == null) {
            return emptyGroupResults();
        }
        return storage.searchGroups(augmented, orderBy, orderDir, offset, limit);
    }

    public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit) {
        Set<SearchFilter> augmented = addAuthorizationFilters(filters, "artifact");
        if (augmented == null) {
            return emptyVersionResults();
        }
        return storage.searchVersions(augmented, orderBy, orderDir, offset, limit);
    }

    private Set<SearchFilter> addAuthorizationFilters(Set<SearchFilter> filters, String resourceType) {
        if (!config.isEnabled() || grantsAc.getAuthorizer() == null) {
            return filters;
        }

        GrantsData data = grantsAc.getGrantsData();
        if (data == null) {
            LOG.error("Grants data not loaded, denying search access.");
            return null;
        }

        String user = getUsername();
        Set<String> roles = getRoles();

        if (data.isAdmin(roles)) {
            return filters;
        }

        String separator = "artifact".equals(resourceType) ? "/" : null;
        SearchFilterData filterData = data.getSearchFilterData(user, roles, resourceType, separator);

        if (filterData.allowAll()) {
            return filters;
        }
        if (!filterData.hasFilters()) {
            return null;
        }

        Set<SearchFilter> augmented = new HashSet<>(filters);
        if (filterData.allowedExactResources().isEmpty()) {
            augmented.add(SearchFilter.ofGroupIdIn(filterData.allowedGroups()));
        } else {
            augmented.add(SearchFilter.ofGroupIdInOrArtifactExact(
                    filterData.allowedGroups(), filterData.allowedExactResources()));
        }
        LOG.debug("Authorization filter: user={}, groups={}, exact={}",
                user, filterData.allowedGroups(), filterData.allowedExactResources());
        return augmented;
    }

    private String getUsername() {
        if (securityIdentity != null && !securityIdentity.isAnonymous()) {
            return securityIdentity.getPrincipal().getName();
        }
        return "anonymous";
    }

    private Set<String> getRoles() {
        if (securityIdentity != null) {
            return securityIdentity.getRoles();
        }
        return Set.of();
    }

    private static ArtifactSearchResultsDto emptyArtifactResults() {
        ArtifactSearchResultsDto result = new ArtifactSearchResultsDto();
        result.setArtifacts(List.of());
        result.setCount(0L);
        return result;
    }

    private static GroupSearchResultsDto emptyGroupResults() {
        GroupSearchResultsDto result = new GroupSearchResultsDto();
        result.setGroups(List.of());
        result.setCount(0);
        return result;
    }

    private static VersionSearchResultsDto emptyVersionResults() {
        VersionSearchResultsDto result = new VersionSearchResultsDto();
        result.setVersions(List.of());
        result.setCount(0L);
        return result;
    }
}
