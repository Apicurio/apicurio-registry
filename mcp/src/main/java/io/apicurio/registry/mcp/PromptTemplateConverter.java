package io.apicurio.registry.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts PROMPT_TEMPLATE artifacts to MCP prompt format.
 * This is the Java equivalent of mcp-converter.ts for server-side conversion.
 */
@ApplicationScoped
public class PromptTemplateConverter {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    private static final Pattern IF_BLOCK_PATTERN = Pattern.compile("\\{\\{#if\\s+(\\w+)\\}\\}([\\s\\S]*?)\\{\\{/if\\}\\}");

    @Inject
    ObjectMapper jsonMapper;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * Represents a parsed prompt template
     */
    public static class PromptTemplate {
        private String templateId;
        private String name;
        private String description;
        private String version;
        private String template;
        private Map<String, VariableSchema> variables;
        private MCPConfiguration mcp;

        public String getTemplateId() {
            return templateId;
        }

        public void setTemplateId(String templateId) {
            this.templateId = templateId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getTemplate() {
            return template;
        }

        public void setTemplate(String template) {
            this.template = template;
        }

        public Map<String, VariableSchema> getVariables() {
            return variables;
        }

        public void setVariables(Map<String, VariableSchema> variables) {
            this.variables = variables;
        }

        public MCPConfiguration getMcp() {
            return mcp;
        }

        public void setMcp(MCPConfiguration mcp) {
            this.mcp = mcp;
        }
    }

    /**
     * Variable schema definition
     */
    public static class VariableSchema {
        private String type;
        private Boolean required;
        private Object defaultValue;
        private String description;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Boolean getRequired() {
            return required;
        }

        public void setRequired(Boolean required) {
            this.required = required;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    /**
     * MCP configuration section
     */
    public static class MCPConfiguration {
        private Boolean enabled;
        private String name;
        private String description;
        private List<MCPArgument> arguments;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<MCPArgument> getArguments() {
            return arguments;
        }

        public void setArguments(List<MCPArgument> arguments) {
            this.arguments = arguments;
        }
    }

    /**
     * MCP argument definition
     */
    public static class MCPArgument {
        private String name;
        private String description;
        private Boolean required;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Boolean getRequired() {
            return required;
        }

        public void setRequired(Boolean required) {
            this.required = required;
        }
    }

    /**
     * MCP Prompt representation for listing
     */
    public static class MCPPrompt {
        private String name;
        private String description;
        private List<MCPArgument> arguments;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<MCPArgument> getArguments() {
            return arguments;
        }

        public void setArguments(List<MCPArgument> arguments) {
            this.arguments = arguments;
        }
    }

    /**
     * Parse content from JSON or YAML format
     */
    public PromptTemplate parseContent(String content, String contentType) {
        try {
            if (contentType != null && (contentType.contains("yaml") || contentType.contains("x-yaml"))) {
                return parseFromNode(yamlMapper.readTree(content));
            } else {
                return parseFromNode(jsonMapper.readTree(content));
            }
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Parse content by auto-detecting format (JSON or YAML)
     */
    public PromptTemplate parseContentAutoDetect(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String trimmed = content.trim();
        // If it starts with '{', it's likely JSON
        if (trimmed.startsWith("{")) {
            return parseContent(content, "application/json");
        }
        // Otherwise assume YAML
        return parseContent(content, "application/x-yaml");
    }

    /**
     * Parse and render with auto-detected format
     */
    public String parseAndRenderAutoDetect(String content, Map<String, Object> args) {
        PromptTemplate template = parseContentAutoDetect(content);
        if (template == null || template.getTemplate() == null) {
            return null;
        }
        return renderTemplate(template.getTemplate(), args);
    }

    private PromptTemplate parseFromNode(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }

        PromptTemplate template = new PromptTemplate();
        template.setTemplateId(getTextValue(node, "templateId"));
        template.setName(getTextValue(node, "name"));
        template.setDescription(getTextValue(node, "description"));
        template.setVersion(getTextValue(node, "version"));
        template.setTemplate(getTextValue(node, "template"));

        // Parse variables
        JsonNode variablesNode = node.get("variables");
        if (variablesNode != null && variablesNode.isObject()) {
            Map<String, VariableSchema> variables = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = variablesNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                VariableSchema varSchema = new VariableSchema();
                JsonNode varNode = entry.getValue();
                varSchema.setType(getTextValue(varNode, "type"));
                varSchema.setRequired(getBooleanValue(varNode, "required"));
                varSchema.setDescription(getTextValue(varNode, "description"));
                if (varNode.has("default")) {
                    varSchema.setDefaultValue(nodeToObject(varNode.get("default")));
                }
                variables.put(entry.getKey(), varSchema);
            }
            template.setVariables(variables);
        }

        // Parse MCP configuration
        JsonNode mcpNode = node.get("mcp");
        if (mcpNode != null && mcpNode.isObject()) {
            MCPConfiguration mcp = new MCPConfiguration();
            mcp.setEnabled(getBooleanValue(mcpNode, "enabled"));
            mcp.setName(getTextValue(mcpNode, "name"));
            mcp.setDescription(getTextValue(mcpNode, "description"));

            JsonNode argsNode = mcpNode.get("arguments");
            if (argsNode != null && argsNode.isArray()) {
                List<MCPArgument> arguments = new ArrayList<>();
                for (JsonNode argNode : argsNode) {
                    MCPArgument arg = new MCPArgument();
                    arg.setName(getTextValue(argNode, "name"));
                    arg.setDescription(getTextValue(argNode, "description"));
                    arg.setRequired(getBooleanValue(argNode, "required"));
                    arguments.add(arg);
                }
                mcp.setArguments(arguments);
            }
            template.setMcp(mcp);
        }

        return template;
    }

    private String getTextValue(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && fieldNode.isTextual() ? fieldNode.asText() : null;
    }

    private Boolean getBooleanValue(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && fieldNode.isBoolean() ? fieldNode.asBoolean() : null;
    }

    private Object nodeToObject(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isNumber()) {
            return node.numberValue();
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isNull()) {
            return null;
        }
        return node.toString();
    }

