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

    @Tool(description = "Get a list of all currently configured global rules.")
    List<RuleType> list_global_rules() {
        return handleError(() -> service.listGlobalRules());
    }

    @Tool(description = "Get configuration information for a specific globally configured rule.")
    Rule get_global_rule(
            @ToolArg(description = RULE_TYPE) String ruleType
    ) {
        return handleError(() -> service.getGlobalRule(ruleType));
    }

    @Tool(description = "Enable/configure a global rule. If the rule already exists, an error is returned.")
    Rule create_global_rule(
            @ToolArg(description = RULE_TYPE) String ruleType,
            @ToolArg(description = RULE_CONFIG) String config
    ) {
        return handleError(() -> service.createGlobalRule(ruleType, config));
    }

    @Tool(description = "Update configuration of an existing global rule. If the rule does not exist, an error is returned.")
    Rule update_global_rule(
            @ToolArg(description = RULE_TYPE) String ruleType,
            @ToolArg(description = RULE_CONFIG) String config
    ) {
        return handleError(() -> service.updateGlobalRule(ruleType, config));
    }

    @Tool(description = "Disable (delete) a specific globally configured rule.")
    String delete_global_rule(
            @ToolArg(description = RULE_TYPE) String ruleType
    ) {
        return handleError(() -> {
            service.deleteGlobalRule(ruleType);
            return "Global rule deleted successfully.";
        });
    }

    @Tool(description = "Disable (delete) all globally configured rules.")
    String delete_all_global_rules() {
        return handleError(() -> {
            service.deleteAllGlobalRules();
            return "All global rules deleted successfully.";
        });
    }

    // ========== Group Rules Tools ==========

    @Tool(description = "Get a list of all currently configured rules for a group.")
    List<RuleType> list_group_rules(
            @ToolArg(description = GROUP_ID) String groupId
    ) {
        return handleError(() -> service.listGroupRules(groupId));
    }

    @Tool(description = "Get configuration information for a specific rule configured on a group.")
    Rule get_group_rule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = RULE_TYPE) String ruleType
    ) {
        return handleError(() -> service.getGroupRule(groupId, ruleType));
    }

    @Tool(description = "Enable/configure a rule on a group. If the rule already exists, an error is returned.")
    Rule create_group_rule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = RULE_TYPE) String ruleType,
            @ToolArg(description = RULE_CONFIG) String config
    ) {
        return handleError(() -> service.createGroupRule(groupId, ruleType, config));
    }

    @Tool(description = "Update configuration of an existing rule on a group. If the rule does not exist, an error is returned.")
    Rule update_group_rule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = RULE_TYPE) String ruleType,
            @ToolArg(description = RULE_CONFIG) String config
    ) {
        return handleError(() -> service.updateGroupRule(groupId, ruleType, config));
    }

    @Tool(description = "Disable (delete) a specific rule configured on a group.")
    String delete_group_rule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = RULE_TYPE) String ruleType
    ) {
        return handleError(() -> {
            service.deleteGroupRule(groupId, ruleType);
            return "Group rule deleted successfully.";
        });
    }

    @Tool(description = "Disable (delete) all rules configured on a group.")
    String delete_all_group_rules(
            @ToolArg(description = GROUP_ID) String groupId
    ) {
        return handleError(() -> {
            service.deleteAllGroupRules(groupId);
            return "All group rules deleted successfully.";
        });
    }

    // ========== Artifact Rules Tools ==========

    @Tool(description = "Get a list of all currently configured rules for an artifact.")
    List<RuleType> list_artifact_rules(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId
    ) {
        return handleError(() -> service.listArtifactRules(groupId, artifactId));
    }

    @Tool(description = "Get configuration information for a specific rule configured on an artifact.")
    Rule get_artifact_rule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = RULE_TYPE) String ruleType
    ) {
        return handleError(() -> service.getArtifactRule(groupId, artifactId, ruleType));
    }

    @Tool(description = "Enable/configure a rule on an artifact. If the rule already exists, an error is returned.")
    Rule create_artifact_rule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = RULE_TYPE) String ruleType,
            @ToolArg(description = RULE_CONFIG) String config
    ) {
        return handleError(() -> service.createArtifactRule(groupId, artifactId, ruleType, config));
    }

    @Tool(description = "Update configuration of an existing rule on an artifact. If the rule does not exist, an error is returned.")
    Rule update_artifact_rule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = RULE_TYPE) String ruleType,
            @ToolArg(description = RULE_CONFIG) String config
    ) {
        return handleError(() -> service.updateArtifactRule(groupId, artifactId, ruleType, config));
    }

    @Tool(description = "Disable (delete) a specific rule configured on an artifact.")
    String delete_artifact_rule(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = RULE_TYPE) String ruleType
    ) {
        return handleError(() -> {
            service.deleteArtifactRule(groupId, artifactId, ruleType);
            return "Artifact rule deleted successfully.";
        });
    }

    @Tool(description = "Disable (delete) all rules configured on an artifact.")
    String delete_all_artifact_rules(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId
    ) {
        return handleError(() -> {
            service.deleteAllArtifactRules(groupId, artifactId);
            return "All artifact rules deleted successfully.";
        });
    }
}
