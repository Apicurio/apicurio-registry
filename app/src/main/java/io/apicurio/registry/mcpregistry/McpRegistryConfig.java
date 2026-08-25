package io.apicurio.registry.mcpregistry;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_MCP;

/**
 * Configuration properties for the official MCP Registry API.
 */
@Singleton
public class McpRegistryConfig {

    @ConfigProperty(name = "apicurio.mcp-registry.enabled", defaultValue = "false")
    @Info(category = CATEGORY_MCP, description = "Enable the MCP Registry API", availableSince = "3.3.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.mcp-registry.max-page-size", defaultValue = "100")
    @Info(category = CATEGORY_MCP, description = "Maximum number of MCP servers returned by a single list request", availableSince = "3.3.0")
    int maxPageSize;

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }
}
