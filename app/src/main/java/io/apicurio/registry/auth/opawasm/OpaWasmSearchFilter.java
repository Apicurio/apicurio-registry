package io.apicurio.registry.auth.opawasm;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

        String user = getUsername();
        Set<String> roles = getRoles();

        if (isAdmin(user, roles)) {
            return filters;
        }

        Set<String> allowedGroups = getAllowedGroups(user, roles, resourceType);
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

    private Set<String> getAllowedGroups(String user, Set<String> roles, String resourceType) {
        String dataJson = opaWasmAc.getPermissionsData();
        if (dataJson == null) {
            return null;
        }

        try {
            JsonNode data = MAPPER.readTree(dataJson);
            JsonNode grants = data.path("grants");
            if (!grants.isArray()) {
                return null;
            }

            boolean hasWildcard = false;
            Set<String> groups = new HashSet<>();

            for (JsonNode grant : grants) {
                if (!matchesPrincipal(grant, user, roles)) {
                    continue;
                }
                if (!matchesResourceType(grant, resourceType)) {
                    continue;
                }
                if (!matchesOperation(grant, "read")) {
                    continue;
                }

                String pattern = grant.path("resource_pattern").asText("");
                String patternType = grant.path("resource_pattern_type").asText("");

                if ("*".equals(pattern)) {
                    hasWildcard = true;
                    break;
                }

                if ("artifact".equals(resourceType)) {
                    if ("prefix".equals(patternType) && pattern.contains("/")) {
                        groups.add(pattern.substring(0, pattern.indexOf("/")));
                    } else if ("exact".equals(patternType) && pattern.contains("/")) {
                        groups.add(pattern.substring(0, pattern.indexOf("/")));
                    }
                } else if ("group".equals(resourceType)) {
                    if ("exact".equals(patternType)) {
                        groups.add(pattern);
                    } else if ("prefix".equals(patternType)) {
                        hasWildcard = true;
                        break;
                    }
                }
            }

            if (hasWildcard) {
                return null;
            }
            return groups;
        } catch (Exception e) {
            LOG.error("Failed to parse grants data for search filtering", e);
            return null;
        }
    }

    private boolean isAdmin(String user, Set<String> roles) {
        String dataJson = opaWasmAc.getPermissionsData();
        if (dataJson == null) {
            return false;
        }
        try {
            JsonNode data = MAPPER.readTree(dataJson);
            JsonNode adminRoles = data.path("config").path("admin_roles");
            if (adminRoles.isArray()) {
                for (JsonNode adminRole : adminRoles) {
                    if (roles.contains(adminRole.asText())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean matchesPrincipal(JsonNode grant, String user, Set<String> roles) {
        String principal = grant.path("principal").asText("");
        if (user.equals(principal)) {
            return true;
        }
        String principalRole = grant.path("principal_role").asText("");
        return !principalRole.isEmpty() && roles.contains(principalRole);
    }

    private boolean matchesResourceType(JsonNode grant, String resourceType) {
        return resourceType.equals(grant.path("resource_type").asText(""));
    }

    private boolean matchesOperation(JsonNode grant, String operation) {
        String grantOp = grant.path("operation").asText("");
        if (grantOp.equals(operation)) {
            return true;
        }
        if ("admin".equals(grantOp)) {
            return true;
        }
        if ("write".equals(grantOp) && "read".equals(operation)) {
            return true;
        }
        return false;
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
        result.setArtifacts(java.util.List.of());
        result.setCount(0L);
        return result;
    }

    private static GroupSearchResultsDto emptyGroupResults() {
        GroupSearchResultsDto result = new GroupSearchResultsDto();
        result.setGroups(java.util.List.of());
        result.setCount(0);
        return result;
    }

    private static VersionSearchResultsDto emptyVersionResults() {
        VersionSearchResultsDto result = new VersionSearchResultsDto();
        result.setVersions(java.util.List.of());
        result.setCount(0L);
        return result;
    }
}
