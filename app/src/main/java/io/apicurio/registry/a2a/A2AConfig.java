package io.apicurio.registry.a2a;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_A2A;

/**
 * Configuration properties for A2A (Agent2Agent) protocol support.
 */
@Singleton
public class A2AConfig {

    @ConfigProperty(name = "apicurio.a2a.enabled", defaultValue = "false")
    @Info(category = CATEGORY_A2A, description = "Enable A2A protocol support", availableSince = "3.0.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.a2a.agent.name", defaultValue = "Apicurio Registry")
    @Info(category = CATEGORY_A2A, description = "Name of the registry agent for A2A discovery", availableSince = "3.0.0")
    String agentName;

    @ConfigProperty(name = "apicurio.a2a.agent.description", defaultValue = "API and Schema Registry with A2A Agent support")
    @Info(category = CATEGORY_A2A, description = "Description of the registry agent", availableSince = "3.0.0")
    String agentDescription;

    @ConfigProperty(name = "apicurio.a2a.agent.version")
    @Info(category = CATEGORY_A2A, description = "Version of the registry agent (defaults to app version)", availableSince = "3.0.0")
    Optional<String> agentVersion;

    @ConfigProperty(name = "apicurio.a2a.agent.url")
    @Info(category = CATEGORY_A2A, description = "Base URL for the registry agent's A2A endpoint", availableSince = "3.0.0")
    Optional<String> agentUrl;

    @ConfigProperty(name = "apicurio.a2a.agent.provider.organization", defaultValue = "Apicurio")
    @Info(category = CATEGORY_A2A, description = "Organization name for the agent provider", availableSince = "3.0.0")
    String providerOrganization;

    @ConfigProperty(name = "apicurio.a2a.agent.provider.url", defaultValue = "https://www.apicur.io")
    @Info(category = CATEGORY_A2A, description = "URL for the agent provider", availableSince = "3.0.0")
    String providerUrl;

    @ConfigProperty(name = "apicurio.a2a.agent.capabilities.streaming", defaultValue = "false")
    @Info(category = CATEGORY_A2A, description = "Whether the agent supports streaming", availableSince = "3.0.0")
    boolean capabilitiesStreaming;

    @ConfigProperty(name = "apicurio.a2a.agent.capabilities.push-notifications", defaultValue = "false")
    @Info(category = CATEGORY_A2A, description = "Whether the agent supports push notifications", availableSince = "3.0.0")
    boolean capabilitiesPushNotifications;

    @ConfigProperty(name = "apicurio.a2a.public-discovery.enabled", defaultValue = "true")
    @Info(category = CATEGORY_A2A, description = "Enable public (unauthenticated) agent discovery endpoint", availableSince = "3.0.0")
    boolean publicDiscoveryEnabled;

    @ConfigProperty(name = "apicurio.a2a.entitlements.enabled", defaultValue = "true")
    @Info(category = CATEGORY_A2A, description = "Enable entitlement-based agent filtering", availableSince = "3.0.0")
    boolean entitlementsEnabled;

    @ConfigProperty(name = "apicurio.a2a.default-visibility", defaultValue = "entitled")
    @Info(category = CATEGORY_A2A, description = "Default visibility for new Agent Card artifacts (public, entitled, private)", availableSince = "3.0.0")
    String defaultVisibility;

    @ConfigProperty(name = "apicurio.a2a.agent.protocol-version", defaultValue = "1.0")
    @Info(category = CATEGORY_A2A, description = "A2A protocol version supported by the registry agent", availableSince = "3.0.0")
    String protocolVersion;

    @ConfigProperty(name = "apicurio.a2a.agent.documentation-url")
    @Info(category = CATEGORY_A2A, description = "URL to the registry agent documentation", availableSince = "3.0.0")
    Optional<String> documentationUrl;

    @ConfigProperty(name = "apicurio.a2a.agent.icon-url")
    @Info(category = CATEGORY_A2A, description = "URL to the registry agent icon", availableSince = "3.0.0")
    Optional<String> iconUrl;

    public boolean isEnabled() {
        return enabled;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getAgentDescription() {
        return agentDescription;
    }

    public Optional<String> getAgentVersion() {
        return agentVersion;
    }

    public Optional<String> getAgentUrl() {
        return agentUrl;
    }

    public String getProviderOrganization() {
        return providerOrganization;
    }

    public String getProviderUrl() {
        return providerUrl;
    }

    public boolean isCapabilitiesStreaming() {
        return capabilitiesStreaming;
    }

    public boolean isCapabilitiesPushNotifications() {
        return capabilitiesPushNotifications;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public Optional<String> getDocumentationUrl() {
        return documentationUrl;
    }

    public Optional<String> getIconUrl() {
        return iconUrl;
    }

    public boolean isPublicDiscoveryEnabled() {
        return publicDiscoveryEnabled;
    }

    public boolean isEntitlementsEnabled() {
        return entitlementsEnabled;
    }

    public String getDefaultVisibility() {
        return defaultVisibility;
    }
}
