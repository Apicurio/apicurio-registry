package io.apicurio.registry.content.extract;

/**
 * Performs metadata extraction for MCP server definition content.
 *
 * Extracts the server name and description from the <code>server.json</code> structure. Delegates to
 * {@link JsonNameDescriptionContentExtractor} for the actual extraction.
 *
 * @see <a href="https://github.com/modelcontextprotocol/registry">MCP Registry</a>
 */
public class McpServerContentExtractor extends JsonNameDescriptionContentExtractor {
}
