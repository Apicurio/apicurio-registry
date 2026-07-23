package io.apicurio.registry.a2a;

import io.apicurio.registry.auth.AuthConfig;
import io.apicurio.registry.rest.v3.beans.A2aSecurityScheme;
import io.apicurio.registry.rest.v3.beans.AgentCapabilities;
import io.apicurio.registry.rest.v3.beans.AgentCard;
import io.apicurio.registry.rest.v3.beans.AgentInterface;
import io.apicurio.registry.rest.v3.beans.AgentProvider;
import io.apicurio.registry.rest.v3.beans.AgentSkill;
import io.apicurio.registry.rest.v3.beans.SecuritySchemes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

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

        var builder = AgentCard.builder()
                .name(a2aConfig.getAgentName())
                .description(a2aConfig.getAgentDescription())
                .version(a2aConfig.getAgentVersion().orElse(system.getVersion()))
                .protocolVersion(a2aConfig.getProtocolVersion())
                .supportedInterfaces(buildInterfaces(agentUrl))
                .provider(buildProvider())
                .capabilities(buildCapabilities())
                .skills(buildSkills())
                .defaultInputModes(List.of("text/plain"))
                .defaultOutputModes(List.of("text/plain"))
                .securitySchemes(buildSecuritySchemes());

        a2aConfig.getDocumentationUrl().ifPresent(builder::documentationUrl);
        a2aConfig.getIconUrl().ifPresent(builder::iconUrl);

        return builder.build();
    }

    private List<AgentInterface> buildInterfaces(String agentUrl) {
        return List.of(
                AgentInterface.builder()
                        .url(agentUrl)
                        .protocolBinding("http+json")
                        .protocolVersion(a2aConfig.getProtocolVersion())
                        .build()
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

    private SecuritySchemes buildSecuritySchemes() {
        SecuritySchemes schemes = new SecuritySchemes();

        if (authConfig.isOidcAuthEnabled()) {
            schemes.setAdditionalProperty("bearer",
                    A2aSecurityScheme.builder().type("httpAuth").scheme("Bearer").build());
        }
        if (authConfig.isBasicAuthEnabled()) {
            schemes.setAdditionalProperty("basic",
                    A2aSecurityScheme.builder().type("httpAuth").scheme("Basic").build());
        }
        if (a2aConfig.isApiKeyAuthEnabled()) {
            schemes.setAdditionalProperty("apiKey",
                    A2aSecurityScheme.builder().type("apiKey").name("X-API-Key")
                            .location("header").build());
        }

        return schemes;
    }
}
