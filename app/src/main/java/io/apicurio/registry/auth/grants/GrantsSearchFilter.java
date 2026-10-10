package io.apicurio.registry.auth.grants;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.apicurio.authz.GrantsData;
import io.apicurio.authz.SearchFilterData;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.ResourceAccessGuard;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.AuthorizationFilter;
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

/**
 * Restricts searches to the resources the caller may read under per-resource authorization, by
 * adding an {@link AuthorizationFilter} that storage translates into its query. Filtering in the
 * query (rather than after it) keeps pagination and total counts accurate.
 *
 * <p>The filter selects exactly what point access allows: the grants patterns are passed through
 * unchanged, admins and the same bypasses as point access see everything, and owners always see
 * their own resources.</p>
 */
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
    ResourceAccessGuard guard;

    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit, boolean skipCount) {
        Set<SearchFilter> restricted = restrict(filters, "artifact");
        if (restricted == null) {
            return emptyArtifactResults();
        }
        return storage.searchArtifacts(restricted, orderBy, orderDir, offset, limit, skipCount);
    }

    public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit) {
        Set<SearchFilter> restricted = restrict(filters, "group");
        if (restricted == null) {
            return emptyGroupResults();
        }
        return storage.searchGroups(restricted, orderBy, orderDir, offset, limit);
    }

    public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit, boolean skipCount) {
        Set<SearchFilter> restricted = restrict(filters, "artifact");
        if (restricted == null) {
            return emptyVersionResults();
        }
        return storage.searchVersions(restricted, orderBy, orderDir, offset, limit, skipCount);
    }

    /**
     * @return the filters, extended with the caller's authorization restriction; or null if the
     *         caller can read nothing (so the search can be skipped)
     */
    Set<SearchFilter> restrict(Set<SearchFilter> filters, String resourceType) {
        if (!guard.isEnforced(AuthorizedLevel.Read)) {
            return filters;
        }
        GrantsData data = grantsAc.getGrantsData();
        if (data == null) {
            // Enabled but not initialized: point access denies everything, and so must search
            LOG.error("Per-resource authorization is enabled but not initialized, denying search access.");
            return null;
        }
        String user = getUsername();
        Set<String> roles = getRoles();
        if (data.isAdmin(roles)) {
            return filters;
        }
        SearchFilterData grants = data.getSearchFilterData(user, roles, resourceType);
        if (grants.allowsEverything()) {
            return filters;
        }
        if (grants.allowsNothing() && user == null) {
            return null;
        }
        Set<SearchFilter> restricted = new HashSet<>(filters);
        restricted.add(SearchFilter.ofAuthorization(new AuthorizationFilter(grants.allowAll(),
                grants.allowedExact(), grants.allowedPrefix(), grants.deniedExact(),
                grants.deniedPrefix(), user)));
        LOG.debug("Search restricted by grants: user={}, type={}, filter={}", user, resourceType, grants);
        return restricted;
    }

    private String getUsername() {
        if (securityIdentity != null && !securityIdentity.isAnonymous()) {
            return securityIdentity.getPrincipal().getName();
        }
        // null rather than a sentinel string, so no named grant or owner can ever match it
        return null;
    }

    private Set<String> getRoles() {
        if (securityIdentity != null && !securityIdentity.isAnonymous()) {
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
