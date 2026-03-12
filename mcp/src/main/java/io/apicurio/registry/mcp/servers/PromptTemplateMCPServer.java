package io.apicurio.registry.mcp.servers;

import io.apicurio.registry.mcp.PromptTemplateConverter;
import io.apicurio.registry.mcp.PromptTemplateConverter.MCPPrompt;
import io.apicurio.registry.mcp.PromptTemplateConverter.PromptTemplate;
import io.apicurio.registry.mcp.RegistryService;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.mcp.Descriptions.ARTIFACT_ID;
import static io.apicurio.registry.mcp.Descriptions.GROUP_ID;
import static io.apicurio.registry.mcp.Descriptions.VERSION_EXPRESSION;
import static io.apicurio.registry.mcp.Utils.handleError;
import static io.quarkiverse.mcp.server.PromptMessage.withUserRole;

/**
 * MCP Server that exposes PROMPT_TEMPLATE artifacts as MCP prompts.
 * This enables AI agents to discover and use prompt templates stored in Apicurio Registry.
 */
public class PromptTemplateMCPServer {

    private static final String PROMPT_TEMPLATE_TYPE = "PROMPT_TEMPLATE";

    @Inject
    RegistryService service;

    @Inject
    PromptTemplateConverter converter;

    @Tool(description = """
            List all PROMPT_TEMPLATE artifacts that are MCP-enabled in Apicurio Registry.
            Returns a list of prompts that can be used via the MCP protocol.""")
    List<MCPPrompt> list_mcp_prompts() {
        return handleError(() -> {
            List<MCPPrompt> prompts = new ArrayList<>();

            // Search for PROMPT_TEMPLATE artifacts
            var versions = service.searchVersions(
                    null,  // groupId - search all groups
                    null,  // artifactId
                    PROMPT_TEMPLATE_TYPE,  // artifactType
                    null,  // name
                    null,  // description
                    null,  // labels
                    "asc", // order
                    "artifactId"  // orderBy
            );

            for (SearchedVersion version : versions) {
                try {
                    String content = service.getVersionContent(
                            version.getGroupId(),
                            version.getArtifactId(),
                            version.getVersion()
                    );

                    PromptTemplate template = converter.parseContentAutoDetect(content);
                    if (converter.isMCPEnabled(template)) {
                        MCPPrompt prompt = converter.toMCPPrompt(template);
                        if (prompt != null) {
                            prompts.add(prompt);
                        }
                    }
                } catch (IOException e) {
                    // Skip artifacts that can't be read
                }
            }

            return prompts;
        });
    }

    @Tool(description = """
            Get an MCP-enabled prompt template from Apicurio Registry and render it with the provided arguments.
            Returns the rendered prompt content ready for use with an LLM.""")
    String get_mcp_prompt(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = VERSION_EXPRESSION + " Defaults to 'branch=latest' if not specified.", required = false)
            String versionExpression,
            @ToolArg(description = "JSON object containing the arguments to substitute into the prompt template.", required = false)
            String argumentsJson
    ) {
        return handleError(() -> {
            String version = versionExpression != null ? versionExpression : "branch=latest";

            String content = service.getVersionContent(groupId, artifactId, version);

            PromptTemplate template = converter.parseContentAutoDetect(content);
            if (template == null) {
                throw new IllegalArgumentException("Failed to parse prompt template content");
            }

            if (!converter.isMCPEnabled(template)) {
                throw new IllegalArgumentException("Prompt template is not MCP-enabled. Set mcp.enabled to true.");
            }

            Map<String, Object> args = parseArguments(argumentsJson);
            return converter.renderTemplate(template.getTemplate(), args);
        });
    }

    @Tool(description = """
            Render a prompt template from Apicurio Registry with the provided arguments.
            This tool works with any PROMPT_TEMPLATE artifact, regardless of MCP enablement.""")
    String render_prompt_template(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = VERSION_EXPRESSION + " Defaults to 'branch=latest' if not specified.", required = false)
            String versionExpression,
            @ToolArg(description = "JSON object containing the variables to substitute into the prompt template.", required = false)
            String variablesJson
    ) {
        return handleError(() -> {
            String version = versionExpression != null ? versionExpression : "branch=latest";

            String content = service.getVersionContent(groupId, artifactId, version);

            Map<String, Object> vars = parseArguments(variablesJson);
            String rendered = converter.parseAndRenderAutoDetect(content, vars);

            if (rendered == null) {
                throw new IllegalArgumentException("Failed to parse and render prompt template");
            }

            return rendered;
        });
    }

    @Prompt(description = "Render a prompt template from Apicurio Registry for interactive use.")
    PromptMessage render_registry_prompt(
            @PromptArg(description = "The group ID containing the prompt template") String groupId,
            @PromptArg(description = "The artifact ID of the prompt template") String artifactId,
            @PromptArg(description = "The version expression (defaults to latest)", required = false) String version,
            @PromptArg(description = "JSON object with template variables", required = false) String variables
    ) {
        String versionExpr = version != null ? version : "branch=latest";

        try {
            String content = service.getVersionContent(groupId, artifactId, versionExpr);

            Map<String, Object> vars = parseArguments(variables);
            String rendered = converter.parseAndRenderAutoDetect(content, vars);

            if (rendered == null) {
                return withUserRole(new TextContent("Error: Failed to parse prompt template from " + groupId + "/" + artifactId));
            }

            return withUserRole(new TextContent(rendered));
        } catch (Exception e) {
            return withUserRole(new TextContent("Error: " + e.getMessage()));
        }
    }

    private Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, Map.class);
            return args;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse arguments JSON: " + e.getMessage(), e);
        }
    }
}
