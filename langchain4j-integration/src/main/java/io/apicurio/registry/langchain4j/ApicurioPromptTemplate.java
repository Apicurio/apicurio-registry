/*
 * Copyright 2024 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.langchain4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import io.apicurio.registry.rest.client.RegistryClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

/**
 * A prompt template wrapper that fetches its content from Apicurio Registry.
 * <p>
 * This class provides seamless integration between Apicurio Registry's prompt template management
 * and LangChain4j's prompt system. Templates stored in the registry using the PROMPT_TEMPLATE
 * artifact type can be fetched and used directly with LangChain4j.
 * <p>
 * The class provides two main ways to use templates:
 * <ol>
 *     <li>Direct variable substitution using the {@link #apply(Map)} method</li>
 *     <li>Conversion to LangChain4j's native {@link PromptTemplate} using {@link #toLangChain4j()}</li>
 * </ol>
 * <p>
 * Example usage:
 * <pre>{@code
 * RegistryClient client = ...; // Create or inject RegistryClient
 * ApicurioPromptTemplate template = new ApicurioPromptTemplate(
 *     client, "default", "summarization-v1", "1.0"
 * );
 *
 * // Option 1: Direct use
 * Prompt prompt = template.apply(Map.of(
 *     "document", "The quick brown fox...",
 *     "style", "concise"
 * ));
 *
 * // Option 2: Convert to LangChain4j PromptTemplate
 * PromptTemplate lc4jTemplate = template.toLangChain4j();
 * Prompt prompt = lc4jTemplate.apply(Map.of("document", "..."));
 *
 * String result = model.generate(prompt.text());
 * }</pre>
 *
 * @author Carles Arnal
 */
public class ApicurioPromptTemplate {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private final RegistryClient client;
    private final String groupId;
    private final String artifactId;
    private final String version;

    private volatile String cachedTemplate;
    private volatile JsonNode cachedVariables;

    /**
     * Creates a new ApicurioPromptTemplate that fetches the latest version.
     *
     * @param client     the Apicurio Registry client
     * @param groupId    the group ID containing the artifact
     * @param artifactId the artifact ID of the prompt template
     */
    public ApicurioPromptTemplate(RegistryClient client, String groupId, String artifactId) {
        this(client, groupId, artifactId, null);
    }

    /**
     * Creates a new ApicurioPromptTemplate for a specific version.
     *
     * @param client     the Apicurio Registry client
     * @param groupId    the group ID containing the artifact
     * @param artifactId the artifact ID of the prompt template
     * @param version    the version expression (e.g., "1.0", "branch=latest"), or null for latest
     */
    public ApicurioPromptTemplate(RegistryClient client, String groupId, String artifactId, String version) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.groupId = Objects.requireNonNull(groupId, "groupId must not be null");
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId must not be null");
        this.version = version;
    }

    /**
     * Applies the given variables to the template and returns a Prompt.
     * <p>
     * Variables are substituted using the {{variable}} syntax. For example,
     * if the template contains "Hello {{name}}", calling apply(Map.of("name", "World"))
     * will produce "Hello World".
     *
     * @param variables the variables to substitute in the template
     * @return a Prompt with the substituted content
     */
    public Prompt apply(Map<String, Object> variables) {
        String template = getTemplate();
        String rendered = substituteVariables(template, variables);
        return Prompt.from(rendered);
    }

    /**
     * Applies the given variables to the template and returns a Prompt.
     * <p>
     * This is an overload that accepts variable arguments as key-value pairs.
     *
     * @param variables the variables to substitute as key1, value1, key2, value2, ...
     * @return a Prompt with the substituted content
     */
    public Prompt apply(Object... variables) {
        if (variables.length % 2 != 0) {
            throw new IllegalArgumentException("Variables must be provided as key-value pairs");
        }
        Map<String, Object> variableMap = new java.util.HashMap<>();
        for (int i = 0; i < variables.length; i += 2) {
            variableMap.put(String.valueOf(variables[i]), variables[i + 1]);
        }
        return apply(variableMap);
    }

    /**
     * Converts this template to a LangChain4j PromptTemplate.
     * <p>
     * The conversion replaces the {{variable}} syntax with {variable} syntax
     * that LangChain4j uses.
     *
     * @return a LangChain4j PromptTemplate
     */
    public PromptTemplate toLangChain4j() {
        String template = getTemplate();
        // Convert {{variable}} to {variable} for LangChain4j compatibility
        String lc4jTemplate = template.replaceAll("\\{\\{([^}]+)\\}\\}", "{$1}");
        return PromptTemplate.from(lc4jTemplate);
    }

    /**
     * Gets the raw template content from the registry.
     * The template is cached after the first fetch.
     *
     * @return the template content
     */
    public String getTemplate() {
        if (cachedTemplate == null) {
            synchronized (this) {
                if (cachedTemplate == null) {
                    fetchAndParseTemplate();
                }
            }
        }
        return cachedTemplate;
    }

    /**
     * Gets the variable schema definitions from the prompt template.
     *
     * @return the variable definitions as a JsonNode, or null if not defined
     */
    public JsonNode getVariables() {
        if (cachedTemplate == null) {
            getTemplate(); // Ensure template is loaded
        }
        return cachedVariables;
    }

    /**
     * Gets the group ID.
     *
     * @return the group ID
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * Gets the artifact ID.
     *
     * @return the artifact ID
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * Gets the version expression.
     *
     * @return the version expression, or null if using latest
     */
    public String getVersion() {
        return version;
    }

    /**
     * Clears the cached template, forcing a fresh fetch on next access.
     */
    public void refresh() {
        synchronized (this) {
            cachedTemplate = null;
            cachedVariables = null;
        }
    }

    private void fetchAndParseTemplate() {
        String versionExpr = version != null ? version : "branch=latest";

        try {
            InputStream contentStream = client.groups().byGroupId(groupId)
                    .artifacts().byArtifactId(artifactId)
                    .versions().byVersionExpression(versionExpr)
                    .content().get();

            byte[] content = contentStream.readAllBytes();
            String contentStr = new String(content);

            JsonNode node = parseContent(contentStr);

            // Extract the template field
            if (node.has("template")) {
                cachedTemplate = node.get("template").asText();
            } else {
                // If no template field, use the entire content as template
                cachedTemplate = contentStr;
            }

            // Extract variables schema if present
            if (node.has("variables")) {
                cachedVariables = node.get("variables");
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch prompt template from registry: " +
                    groupId + "/" + artifactId + "/" + versionExpr, e);
        }
    }

    private JsonNode parseContent(String content) throws IOException {
        // Try YAML first, then JSON
        try {
            return YAML_MAPPER.readTree(content);
        } catch (Exception yamlError) {
            try {
                return JSON_MAPPER.readTree(content);
            } catch (Exception jsonError) {
                throw new IOException("Failed to parse prompt template as YAML or JSON", jsonError);
            }
        }
    }

    private String substituteVariables(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = formatValue(entry.getValue());
            result = result.replace(placeholder, value);
        }
        return result;
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        // For complex types, use JSON serialization
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            return String.valueOf(value);
        }
    }

    @Override
    public String toString() {
        return "ApicurioPromptTemplate{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                '}';
    }
}
