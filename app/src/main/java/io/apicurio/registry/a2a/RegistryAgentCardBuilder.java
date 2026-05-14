package io.apicurio.registry.a2a;

import io.apicurio.registry.a2a.rest.beans.AgentCapabilities;
import io.apicurio.registry.a2a.rest.beans.AgentCard;
import io.apicurio.registry.a2a.rest.beans.AgentInterface;
import io.apicurio.registry.a2a.rest.beans.AgentProvider;
import io.apicurio.registry.a2a.rest.beans.AgentSkill;
import io.apicurio.registry.a2a.rest.beans.SecurityScheme;
import io.apicurio.registry.auth.AuthConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the Agent Card that represents Apicurio Registry as an A2A agent.
 * This agent card is served at /.well-known/a2a per the A2A v1.0 protocol.
 */
@ApplicationScoped
public class RegistryAgentCardBuilder {

    @Inject
    A2AConfig a2aConfig;

    @Inject
    io.apicurio.registry.core.System system;

    @Inject
    AuthConfig authConfig;

    public AgentCard build(String baseUrl) {
        String agentUrl = a2aConfig.getAgentUrl().orElse(baseUrl);

        AgentCard.Builder builder = AgentCard.builder()
                .name(a2aConfig.getAgentName())
                .description(a2aConfig.getAgentDescription())
                .version(a2aConfig.getAgentVersion().orElse(system.getVersion()))
                .protocolVersion(a2aConfig.getProtocolVersion())
                .supportedInterfaces(buildInterfaces(agentUrl))
                .provider(buildProvider())
                .capabilities(buildCapabilities())
                .skills(buildSkills())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .securitySchemes(buildSecuritySchemes());

        a2aConfig.getDocumentationUrl().ifPresent(builder::documentationUrl);
        a2aConfig.getIconUrl().ifPresent(builder::iconUrl);

        return builder.build();
    }

    private List<AgentInterface> buildInterfaces(String agentUrl) {
        return List.of(
                new AgentInterface(agentUrl, "http+json", a2aConfig.getProtocolVersion())
        );
    }

    private AgentProvider buildProvider() {
        return AgentProvider.builder()
                .organization(a2aConfig.getProviderOrganization())
                .url(a2aConfig.getProviderUrl())
                .build();
    }

    private AgentCapabilities buildCapabilities() {
        return AgentCapabilities.builder()
                .streaming(a2aConfig.isCapabilitiesStreaming())
                .pushNotifications(a2aConfig.isCapabilitiesPushNotifications())
                .extendedAgentCard(false)
                .build();
    }

    private List<AgentSkill> buildSkills() {
        return List.of(
                AgentSkill.builder()
                        .id("schema-validation")
                        .name("Schema Validation")
                        .description("Validate schemas against format specifications"
                                + " (Avro, JSON Schema, Protobuf, OpenAPI, etc.)")
                        .tags(List.of("schema", "validation", "avro", "json-schema",
                                "protobuf", "openapi"))
                        .build(),
                AgentSkill.builder()
                        .id("schema-search")
                        .name("Schema Search")
                        .description("Search for schemas and APIs in the registry"
                                + " by name, description, labels, or content")
                        .tags(List.of("schema", "search", "discovery"))
                        .build(),
                AgentSkill.builder()
                        .id("artifact-management")
                        .name("Artifact Management")
                        .description("Create, update, and manage schema and API artifacts"
                                + " with full version history")
                        .tags(List.of("artifact", "crud", "versioning"))
                        .build(),
                AgentSkill.builder()
                        .id("compatibility-check")
                        .name("Compatibility Check")
                        .description("Check schema compatibility between versions"
                                + " using configurable compatibility rules")
                        .tags(List.of("compatibility", "evolution", "breaking-changes"))
                        .build(),
                AgentSkill.builder()
                        .id("agent-discovery")
                        .name("Agent Discovery")
                        .description("Discover and manage A2A agent cards"
                                + " registered in the registry")
                        .tags(List.of("a2a", "agent", "discovery"))
                        .build()
        );
    }

    private Map<String, SecurityScheme> buildSecuritySchemes() {
        Map<String, SecurityScheme> schemes = new LinkedHashMap<>();

        if (authConfig.isOidcAuthEnabled()) {
            schemes.put("bearer", SecurityScheme.httpAuth("Bearer"));
        }
        if (authConfig.isBasicAuthEnabled()) {
            schemes.put("basic", SecurityScheme.httpAuth("Basic"));
        }

        return schemes;
    }
}
