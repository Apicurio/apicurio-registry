package io.apicurio.registry.agents.mcptools;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_MCP;

/**
 * Configuration properties for MCP tool definition artifact support.
 */
@Singleton
public class McpToolsConfig {

    @ConfigProperty(name = "apicurio.mcp-tools.enabled")
    @Info(category = CATEGORY_MCP, description = "Enable MCP tool definition discovery endpoints. Defaults to apicurio.agents.enabled", dependsOn = "apicurio.agents.enabled", availableSince = "3.0.0", experimental = true)
    boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }
}
