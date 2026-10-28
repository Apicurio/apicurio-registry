package io.apicurio.registry.auth.grants;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.apicurio.authz.Artifact;
import io.apicurio.authz.Group;
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
import io.apicurio.scope.ResourceScope;
import io.kroxylicious.authorizer.service.ResourceType;
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
 * <p>The filter selects exactly what point access allows: the scope of the access rules is passed
 * through unchanged, the same bypasses as point access see everything, and owners see their own
 * resources unless the owner bypass is disabled. Rules with {@code matching} patterns cannot be
 * evaluated by storage, so a caller whose scope has patterns gets no results (fail closed).</p>
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

    @Inject
    GrantsAccessControllerConfig config;

    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit, boolean skipCount) {
        Set<SearchFilter> restricted = restrict(filters, Artifact.READ);
        if (restricted == null) {
            return emptyArtifactResults();
        }
        return storage.searchArtifacts(restricted, orderBy, orderDir, offset, limit, skipCount);
    }

    public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit) {
        Set<SearchFilter> restricted = restrict(filters, Group.READ);
        if (restricted == null) {
            return emptyGroupResults();
        }
        return storage.searchGroups(restricted, orderBy, orderDir, offset, limit);
    }

    public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit, boolean skipCount) {
        Set<SearchFilter> restricted = restrict(filters, Artifact.READ);
        if (restricted == null) {
            return emptyVersionResults();
        }
        return storage.searchVersions(restricted, orderBy, orderDir, offset, limit, skipCount);
    }

    /**
     * @return the filters, extended with the caller's authorization restriction; or null if the
     *         caller can read nothing (so the search can be skipped)
     */
    Set<SearchFilter> restrict(Set<SearchFilter> filters, ResourceType<?> readOperation) {
        if (!guard.isEnforced(AuthorizedLevel.Read)) {
            return filters;
        }
        ResourceScope scope = grantsAc.readScope(readOperation);
        if (scope == null) {
            // Enabled but not initialized: point access denies everything, and so must search
            LOG.error("Per-resource authorization is enabled but not initialized, denying search access.");
            return null;
        }
        String user = getUsername();
        // Owners match in search exactly when they bypass the access rules in point access
        String owner = config.isOwnerBypassEnabled() ? user : null;
        AuthorizationFilter filter = switch (scope) {
            case ResourceScope.Unrestricted unrestricted -> null;
            case ResourceScope.Forbidden forbidden ->
                    owner == null ? null : new AuthorizationFilter(false, Set.of(), Set.of(), Set.of(), Set.of(), owner);
            case ResourceScope.Scoped scoped -> {
                if (scoped.hasPatterns()) {
                    // SQL and Elasticsearch can't evaluate RE2 patterns. Ignoring a denied pattern would
                    // expose resources and ignoring an allowed one would hide them, so fail closed.
                    LOG.warn("Search returns no results: the access rules of this caller use 'matching' for {} "
                            + "resources, which search cannot evaluate. Use '=', 'in' or 'like' for resources "
                            + "that must be searchable.", readOperation.getClass().getSimpleName());
                    yield null;
                }
                yield new AuthorizationFilter(scoped.allowAll(), scoped.allowExact(), scoped.allowPrefix(),
                        scoped.denyExact(), scoped.denyPrefix(), owner);
            }
        };
        if (scope instanceof ResourceScope.Unrestricted) {
            return filters;
        }
        if (filter == null) {
            return null;
        }
        Set<SearchFilter> restricted = new HashSet<>(filters);
        restricted.add(SearchFilter.ofAuthorization(filter));
        LOG.debug("Search restricted by access rules: user={}, operation={}, scope={}", user, readOperation, scope);
        return restricted;
    }

    private String getUsername() {
        if (securityIdentity != null && !securityIdentity.isAnonymous()) {
            return securityIdentity.getPrincipal().getName();
        }
        // null rather than a sentinel string, so no named grant or owner can ever match it
        return null;
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
