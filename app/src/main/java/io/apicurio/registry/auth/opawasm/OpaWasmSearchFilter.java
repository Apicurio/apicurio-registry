package io.apicurio.registry.auth.opawasm;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
public class OpaWasmSearchFilter {

    private static final Logger LOG = LoggerFactory.getLogger(OpaWasmSearchFilter.class);

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    OpaWasmAccessController opaWasmAc;

    @Inject
    OpaWasmAccessControllerConfig config;

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
        if (!config.isEnabled() || opaWasmAc.getPolicyPool() == null) {
            return filters;
        }

        GrantsData data = opaWasmAc.getGrantsData();
        if (data == null) {
            LOG.error("Grants data not loaded, denying search access.");
            return null;
        }

        String user = getUsername();
        Set<String> roles = getRoles();

        if (data.isAdmin(roles)) {
            return filters;
        }

        Set<String> allowedGroups = data.getAllowedGroups(user, roles, resourceType);
        if (allowedGroups == null) {
            return filters;
        }
        if (allowedGroups.isEmpty()) {
            return null;
        }

        Set<SearchFilter> augmented = new HashSet<>(filters);
        augmented.add(SearchFilter.ofGroupIdIn(allowedGroups));
        LOG.debug("Authorization filter: user={}, allowedGroups={}", user, allowedGroups);
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
