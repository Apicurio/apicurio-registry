package io.apicurio.registry.a2a;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.config.ExperimentalFeaturesConfig;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_A2A;

/**
 * Configuration properties for A2A (Agent2Agent) protocol support.
 */
@Singleton
public class A2AConfig {

    @Inject
    Logger log;

    @Inject
    ExperimentalFeaturesConfig experimentalConfig;

    @ConfigProperty(name = "apicurio.a2a.enabled", defaultValue = "true")
    @Info(category = CATEGORY_A2A, description = "Enable A2A protocol support", availableSince = "3.0.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.a2a.agent.name", defaultValue = "Apicurio Registry")
    @Info(category = CATEGORY_A2A, description = "Name of the registry agent for A2A discovery", availableSince = "3.0.0", experimental = true)
    String agentName;

    @ConfigProperty(name = "apicurio.a2a.agent.description", defaultValue = "API and Schema Registry with A2A Agent support")
    @Info(category = CATEGORY_A2A, description = "Description of the registry agent", availableSince = "3.0.0", experimental = true)
    String agentDescription;

    @ConfigProperty(name = "apicurio.a2a.agent.version")
    @Info(category = CATEGORY_A2A, description = "Version of the registry agent (defaults to app version)", availableSince = "3.0.0", experimental = true)
    Optional<String> agentVersion;

    @ConfigProperty(name = "apicurio.a2a.agent.url")
    @Info(category = CATEGORY_A2A, description = "Base URL for the registry agent's A2A endpoint", availableSince = "3.0.0", experimental = true)
    Optional<String> agentUrl;

    @ConfigProperty(name = "apicurio.a2a.agent.provider.organization", defaultValue = "Apicurio")
    @Info(category = CATEGORY_A2A, description = "Organization name for the agent provider", availableSince = "3.0.0", experimental = true)
    String providerOrganization;

    @ConfigProperty(name = "apicurio.a2a.agent.provider.url", defaultValue = "https://www.apicur.io")
    @Info(category = CATEGORY_A2A, description = "URL for the agent provider", availableSince = "3.0.0", experimental = true)
    String providerUrl;

    @ConfigProperty(name = "apicurio.a2a.agent.capabilities.streaming", defaultValue = "false")
    @Info(category = CATEGORY_A2A, description = "Whether the agent supports streaming", availableSince = "3.0.0", experimental = true)
    boolean capabilitiesStreaming;

    @ConfigProperty(name = "apicurio.a2a.agent.capabilities.push-notifications", defaultValue = "false")
    @Info(category = CATEGORY_A2A, description = "Whether the agent supports push notifications", availableSince = "3.0.0", experimental = true)
    boolean capabilitiesPushNotifications;

    @PostConstruct
    void onConstruct() {
        if (enabled && !experimentalConfig.isExperimentalFeaturesEnabled()) {
            log.info("A2A protocol support is enabled but the experimental features gate is disabled. "
                    + "Set 'apicurio.features.experimental.enabled=true' to activate A2A.");
        }
    }

    public boolean isEnabled() {
        return enabled && experimentalConfig.isExperimentalFeaturesEnabled();
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
}
