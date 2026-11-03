package io.apicurio.registry.agents.mcpimport;

/**
 * An MCP import refused before connecting, because the URL or its host is not allowed.
 */
public class McpImportDeniedException extends McpImportException {

    private static final long serialVersionUID = 2617409519352268810L;

    public McpImportDeniedException(String message) {
        super(Reason.HOST_DENIED, message);
    }
}
