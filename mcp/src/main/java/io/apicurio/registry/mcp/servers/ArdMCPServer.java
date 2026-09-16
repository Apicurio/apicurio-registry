package io.apicurio.registry.mcp.servers;

import io.apicurio.registry.mcp.RegistryService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

import static io.apicurio.registry.mcp.Descriptions.ARD_SEARCH_CAPABILITIES;
import static io.apicurio.registry.mcp.Descriptions.ARD_SEARCH_PAGE_SIZE;
import static io.apicurio.registry.mcp.Descriptions.ARD_SEARCH_PAGE_TOKEN;
import static io.apicurio.registry.mcp.Descriptions.ARD_SEARCH_PUBLISHER;
import static io.apicurio.registry.mcp.Descriptions.ARD_SEARCH_TAGS;
import static io.apicurio.registry.mcp.Descriptions.ARD_SEARCH_TEXT;
import static io.apicurio.registry.mcp.Descriptions.ARD_SEARCH_TYPE;
import static io.apicurio.registry.mcp.Utils.handleError;

/**
 * Exposes the registry's ARD (Agentic Resource Discovery) search API,
 * {@code POST /.well-known/ard/search}, as an MCP tool, per ARD spec &sect;5.3.5 (Protocol
 * Wrappers).
 *
 * <p>If ARD support is disabled on the target registry ({@code apicurio.ard.enabled=false}),
 * the underlying REST call returns a 404, which is translated into a
 * {@link io.quarkiverse.mcp.server.ToolCallException} by {@link RegistryService#ardSearch}
 * / {@link io.apicurio.registry.mcp.Utils#handleError}, the same pattern already used by
 * {@link AgentsMCPServer} for the {@code apicurio.a2a.enabled} / {@code apicurio.mcp-tools.enabled}
 * gates. The MCP server process does not have direct access to the target registry's
 * configuration (it is a separate client process, potentially pointed at a remote registry),
 * so gating happens at call time rather than at tool-registration time.
 */
public class ArdMCPServer {

    @Inject
    RegistryService service;

    @Tool(description = """
            Search Apicurio Registry's Agentic Resource Discovery (ARD) index, \
            https://agenticresourcediscovery.org/spec/. Given a natural-language query and \
            optional structured filters, returns matching A2A agents and MCP tools \
            registered in Apicurio Registry, in the ARD entry format (identifier, \
            displayName, type, url, description, capabilities, tags, publisher, etc).""")
    String ard_search(
            @ToolArg(description = ARD_SEARCH_TEXT) String text,
            @ToolArg(description = ARD_SEARCH_TYPE, required = false) String type,
            @ToolArg(description = ARD_SEARCH_TAGS, required = false) String tags,
            @ToolArg(description = ARD_SEARCH_CAPABILITIES, required = false) String capabilities,
            @ToolArg(description = ARD_SEARCH_PUBLISHER, required = false) String publisher,
            @ToolArg(description = ARD_SEARCH_PAGE_TOKEN, required = false) String pageToken,
            @ToolArg(description = ARD_SEARCH_PAGE_SIZE, required = false) Integer pageSize
    ) {
        return handleError(() -> service.ardSearch(text, type, tags, capabilities, publisher, pageToken, pageSize));
    }
}
