package io.apicurio.authz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrantsData {

    private static final Logger LOG = LoggerFactory.getLogger(GrantsData.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> VALID_OPERATIONS = Set.of("read", "write", "admin");
    private static final Set<String> VALID_PATTERN_TYPES = Set.of("prefix", "exact");

    private final Set<String> adminRoles;
    private final List<Grant> grants;

    private GrantsData(Set<String> adminRoles, List<Grant> grants) {
        this.adminRoles = adminRoles;
        this.grants = grants;
    }

    private static IllegalArgumentException invalid(int index, String problem) {
        return new IllegalArgumentException("Grant at index " + index + " " + problem);
    }

    /** @return grants data with no grants and no admin roles (denies everything) */
    public static GrantsData empty() {
        return new GrantsData(Set.of(), List.of());
    }

    /**
     * Lenient parse: invalid input yields empty grants, which deny everything.
     */
    public static GrantsData parse(String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            return parseStrict(json);
        } catch (IllegalArgumentException e) {
            LOG.error("Failed to parse grants data, authorization will deny all access", e);
            return empty();
        }
    }

    /**
     * Parses grants data, rejecting malformed input: invalid JSON, a root that is not an object,
     * a missing {@code grants} array, or any invalid grant.
     *
     * @throws IllegalArgumentException if the document is malformed
     */
    public static GrantsData parseStrict(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Grants data is not valid JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Grants data must be a JSON object");
        }
        if (!root.path("grants").isArray()) {
            throw new IllegalArgumentException("Grants data must contain a 'grants' array");
        }
        Set<String> adminRoles = new HashSet<>();
        JsonNode adminRolesNode = root.path("config").path("admin_roles");
        if (adminRolesNode.isArray()) {
            for (JsonNode r : adminRolesNode) {
                adminRoles.add(r.asText());
            }
        }

        List<Grant> grants = new ArrayList<>();
        JsonNode grantsNode = root.path("grants");
        if (grantsNode.isArray()) {
            for (int i = 0; i < grantsNode.size(); i++) {
                JsonNode g = grantsNode.get(i);
                String principal = g.path("principal").asText("");
                String principalRole = g.path("principal_role").asText("");
                String operation = g.path("operation").asText("");
                String resourceType = g.path("resource_type").asText("");
                String resourcePatternType = g.path("resource_pattern_type").asText("");
                String resourcePattern = g.path("resource_pattern").asText("");

                // Any invalid grant rejects the whole document: silently skipping a deny rule
                // would grant access the operator meant to forbid.
                if (principal.isEmpty() == principalRole.isEmpty()) {
                    throw invalid(i, "must have exactly one of 'principal' or 'principal_role'");
                }
                if (!VALID_OPERATIONS.contains(operation)) {
                    throw invalid(i, "has operation '" + operation + "'; expected read, write or admin");
                }
                if (resourceType.isEmpty()) {
                    throw invalid(i, "has no 'resource_type'");
                }
                if (resourcePattern.isEmpty()) {
                    throw invalid(i, "has no 'resource_pattern'");
                }
                if (!resourcePatternType.isEmpty() && !VALID_PATTERN_TYPES.contains(resourcePatternType)) {
                    throw invalid(i, "has resource_pattern_type '" + resourcePatternType
                            + "'; expected prefix or exact");
                }
                if (g.has("deny") && !g.get("deny").isBoolean()) {
                    throw invalid(i, "has a non-boolean 'deny'");
                }

                boolean deny = g.path("deny").asBoolean(false);

                grants.add(new Grant(principal, principalRole, operation, resourceType,
                        resourcePatternType, resourcePattern, deny));
            }
        }

        LOG.info("Loaded {} grants ({} admin roles: {}).", grants.size(), adminRoles.size(), adminRoles);
        return new GrantsData(Collections.unmodifiableSet(adminRoles),
                Collections.unmodifiableList(grants));
    }

    public List<Grant> getGrants() {
        return grants;
    }

    public boolean isAdmin(Set<String> roles) {
        for (String adminRole : adminRoles) {
            if (roles.contains(adminRole)) {
                return true;
            }
        }
        return false;
    }

    public List<Grant> getGrantsForUser(String user, Set<String> roles) {
        List<Grant> result = new ArrayList<>();
        for (Grant grant : grants) {
            if (grant.matchesPrincipal(user, roles)) {
                result.add(grant);
            }
        }
        return result;
    }

    /**
     * Returns the read grants of a subject for a resource type as resource-name patterns, for
     * translating into storage queries. The patterns mirror {@link Grant#matchesResource}: a
     * wildcard pattern matches everything, a {@code prefix} pattern matches by prefix, any other
     * pattern type matches exactly. Admins are not special-cased here; check {@link #isAdmin}.
     */
    public SearchFilterData getSearchFilterData(String user, Set<String> roles, String resourceType) {
        boolean allowAll = false;
        Set<String> allowedExact = new HashSet<>();
        Set<String> allowedPrefix = new HashSet<>();
        Set<String> deniedExact = new HashSet<>();
        Set<String> deniedPrefix = new HashSet<>();

        for (Grant grant : grants) {
            if (!grant.matchesPrincipal(user, roles) || !grant.matchesResourceType(resourceType)) {
                continue;
            }
            boolean affectsRead = grant.deny() ? grant.deniesOperation("read") : grant.impliesOperation("read");
            if (!affectsRead) {
                continue;
            }
            Set<String> exact = grant.deny() ? deniedExact : allowedExact;
            Set<String> prefix = grant.deny() ? deniedPrefix : allowedPrefix;
            if (grant.isWildcard()) {
                if (grant.deny()) {
                    deniedPrefix.add("");
                } else {
                    allowAll = true;
                }
            } else if ("prefix".equals(grant.resourcePatternType())) {
                prefix.add(grant.resourcePattern());
            } else {
                exact.add(grant.resourcePattern());
            }
        }
        return new SearchFilterData(allowAll, allowedExact, allowedPrefix, deniedExact, deniedPrefix);
    }

}
