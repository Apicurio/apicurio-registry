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

import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import io.apicurio.registry.rest.client.RegistryClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CDI bean for fetching and caching prompt templates from Apicurio Registry.
 * <p>
 * This bean provides a convenient way to fetch prompt templates from Apicurio Registry
 * and use them with LangChain4j. Templates are cached in memory for efficiency.
 * <p>
 * Example usage:
 * <pre>{@code
 * @Inject
 * ApicurioPromptRegistry promptRegistry;
 *
 * @Inject
 * ChatLanguageModel model;
 *
 * public String chat(String question, String context) {
 *     ApicurioPromptTemplate template = promptRegistry.getPrompt("qa-assistant", "1.0");
 *     Prompt prompt = template.apply(Map.of(
 *         "question", question,
 *         "context", context
 *     ));
 *     return model.generate(prompt.text());
 * }
 * }</pre>
 *
 * @author Carles Arnal
 */
@ApplicationScoped
public class ApicurioPromptRegistry {

    @Inject
    RegistryClient client;

    @Inject
    ApicurioRegistryConfig config;

    private final Map<String, ApicurioPromptTemplate> cache = new ConcurrentHashMap<>();

    /**
     * Gets a prompt template from the registry using the default group and latest version.
     *
     * @param artifactId the artifact ID of the prompt template
     * @return the prompt template
     */
    public ApicurioPromptTemplate getPrompt(String artifactId) {
        return getPrompt(artifactId, null);
    }

    /**
     * Gets a prompt template from the registry using the default group.
     *
     * @param artifactId the artifact ID of the prompt template
     * @param version    the version expression, or null for latest
     * @return the prompt template
     */
    public ApicurioPromptTemplate getPrompt(String artifactId, String version) {
        return getPrompt(config.defaultGroup(), artifactId, version);
    }

    /**
     * Gets a prompt template from the registry.
     *
     * @param groupId    the group ID containing the artifact
     * @param artifactId the artifact ID of the prompt template
     * @param version    the version expression, or null for latest
     * @return the prompt template
     */
    public ApicurioPromptTemplate getPrompt(String groupId, String artifactId, String version) {
        if (!config.cacheEnabled()) {
            return new ApicurioPromptTemplate(client, groupId, artifactId, version);
        }

        String cacheKey = buildCacheKey(groupId, artifactId, version);
        return cache.computeIfAbsent(cacheKey, k ->
                new ApicurioPromptTemplate(client, groupId, artifactId, version)
        );
    }

    /**
     * Gets a prompt template from the registry as a LangChain4j PromptTemplate.
     *
     * @param artifactId the artifact ID of the prompt template
     * @return the LangChain4j PromptTemplate
     */
    public PromptTemplate getLangChain4jPrompt(String artifactId) {
        return getPrompt(artifactId).toLangChain4j();
    }

    /**
     * Gets a prompt template from the registry as a LangChain4j PromptTemplate.
     *
     * @param artifactId the artifact ID of the prompt template
     * @param version    the version expression, or null for latest
     * @return the LangChain4j PromptTemplate
     */
    public PromptTemplate getLangChain4jPrompt(String artifactId, String version) {
        return getPrompt(artifactId, version).toLangChain4j();
    }

    /**
     * Gets a prompt template from the registry as a LangChain4j PromptTemplate.
     *
     * @param groupId    the group ID containing the artifact
     * @param artifactId the artifact ID of the prompt template
     * @param version    the version expression, or null for latest
     * @return the LangChain4j PromptTemplate
     */
    public PromptTemplate getLangChain4jPrompt(String groupId, String artifactId, String version) {
        return getPrompt(groupId, artifactId, version).toLangChain4j();
    }

    /**
     * Gets a prompt template and immediately renders it with the given variables.
     *
     * @param artifactId the artifact ID of the prompt template
     * @param variables  the variables to substitute
     * @return the rendered prompt
     */
    public Prompt render(String artifactId, Map<String, Object> variables) {
        return render(config.defaultGroup(), artifactId, null, variables);
    }

    /**
     * Gets a prompt template and immediately renders it with the given variables.
     *
     * @param artifactId the artifact ID of the prompt template
     * @param version    the version expression, or null for latest
     * @param variables  the variables to substitute
     * @return the rendered prompt
     */
    public Prompt render(String artifactId, String version, Map<String, Object> variables) {
        return render(config.defaultGroup(), artifactId, version, variables);
    }

    /**
     * Gets a prompt template and immediately renders it with the given variables.
     *
     * @param groupId    the group ID containing the artifact
     * @param artifactId the artifact ID of the prompt template
     * @param version    the version expression, or null for latest
     * @param variables  the variables to substitute
     * @return the rendered prompt
     */
    public Prompt render(String groupId, String artifactId, String version, Map<String, Object> variables) {
        ApicurioPromptTemplate template = getPrompt(groupId, artifactId, version);
        return template.apply(variables);
    }

    /**
     * Gets the raw template content from the registry.
     *
     * @param artifactId the artifact ID of the prompt template
     * @return the raw template content
     */
    public String getTemplateContent(String artifactId) {
        return getTemplateContent(config.defaultGroup(), artifactId, null);
    }

    /**
     * Gets the raw template content from the registry.
     *
     * @param groupId    the group ID containing the artifact
     * @param artifactId the artifact ID of the prompt template
     * @param version    the version expression, or null for latest
     * @return the raw template content
     */
    public String getTemplateContent(String groupId, String artifactId, String version) {
        ApicurioPromptTemplate template = getPrompt(groupId, artifactId, version);
        return template.getTemplate();
    }

    /**
     * Clears the entire prompt template cache.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Evicts a specific artifact from the cache.
     *
     * @param artifactId the artifact ID to evict
     */
    public void evict(String artifactId) {
        cache.keySet().removeIf(key -> key.contains("/" + artifactId + "/"));
    }

    /**
     * Evicts a specific artifact version from the cache.
     *
     * @param groupId    the group ID
     * @param artifactId the artifact ID
     * @param version    the version
     */
    public void evict(String groupId, String artifactId, String version) {
        String cacheKey = buildCacheKey(groupId, artifactId, version);
        cache.remove(cacheKey);
    }

    /**
     * Refreshes a cached prompt template by re-fetching it from the registry.
     *
     * @param artifactId the artifact ID to refresh
     */
    public void refresh(String artifactId) {
        refresh(config.defaultGroup(), artifactId, null);
    }

    /**
     * Refreshes a cached prompt template by re-fetching it from the registry.
     *
     * @param groupId    the group ID
     * @param artifactId the artifact ID
     * @param version    the version
     */
    public void refresh(String groupId, String artifactId, String version) {
        String cacheKey = buildCacheKey(groupId, artifactId, version);
        ApicurioPromptTemplate template = cache.get(cacheKey);
        if (template != null) {
            template.refresh();
        }
    }

    private String buildCacheKey(String groupId, String artifactId, String version) {
        return groupId + "/" + artifactId + "/" + (version != null ? version : "latest");
    }
}