    /**
     * Check if a prompt template is MCP-enabled
     */
    public boolean isMCPEnabled(PromptTemplate template) {
        return template != null && template.getMcp() != null && Boolean.TRUE.equals(template.getMcp().getEnabled());
    }

    /**
     * Convert a prompt template to MCP Prompt format for listing
     */
    public MCPPrompt toMCPPrompt(PromptTemplate template) {
        if (!isMCPEnabled(template)) {
            return null;
        }

        MCPConfiguration mcpConfig = template.getMcp();
        MCPPrompt prompt = new MCPPrompt();

        prompt.setName(mcpConfig.getName() != null ? mcpConfig.getName() : template.getTemplateId());
        prompt.setDescription(mcpConfig.getDescription() != null ? mcpConfig.getDescription() : template.getDescription());

        // Derive arguments from variables if not explicitly specified
        List<MCPArgument> arguments = mcpConfig.getArguments();
        if (arguments == null || arguments.isEmpty()) {
            arguments = deriveArgumentsFromVariables(template.getVariables());
        }
        if (!arguments.isEmpty()) {
            prompt.setArguments(arguments);
        }

        return prompt;
    }

    /**
     * Derive MCP arguments from template variables
     */
    private List<MCPArgument> deriveArgumentsFromVariables(Map<String, VariableSchema> variables) {
        List<MCPArgument> arguments = new ArrayList<>();
        if (variables == null) {
            return arguments;
        }

        for (Map.Entry<String, VariableSchema> entry : variables.entrySet()) {
            MCPArgument arg = new MCPArgument();
            arg.setName(entry.getKey());
            arg.setDescription(entry.getValue().getDescription());
            arg.setRequired(entry.getValue().getRequired());
            arguments.add(arg);
        }

        return arguments;
    }

    /**
     * Render a prompt template with the provided arguments
     */
    public String renderTemplate(String template, Map<String, Object> args) {
        if (template == null || args == null) {
            return template;
        }

        String rendered = template;

        // Simple {{variable}} substitution
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String placeholder = "\\{\\{" + entry.getKey() + "\\}\\}";
            String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            rendered = rendered.replaceAll(placeholder, Matcher.quoteReplacement(value));
        }

        // Handle {{#if variable}} ... {{/if}} blocks
        rendered = processConditionalBlocks(rendered, args);

        return rendered;
    }

    /**
     * Process {{#if variable}} ... {{/if}} conditional blocks
     */
    private String processConditionalBlocks(String template, Map<String, Object> args) {
        Matcher matcher = IF_BLOCK_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String content = matcher.group(2);

            Object value = args.get(varName);
            // Truthy check: non-null, non-empty string, non-false
            boolean isTruthy = value != null &&
                    !Boolean.FALSE.equals(value) &&
                    !(value instanceof String && ((String) value).isEmpty());

            matcher.appendReplacement(result, Matcher.quoteReplacement(isTruthy ? content : ""));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Parse content and render with arguments
     */
    public String parseAndRender(String content, String contentType, Map<String, Object> args) {
        PromptTemplate template = parseContent(content, contentType);
        if (template == null || template.getTemplate() == null) {
            return null;
        }
        return renderTemplate(template.getTemplate(), args);
    }
}
