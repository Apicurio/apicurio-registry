package io.apicurio.registry.extensions;

import io.apicurio.registry.rest.v3.beans.McpToolImportRequest;
import io.apicurio.registry.rest.v3.beans.McpToolImportResult;

/**
 * Extension point implementing the v3 {@code /groups/{groupId}/artifacts/{artifactId}/mcp-import}
 * endpoint.
 * <p>
 * The endpoint is part of the core REST API, but the behaviour belongs to the optional agents
 * module. When no bean implements this interface the endpoint responds with 409, as when the
 * feature is disabled.
 * </p>
 */
public interface McpToolImportHandler {

    /**
     * Imports the tools of the MCP server in the request into the given {@code MCP_SERVER}
     * artifact. Write access to that artifact has already been checked by the caller; required
     * parameters have been validated.
     *
     * @param groupId    the group id as given in the request
     * @param artifactId the MCP_SERVER artifact id
     * @param request    the import request
     * @return the result for the server and for each tool
     */
    McpToolImportResult importTools(String groupId, String artifactId, McpToolImportRequest request);

    /**
     * @return whether importing is enabled on this server, so the UI can offer it
     */
    boolean isEnabled();
}
