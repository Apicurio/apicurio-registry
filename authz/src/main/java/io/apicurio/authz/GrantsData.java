package io.apicurio.authz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrantsData {

    private static final Logger LOG = LoggerFactory.getLogger(GrantsData.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String rawJson;
    private final Set<String> adminRoles;
    private final List<Grant> grants;
    private final Map<String, String> userDataCache = new ConcurrentHashMap<>();

    private GrantsData(String rawJson, Set<String> adminRoles, List<Grant> grants) {
        this.rawJson = rawJson;
        this.adminRoles = adminRoles;
        this.grants = grants;
    }

    public static GrantsData parse(String json) {
        if (json == null || json.isBlank()) {
            return new GrantsData("{}", Set.of(), List.of());
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
                for (JsonNode g : grantsNode) {
                    grants.add(new Grant(
                            g.path("principal").asText(""),
                            g.path("principal_role").asText(""),
                            g.path("operation").asText(""),
                            g.path("resource_type").asText(""),
                            g.path("resource_pattern_type").asText(""),
                            g.path("resource_pattern").asText("")));
                }
            }

            return new GrantsData(json, Collections.unmodifiableSet(adminRoles),
                    Collections.unmodifiableList(grants));
        } catch (Exception e) {
            LOG.error("Failed to parse grants data, authorization will deny all access", e);
            return new GrantsData(json, Set.of(), List.of());
        }
    }

    public String getRawJson() {
        return rawJson;
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

    public String getDataJsonForUser(String user, Set<String> roles) {
        String cacheKey = user + ":" + String.join(",", roles);
        return userDataCache.computeIfAbsent(cacheKey, k -> buildUserDataJson(user, roles));
    }

    private String buildUserDataJson(String user, Set<String> roles) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode config = root.putObject("config");
        ArrayNode adminRolesNode = config.putArray("admin_roles");
        adminRoles.forEach(adminRolesNode::add);

        ArrayNode grantsNode = root.putArray("grants");
        for (Grant grant : grants) {
            if (grant.matchesPrincipal(user, roles)) {
                ObjectNode g = grantsNode.addObject();
                g.put("principal", grant.principal());
                g.put("principal_role", grant.principalRole());
                g.put("operation", grant.operation());
                g.put("resource_type", grant.resourceType());
                g.put("resource_pattern_type", grant.resourcePatternType());
                g.put("resource_pattern", grant.resourcePattern());
            }
        }

        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize per-user grants data", e);
            return rawJson;
        }
    }

    public Set<String> getAllowedValues(String user, Set<String> roles, String resourceType,
            String groupSeparator) {
        boolean hasWildcard = false;
        Set<String> values = new HashSet<>();

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
            if (grant.isWildcard()) {
                hasWildcard = true;
                break;
            }

            if (groupSeparator != null) {
                String group = grant.extractGroupFromPattern(groupSeparator);
                if (group != null) {
                    values.add(group);
                }
            } else {
                if ("exact".equals(grant.resourcePatternType())) {
                    values.add(grant.resourcePattern());
                } else if ("prefix".equals(grant.resourcePatternType())) {
                    hasWildcard = true;
                    break;
                }
            }
        }

        return hasWildcard ? null : values;
    }
}
