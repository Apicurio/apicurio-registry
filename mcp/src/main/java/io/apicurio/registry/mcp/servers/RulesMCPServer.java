package io.apicurio.registry.mcp.servers;

import io.apicurio.registry.mcp.RegistryService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

import static io.apicurio.registry.mcp.Descriptions.ARTIFACT_ID;
import static io.apicurio.registry.mcp.Descriptions.GROUP_ID;
import static io.apicurio.registry.mcp.Utils.handleError;

public class RulesMCPServer {

    @Inject
    RegistryService service;

    @Tool(description = """
            Test schema compatibility against an existing artifact in the Apicurio Registry. \
            This does not create a new version; it simulates doing so to verify if the provided \
            schema content passes all configured rules (e.g. backward compatibility). \
            If it fails, it returns a detailed explanation of why it broke compatibility.""")
    String test_schema_compatibility(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = "The raw content of the new version to test.") String versionContent,
            @ToolArg(description = "The content type of the new version (e.g. application/json).", required = false) String versionContentType
    ) {
        return handleError(() -> service.testSchemaCompatibility(
                groupId,
                artifactId,
                versionContent,
                versionContentType
        ));
    }
}
