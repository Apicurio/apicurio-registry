package io.apicurio.registry.agents;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_A2A;

/**
 * The one switch for the agent registry's discovery features. A2A, MCP tool discovery, the MCP
 * Registry API, the AI Catalog, ARD and the web console's Agents tab follow it unless their own
 * property is set, which always wins (see {@code application.properties}).
 * <p>
 * Features that make outbound connections, such as importing tools from MCP servers, inventory
 * sync and federation, do not follow it: each needs its own explicit opt-in.
 */
@Singleton
public class AgentsConfig {

    @ConfigProperty(name = "apicurio.agents.enabled", defaultValue = "false")
    @Info(category = CATEGORY_A2A, description = "Enable the agent registry's discovery features: A2A agent discovery, MCP tool discovery, the MCP Registry API, the AI Catalog, ARD, and the web console's Agents tab. Each of them can still be turned on or off with its own property, which takes precedence. Features that connect to other servers (apicurio.mcp-import.enabled, apicurio.agent-inventory.enabled, apicurio.federation.enabled) are not included and must be enabled separately", availableSince = "3.4.0", experimental = true)
    boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }
}
