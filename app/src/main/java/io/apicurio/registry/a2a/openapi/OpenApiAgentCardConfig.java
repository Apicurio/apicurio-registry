package io.apicurio.registry.a2a.openapi;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_A2A;

/**
 * Configuration properties for auto-generating AGENT_CARD artifacts from the {@code x-agent-card}
 * OpenAPI vendor extension.
 */
@Singleton
public class OpenApiAgentCardConfig {

    @ConfigProperty(name = "apicurio.a2a.openapi-integration.enabled", defaultValue = "false")
    @Info(category = CATEGORY_A2A, description = "Auto-generate a companion AGENT_CARD artifact from the "
            + "'x-agent-card' OpenAPI vendor extension", availableSince = "3.3.4", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.a2a.openapi-integration.sync-on-update.enabled", defaultValue = "true")
    @Info(category = CATEGORY_A2A, description = "Update the generated Agent Card whenever its source "
            + "OpenAPI artifact is updated", availableSince = "3.3.4")
    boolean syncOnUpdateEnabled;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSyncOnUpdateEnabled() {
        return syncOnUpdateEnabled;
    }
}
