package io.apicurio.registry.agents.mcpimport;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_MCP;

/**
 * Configuration of importing tool definitions from live MCP servers.
 * <p>
 * {@code @Singleton}, like the other configuration classes such as {@code FederationConfig}: it
 * holds injected configuration values only, so it needs no client proxy.
 */
@Singleton
public class McpImportConfig {

    @ConfigProperty(name = "apicurio.mcp-import.enabled", defaultValue = "false")
    @Info(category = CATEGORY_MCP, description = "Enable importing tool definitions from live MCP servers. The registry connects to the MCP server URL given in the request", availableSince = "3.4.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.mcp-import.allowed-hosts")
    @Info(category = CATEGORY_MCP, description = "Hosts the registry may connect to when importing from an MCP server, as a comma-separated list. Each entry is a hostname (mcp.example.com), a domain wildcard (*.example.com, which does not match example.com itself), an IP address, or a CIDR range (10.0.0.0/8) that every address the host resolves to must be in. When empty, every host is refused. Loopback, link-local, and cloud metadata addresses are refused even when listed", availableSince = "3.4.0")
    Optional<List<String>> allowedHosts;

    @ConfigProperty(name = "apicurio.mcp-import.insecure-http.enabled", defaultValue = "false")
    @Info(category = CATEGORY_MCP, description = "Allow MCP server URLs that use plain HTTP instead of HTTPS when importing", availableSince = "3.4.0")
    boolean insecureHttpEnabled;

    @ConfigProperty(name = "apicurio.mcp-import.loopback.enabled", defaultValue = "false")
    @Info(category = CATEGORY_MCP, description = "Allow MCP server URLs that point at a loopback address or a localhost name when importing, for development and tests. The host must still be allowed by apicurio.mcp-import.allowed-hosts", availableSince = "3.4.0")
    boolean loopbackEnabled;

    @ConfigProperty(name = "apicurio.mcp-import.timeout.seconds", defaultValue = "10")
    @Info(category = CATEGORY_MCP, description = "Timeout, in seconds, for connecting to the MCP server and for each request to it during an import", availableSince = "3.4.0")
    int timeoutSeconds;

    @ConfigProperty(name = "apicurio.mcp-import.response.max-size", defaultValue = "1048576")
    @Info(category = CATEGORY_MCP, description = "Maximum size, in bytes, of each message received from an MCP server during an import", availableSince = "3.4.0")
    int responseMaxSize;

    @ConfigProperty(name = "apicurio.mcp-import.tools.max-size", defaultValue = "500")
    @Info(category = CATEGORY_MCP, description = "Maximum number of tools an MCP server may list for an import. An import from a server listing more is refused", availableSince = "3.4.0")
    int toolsMaxSize;

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getAllowedHosts() {
        return allowedHosts.orElse(List.of());
    }

    public boolean isInsecureHttpEnabled() {
        return insecureHttpEnabled;
    }

    public boolean isLoopbackEnabled() {
        return loopbackEnabled;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getResponseMaxSize() {
        return responseMaxSize;
    }

    public int getToolsMaxSize() {
        return toolsMaxSize;
    }
}
