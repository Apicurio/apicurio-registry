package io.apicurio.registry.agents.types;

import io.apicurio.registry.agents.content.AgentCardContentAccepter;
import io.apicurio.registry.agents.content.PromptTemplateContentAccepter;
import io.apicurio.registry.agents.rules.validity.McpToolContentValidator;
import io.apicurio.registry.agents.rules.validity.ModelSchemaContentValidator;
import io.apicurio.registry.content.canon.YamlContentCanonicalizer;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import io.apicurio.registry.types.provider.StandardArtifactTypeProviderRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the agent artifact types are discovered through ServiceLoader and slot into the
 * same detection order they had when they were registered directly by core.
 */
class AgentArtifactTypeProviderContributorTest {

    private static final List<String> EXPECTED_PROVIDER_ORDER = List.of(
            ArtifactType.PROTOBUF,
            ArtifactType.OPENAPI,
            ArtifactType.ASYNCAPI,
            ArtifactType.JSON,
            ArtifactType.AVRO,
            ArtifactType.GRAPHQL,
            ArtifactType.KCONNECT,
            ArtifactType.WSDL,
            ArtifactType.XSD,
            ArtifactType.XML,
            ArtifactType.AGENT_CARD,
            ArtifactType.MCP_TOOL,
            ArtifactType.ICEBERG_TABLE,
            ArtifactType.ICEBERG_VIEW,
            ArtifactType.OPENRPC,
            ArtifactType.MODEL_SCHEMA,
            ArtifactType.PROMPT_TEMPLATE,
            ArtifactType.ODCS_CONTRACT,
            ArtifactType.THRIFT
    );

    @Test
    void testContributorDeclaresExactlyTheFourAgentTypes() {
        assertEquals(List.of(ArtifactType.AGENT_CARD, ArtifactType.MCP_TOOL, ArtifactType.MODEL_SCHEMA,
                ArtifactType.PROMPT_TEMPLATE),
                List.copyOf(new AgentArtifactTypeProviderContributor().getProviderConfigs().keySet()));
    }

    @Test
    void testStandardProvidersIncludeAgentTypesInOriginalDetectionOrder() {
        List<String> types = StandardArtifactTypeProviderRegistry.createStandardProviders().stream()
                .map(ArtifactTypeUtilProvider::getArtifactType).collect(Collectors.toList());
        assertEquals(EXPECTED_PROVIDER_ORDER, types);
    }

    @Test
    void testFactoryResolvesAgentTypeComponents() {
        DefaultArtifactTypeUtilProviderImpl factory = new DefaultArtifactTypeUtilProviderImpl(true);
        assertEquals(EXPECTED_PROVIDER_ORDER, factory.getAllArtifactTypes());

        assertInstanceOf(AgentCardContentAccepter.class,
                factory.getArtifactTypeProvider(ArtifactType.AGENT_CARD).getContentAccepter());
        assertInstanceOf(McpToolContentValidator.class,
                factory.getArtifactTypeProvider(ArtifactType.MCP_TOOL).getContentValidator());
        assertInstanceOf(ModelSchemaContentValidator.class,
                factory.getArtifactTypeProvider(ArtifactType.MODEL_SCHEMA).getContentValidator());

        ArtifactTypeUtilProvider prompt = factory.getArtifactTypeProvider(ArtifactType.PROMPT_TEMPLATE);
        assertInstanceOf(PromptTemplateContentAccepter.class, prompt.getContentAccepter());
        assertInstanceOf(YamlContentCanonicalizer.class, prompt.getContentCanonicalizer());
        assertEquals(Set.of(ContentTypes.APPLICATION_JSON, ContentTypes.APPLICATION_YAML,
                ContentTypes.TEXT_PROMPT_TEMPLATE), prompt.getContentTypes());
    }

    @Test
    void testSupportsReferencesWithContext() {
        DefaultArtifactTypeUtilProviderImpl factory = new DefaultArtifactTypeUtilProviderImpl(true);
        assertFalse(factory.getArtifactTypeProvider(ArtifactType.AGENT_CARD).supportsReferencesWithContext());
        assertFalse(factory.getArtifactTypeProvider(ArtifactType.MCP_TOOL).supportsReferencesWithContext());
        assertTrue(factory.getArtifactTypeProvider(ArtifactType.MODEL_SCHEMA).supportsReferencesWithContext());
        assertTrue(factory.getArtifactTypeProvider(ArtifactType.PROMPT_TEMPLATE).supportsReferencesWithContext());
    }
}
