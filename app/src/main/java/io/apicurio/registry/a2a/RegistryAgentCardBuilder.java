package io.apicurio.registry.a2a;

import io.apicurio.registry.a2a.rest.beans.AgentAuthentication;
import io.apicurio.registry.a2a.rest.beans.AgentCapabilities;
import io.apicurio.registry.a2a.rest.beans.AgentCard;
import io.apicurio.registry.a2a.rest.beans.AgentProvider;
import io.apicurio.registry.a2a.rest.beans.AgentSkill;
import io.apicurio.registry.auth.AuthConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the Agent Card that represents Apicurio Registry as an A2A agent.
 * This agent card is served at /.well-known/agent.json per the A2A protocol.
 */
@ApplicationScoped
public class RegistryAgentCardBuilder {

    @Inject
    A2AConfig a2aConfig;

    @Inject
    io.apicurio.registry.core.System system;

    @Inject
    AuthConfig authConfig;

    /**
     * Builds and returns the Agent Card for this Apicurio Registry instance.
     *
     * @param baseUrl the base URL of the registry (used if not configured)
     * @return the Agent Card
     */
    public AgentCard build(String baseUrl) {
        return AgentCard.builder()
                .name(a2aConfig.getAgentName())
                .description(a2aConfig.getAgentDescription())
                .version(a2aConfig.getAgentVersion().orElse(system.getVersion()))
                .url(a2aConfig.getAgentUrl().orElse(baseUrl))
                .provider(buildProvider())
                .capabilities(buildCapabilities())
                .skills(buildSkills())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .authentication(buildAuthentication())
                .supportsExtendedAgentCard(false)
                .build();
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
                .build();
    }

    private List<AgentSkill> buildSkills() {
        return List.of(
                AgentSkill.builder()
                        .id("schema-validation")
                        .name("Schema Validation")
                        .description("Validate schemas against format specifications (Avro, JSON Schema, Protobuf, OpenAPI, etc.)")
                        .tags(List.of("schema", "validation", "avro", "json-schema", "protobuf", "openapi"))
                        .build(),
                AgentSkill.builder()
                        .id("schema-search")
                        .name("Schema Search")
                        .description("Search for schemas and APIs in the registry by name, description, labels, or content")
                        .tags(List.of("schema", "search", "discovery"))
                        .build(),
                AgentSkill.builder()
                        .id("artifact-management")
                        .name("Artifact Management")
                        .description("Create, update, and manage schema and API artifacts with full version history")
                        .tags(List.of("artifact", "crud", "versioning"))
                        .build(),
                AgentSkill.builder()
                        .id("compatibility-check")
                        .name("Compatibility Check")
                        .description("Check schema compatibility between versions using configurable compatibility rules")
                        .tags(List.of("compatibility", "evolution", "breaking-changes"))
                        .build(),
                AgentSkill.builder()
                        .id("agent-discovery")
                        .name("Agent Discovery")
                        .description("Discover and manage A2A agent cards registered in the registry")
                        .tags(List.of("a2a", "agent", "discovery"))
                        .build()
        );
    }

    private AgentAuthentication buildAuthentication() {
        List<String> schemes = new ArrayList<>();

        if (authConfig.isOidcAuthEnabled()) {
            schemes.add("bearer");
        }
        if (authConfig.isBasicAuthEnabled()) {
            schemes.add("basic");
        }

        if (schemes.isEmpty()) {
            schemes.add("none");
        }

        return AgentAuthentication.builder()
                .schemes(schemes)
                .build();
    }
}
