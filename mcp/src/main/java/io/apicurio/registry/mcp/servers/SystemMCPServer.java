package io.apicurio.registry.mcp.servers;

import io.apicurio.registry.mcp.RegistryService;
import io.apicurio.registry.rest.client.models.SystemInfo;
import io.quarkiverse.mcp.server.Tool;
import jakarta.inject.Inject;

import static io.apicurio.registry.mcp.Utils.handleError;

public class SystemMCPServer {

    @Inject
    RegistryService service;

    @Tool(description = """
            Get information about the Apicurio Registry server.""")
    SystemInfo get_server_info() {
        return handleError(() -> service.getServerInfo());
    }
}
