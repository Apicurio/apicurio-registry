package io.apicurio.registry.content.extract;

/**
 * Performs metadata extraction for MCP tool definition content.
 *
 * Extracts the tool name and description from the MCP tool JSON structure.
 * Delegates to {@link JsonNameDescriptionContentExtractor} for the actual extraction.
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/tools/">MCP Tools</a>
 */
public class McpToolContentExtractor extends JsonNameDescriptionContentExtractor {
}
