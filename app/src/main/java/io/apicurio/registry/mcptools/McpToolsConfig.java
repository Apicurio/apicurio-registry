package io.apicurio.registry.mcptools;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_MCP;

/**
 * Configuration properties for MCP tool definition artifact support.
 */
@Singleton
public class McpToolsConfig {

    @ConfigProperty(name = "apicurio.mcp-tools.enabled", defaultValue = "false")
    @Info(category = CATEGORY_MCP, description = "Enable MCP tool definition discovery endpoints", availableSince = "3.0.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.mcp-tools.entitlements.enabled", defaultValue = "true")
    @Info(category = CATEGORY_MCP, description = "Enable entitlement-based MCP tool filtering", availableSince = "3.0.0")
    boolean entitlementsEnabled;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEntitlementsEnabled() {
        return entitlementsEnabled;
    }
}
