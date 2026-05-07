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

    public static GrantsData parse(String json) {
        if (json == null || json.isBlank()) {
            return new GrantsData(Set.of(), List.of());
        }
        try {
            JsonNode root = MAPPER.readTree(json);

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

                    if (principal.isEmpty() && principalRole.isEmpty()) {
                        LOG.warn("Grant at index {} has no principal or principal_role, skipping.", i);
                        continue;
                    }
                    if (operation.isEmpty()) {
                        LOG.warn("Grant at index {} has no operation, skipping.", i);
                        continue;
                    }
                    if (resourceType.isEmpty()) {
                        LOG.warn("Grant at index {} has no resource_type, skipping.", i);
                        continue;
                    }
                    if (resourcePattern.isEmpty()) {
                        LOG.warn("Grant at index {} has no resource_pattern, skipping.", i);
                        continue;
                    }
                    if (!VALID_OPERATIONS.contains(operation)) {
                        LOG.warn("Grant at index {} has unrecognized operation '{}'. "
                                + "Valid values: read, write, admin.", i, operation);
                    }
                    if (!resourcePatternType.isEmpty() && !VALID_PATTERN_TYPES.contains(resourcePatternType)) {
                        LOG.warn("Grant at index {} has unrecognized resource_pattern_type '{}'. "
                                + "Valid values: prefix, exact.", i, resourcePatternType);
                    }

                    boolean deny = g.path("deny").asBoolean(false);

                    grants.add(new Grant(principal, principalRole, operation, resourceType,
                            resourcePatternType, resourcePattern, deny));
                }
            }

            LOG.info("Loaded {} grants ({} admin roles: {}).", grants.size(), adminRoles.size(), adminRoles);
            return new GrantsData(Collections.unmodifiableSet(adminRoles),
                    Collections.unmodifiableList(grants));
        } catch (Exception e) {
            LOG.error("Failed to parse grants data, authorization will deny all access", e);
            return new GrantsData(Set.of(), List.of());
        }
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

    public SearchFilterData getSearchFilterData(String user, Set<String> roles, String resourceType,
            String groupSeparator) {
        Set<String> groups = new HashSet<>();
        Set<String> exactResources = new HashSet<>();
        Set<String> deniedExactResources = new HashSet<>();
        boolean hasWildcard = false;

        for (Grant grant : grants) {
            if (!grant.matchesPrincipal(user, roles)) {
                continue;
            }
            if (!grant.matchesResourceType(resourceType)) {
                continue;
            }
            if (!grant.impliesOperation("read")) {
                continue;
            }

            if (grant.deny()) {
                if ("exact".equals(grant.resourcePatternType()) && groupSeparator != null
                        && grant.resourcePattern().contains(groupSeparator)) {
                    deniedExactResources.add(grant.resourcePattern());
                }
                continue;
            }

            if (grant.isWildcard()) {
                hasWildcard = true;
                continue;
            }

            if (hasWildcard) {
                continue;
            }

            if (groupSeparator != null) {
                if ("prefix".equals(grant.resourcePatternType())) {
                    String group = grant.extractGroupFromPattern(groupSeparator);
                    if (group != null) {
                        groups.add(group);
                    }
                } else if ("exact".equals(grant.resourcePatternType())) {
                    if (grant.resourcePattern().contains(groupSeparator)) {
                        exactResources.add(grant.resourcePattern());
                    } else {
                        groups.add(grant.resourcePattern());
                    }
                }
            } else {
                if ("exact".equals(grant.resourcePatternType())) {
                    groups.add(grant.resourcePattern());
                } else if ("prefix".equals(grant.resourcePatternType())) {
                    hasWildcard = true;
                }
            }
        }

        if (hasWildcard) {
            if (deniedExactResources.isEmpty()) {
                return SearchFilterData.all();
            }
            return new SearchFilterData(Set.of(), Set.of(), deniedExactResources, true);
        }
        if (groups.isEmpty() && exactResources.isEmpty()) {
            return SearchFilterData.none();
        }
        return new SearchFilterData(groups, exactResources, deniedExactResources, false);
    }

}
