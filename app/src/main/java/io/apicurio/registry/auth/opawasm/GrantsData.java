package io.apicurio.registry.auth.opawasm;

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

    public boolean isAdmin(Set<String> roles) {
        for (String adminRole : adminRoles) {
            if (roles.contains(adminRole)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> getAllowedGroups(String user, Set<String> roles, String resourceType) {
        boolean hasWildcard = false;
        Set<String> groups = new HashSet<>();

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

            if ("*".equals(grant.resourcePattern)) {
                hasWildcard = true;
                break;
            }

            if ("artifact".equals(resourceType)) {
                String group = grant.extractGroupFromArtifactPattern();
                if (group != null) {
                    groups.add(group);
                }
            } else if ("group".equals(resourceType)) {
                if ("exact".equals(grant.resourcePatternType)) {
                    groups.add(grant.resourcePattern);
                } else if ("prefix".equals(grant.resourcePatternType)) {
                    hasWildcard = true;
                    break;
                }
            }
        }

        return hasWildcard ? null : groups;
    }

    public record Grant(
            String principal,
            String principalRole,
            String operation,
            String resourceType,
            String resourcePatternType,
            String resourcePattern) {

        boolean matchesPrincipal(String user, Set<String> roles) {
            if (user.equals(principal)) {
                return true;
            }
            return !principalRole.isEmpty() && roles.contains(principalRole);
        }

        boolean matchesResourceType(String type) {
            return type.equals(resourceType);
        }

        boolean impliesOperation(String op) {
            if (operation.equals(op)) {
                return true;
            }
            if ("admin".equals(operation)) {
                return true;
            }
            return "write".equals(operation) && "read".equals(op);
        }

        String extractGroupFromArtifactPattern() {
            if ("prefix".equals(resourcePatternType) && resourcePattern.contains("/")) {
                return resourcePattern.substring(0, resourcePattern.indexOf("/"));
            }
            if ("exact".equals(resourcePatternType) && resourcePattern.contains("/")) {
                return resourcePattern.substring(0, resourcePattern.indexOf("/"));
            }
            return null;
        }
    }
}
