package io.apicurio.registry.mcp.servers;

import io.apicurio.registry.mcp.RegistryService;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

import java.util.List;

import static io.apicurio.registry.mcp.Descriptions.GROUP_ID;
import static io.apicurio.registry.mcp.Descriptions.ARTIFACT_ID;
import static io.apicurio.registry.mcp.Descriptions.RULE_TYPE;
import static io.apicurio.registry.mcp.Descriptions.RULE_CONFIG;
import static io.apicurio.registry.mcp.Utils.handleError;

public class RulesMCPServer {

    @Inject
    RegistryService service;

    // ========== Global Rules Tools ==========

    @Tool(name = "list_global_rules", description = "Get a list of all currently configured global rules.")
    List<RuleType> listGlobalRules() {
        return handleError(() -> service.listGlobalRules());
    }

    @Tool(name = "get_global_rule", description = "Get configuration information for a specific globally configured rule.")
    Rule getGlobalRule(
            @ToolArg(description = RULE_TYPE) String ruleType
    ) {
        return handleError(() -> service.getGlobalRule(ruleType));
    }

    @Tool(name = "create_global_rule", description = "Enable/configure a global rule. If the rule already exists, an error is returned.")
    Rule createGlobalRule(
            @ToolArg(description = RULE_TYPE) String ruleType,
            @ToolArg(description = RULE_CONFIG) String config
    ) {
        return handleError(() -> service.createGlobalRule(ruleType, config));
    }

    @Tool(name = "update_global_rule", description = "Update configuration of an existing global rule. If the rule does not exist, an error is returned.")
    Rule updateGlobalRule(
            @ToolArg(description = RULE_TYPE) String ruleType,
            @ToolArg(description = RULE_CONFIG) String config
    ) {
        return handleError(() -> service.updateGlobalRule(ruleType, config));
    }

    @Tool(name = "delete_global_rule", description = "Disable (delete) a specific globally configured rule.")
    String deleteGlobalRule(
            @ToolArg(description = RULE_TYPE) String ruleType
    ) {
        return handleError(() -> {
            service.deleteGlobalRule(ruleType);
            return "Global rule deleted successfully.";
        });
    }

    @Tool(name = "delete_all_global_rules", description = "Disable (delete) all globally configured rules.")
    String deleteAllGlobalRules() {
        return handleError(() -> {
            service.deleteAllGlobalRules();
            return "All global rules deleted successfully.";
        });
    }

    // ========== Group Rules Tools ==========

    @Tool(name = "list_group_rules", description = "Get a list of all currently configured rules for a group.")
    List<RuleType> listGroupRules(
            @ToolArg(description = GROUP_ID) String groupId
    ) {
        return handleError(() -> service.listGroupRules(groupId));
    }

    @Tool(name = "get_group_rule", description = "Get configuration information for a specific rule configured on a group.")
    Rule getGroupRule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = RULE_TYPE) String ruleType
    ) {
        return handleError(() -> service.getGroupRule(groupId, ruleType));
    }

    @Tool(name = "create_group_rule", description = "Enable/configure a rule on a group. If the rule already exists, an error is returned.")
    Rule createGroupRule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = RULE_TYPE) String ruleType,
            @ToolArg(description = RULE_CONFIG) String config
    ) {
        return handleError(() -> service.createGroupRule(groupId, ruleType, config));
    }

    @Tool(name = "update_group_rule", description = "Update configuration of an existing rule on a group. If the rule does not exist, an error is returned.")
    Rule updateGroupRule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = RULE_TYPE) String ruleType,
            @ToolArg(description = RULE_CONFIG) String config
    ) {
        return handleError(() -> service.updateGroupRule(groupId, ruleType, config));
    }

    @Tool(name = "delete_group_rule", description = "Disable (delete) a specific rule configured on a group.")
    String deleteGroupRule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = RULE_TYPE) String ruleType
    ) {
        return handleError(() -> {
            service.deleteGroupRule(groupId, ruleType);
            return "Group rule deleted successfully.";
        });
    }

    @Tool(name = "delete_all_group_rules", description = "Disable (delete) all rules configured on a group.")
    String deleteAllGroupRules(
            @ToolArg(description = GROUP_ID) String groupId
    ) {
        return handleError(() -> {
            service.deleteAllGroupRules(groupId);
            return "All group rules deleted successfully.";
        });
    }

    // ========== Artifact Rules Tools ==========

    @Tool(name = "list_artifact_rules", description = "Get a list of all currently configured rules for an artifact.")
    List<RuleType> listArtifactRules(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId
    ) {
        return handleError(() -> service.listArtifactRules(groupId, artifactId));
    }

    @Tool(name = "get_artifact_rule", description = "Get configuration information for a specific rule configured on an artifact.")
    Rule getArtifactRule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = RULE_TYPE) String ruleType
    ) {
        return handleError(() -> service.getArtifactRule(groupId, artifactId, ruleType));
    }

    @Tool(name = "create_artifact_rule", description = "Enable/configure a rule on an artifact. If the rule already exists, an error is returned.")
    Rule createArtifactRule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = RULE_TYPE) String ruleType,
            @ToolArg(description = RULE_CONFIG) String config
    ) {
        return handleError(() -> service.createArtifactRule(groupId, artifactId, ruleType, config));
    }

    @Tool(name = "update_artifact_rule", description = "Update configuration of an existing rule on an artifact. If the rule does not exist, an error is returned.")
    Rule updateArtifactRule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = RULE_TYPE) String ruleType,
            @ToolArg(description = RULE_CONFIG) String config
    ) {
        return handleError(() -> service.updateArtifactRule(groupId, artifactId, ruleType, config));
    }

    @Tool(name = "delete_artifact_rule", description = "Disable (delete) a specific rule configured on an artifact.")
    String deleteArtifactRule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = RULE_TYPE) String ruleType
    ) {
        return handleError(() -> {
            service.deleteArtifactRule(groupId, artifactId, ruleType);
            return "Artifact rule deleted successfully.";
        });
    }

    @Tool(name = "delete_all_artifact_rules", description = "Disable (delete) all rules configured on an artifact.")
    String deleteAllArtifactRules(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId
    ) {
        return handleError(() -> {
            service.deleteAllArtifactRules(groupId, artifactId);
            return "All artifact rules deleted successfully.";
        });
    }
}
