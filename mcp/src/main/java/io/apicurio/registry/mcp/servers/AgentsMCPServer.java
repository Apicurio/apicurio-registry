package io.apicurio.registry.mcp.servers;

import io.apicurio.registry.mcp.RegistryService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

import static io.apicurio.registry.mcp.Descriptions.ARTIFACT_ID;
import static io.apicurio.registry.mcp.Descriptions.GROUP_ID;
import static io.apicurio.registry.mcp.Utils.handleError;

public class AgentsMCPServer {

    @Inject
    RegistryService service;

    @Tool(description = """
            Discover A2A agent cards registered in the Apicurio Registry. \
            Returns a list of agents matching the search criteria. \
            Each result includes the agent's name, description, skills, \
            capabilities, and the URL where the agent can be reached.""")
    String discover_agents(
            @ToolArg(description = "Filter agents by name (partial match)", required = false) String name,
            @ToolArg(description = "Filter agents by skill ID", required = false) String skill,
            @ToolArg(description = "Filter agents by capability (e.g. 'streaming:true')", required = false) String capability
    ) {
        return handleError(() -> service.searchAgentCards(name, skill, capability));
    }

    @Tool(description = """
            Get a specific A2A agent card registered in the Apicurio Registry. \
            Returns the full agent card JSON including the agent's URL, skills, \
            capabilities, security schemes, and supported interfaces.""")
    String get_agent_card(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId
    ) {
        return handleError(() -> service.getAgentCard(groupId, artifactId));
    }

    @Tool(description = """
            Search for MCP tool definitions registered in the Apicurio Registry. \
            Returns a list of MCP tools matching the search criteria.""")
    String search_mcp_tools(
            @ToolArg(description = "Filter tools by name (partial match)", required = false) String name,
            @ToolArg(description = "Filter tools by input parameter name", required = false) String parameter
    ) {
        return handleError(() -> service.searchMcpTools(name, parameter));
    }

    @Tool(description = """
            Get a specific MCP tool definition registered in the Apicurio Registry. \
            Returns the full MCP tool JSON including input schema and parameters.""")
    String get_mcp_tool(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId
    ) {
        return handleError(() -> service.getMcpTool(groupId, artifactId));
    }
}
