package io.apicurio.registry.agents.types;

import io.apicurio.registry.agents.content.AgentCardContentAccepter;
import io.apicurio.registry.agents.content.McpToolContentAccepter;
import io.apicurio.registry.agents.content.ModelSchemaContentAccepter;
import io.apicurio.registry.agents.content.PromptTemplateContentAccepter;
import io.apicurio.registry.agents.content.dereference.ModelSchemaDereferencer;
import io.apicurio.registry.agents.content.dereference.PromptTemplateDereferencer;
import io.apicurio.registry.agents.content.extract.AgentCardContentExtractor;
import io.apicurio.registry.agents.content.extract.AgentCardStructuredContentExtractor;
import io.apicurio.registry.agents.content.extract.McpToolContentExtractor;
import io.apicurio.registry.agents.content.extract.McpToolStructuredContentExtractor;
import io.apicurio.registry.agents.content.extract.ModelSchemaContentExtractor;
import io.apicurio.registry.agents.content.extract.ModelSchemaStructuredContentExtractor;
import io.apicurio.registry.agents.content.extract.PromptTemplateContentExtractor;
import io.apicurio.registry.agents.content.extract.PromptTemplateStructuredContentExtractor;
import io.apicurio.registry.agents.content.refs.ModelSchemaReferenceFinder;
import io.apicurio.registry.agents.content.refs.PromptTemplateReferenceFinder;
import io.apicurio.registry.agents.rules.compatibility.AgentCardCompatibilityChecker;
import io.apicurio.registry.agents.rules.compatibility.McpToolCompatibilityChecker;
import io.apicurio.registry.agents.rules.compatibility.ModelSchemaCompatibilityChecker;
import io.apicurio.registry.agents.rules.compatibility.PromptTemplateCompatibilityChecker;
import io.apicurio.registry.agents.rules.validity.AgentCardContentValidator;
import io.apicurio.registry.agents.rules.validity.McpToolContentValidator;
import io.apicurio.registry.agents.rules.validity.ModelSchemaContentValidator;
import io.apicurio.registry.agents.rules.validity.PromptTemplateContentValidator;
import io.apicurio.registry.content.canon.YamlContentCanonicalizer;
import io.apicurio.registry.json.content.canon.JsonContentCanonicalizer;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.provider.ArtifactTypeProviderContributor;
import io.apicurio.registry.types.provider.ProviderConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Contributes the AI/agent artifact types (AGENT_CARD, MCP_TOOL, MODEL_SCHEMA, PROMPT_TEMPLATE) to the
 * standard artifact type registry. Discovered through {@link java.util.ServiceLoader}.
 */
public class AgentArtifactTypeProviderContributor implements ArtifactTypeProviderContributor {

    @Override
    public Map<String, ProviderConfig> getProviderConfigs() {
        Map<String, ProviderConfig> providers = new LinkedHashMap<>();
        providers.put(ArtifactType.AGENT_CARD, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON))
                .accepter(AgentCardContentAccepter::new)
                .compatibilityChecker(AgentCardCompatibilityChecker::new)
                .canonicalizer(JsonContentCanonicalizer::new)
                .validator(AgentCardContentValidator::new)
                .extractor(AgentCardContentExtractor::new)
                .structuredContentExtractor(AgentCardStructuredContentExtractor::new)
                .build());
        providers.put(ArtifactType.MCP_TOOL, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON))
                .accepter(McpToolContentAccepter::new)
                .compatibilityChecker(McpToolCompatibilityChecker::new)
                .canonicalizer(JsonContentCanonicalizer::new)
                .validator(McpToolContentValidator::new)
                .extractor(McpToolContentExtractor::new)
                .structuredContentExtractor(McpToolStructuredContentExtractor::new)
                .build());
        providers.put(ArtifactType.MODEL_SCHEMA, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON, ContentTypes.APPLICATION_YAML))
                .accepter(ModelSchemaContentAccepter::new)
                .compatibilityChecker(ModelSchemaCompatibilityChecker::new)
                .canonicalizer(JsonContentCanonicalizer::new)
                .validator(ModelSchemaContentValidator::new)
                .extractor(ModelSchemaContentExtractor::new)
                .dereferencer(ModelSchemaDereferencer::new)
                .referenceFinder(ModelSchemaReferenceFinder::new)
                .structuredContentExtractor(ModelSchemaStructuredContentExtractor::new)
                .supportsReferencesWithContext(true)
                .build());
        providers.put(ArtifactType.PROMPT_TEMPLATE, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON, ContentTypes.APPLICATION_YAML,
                        ContentTypes.TEXT_PROMPT_TEMPLATE))
                .accepter(PromptTemplateContentAccepter::new)
                .compatibilityChecker(PromptTemplateCompatibilityChecker::new)
                .canonicalizer(YamlContentCanonicalizer::new)
                .validator(PromptTemplateContentValidator::new)
                .extractor(PromptTemplateContentExtractor::new)
                .dereferencer(PromptTemplateDereferencer::new)
                .referenceFinder(PromptTemplateReferenceFinder::new)
                .structuredContentExtractor(PromptTemplateStructuredContentExtractor::new)
                .supportsReferencesWithContext(true)
                .build());
        return providers;
    }
}
