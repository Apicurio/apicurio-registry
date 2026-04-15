package io.apicurio.registry.content;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.util.ContentTypeUtil;

import java.util.Map;

/**
 * Content accepter for MCP (Model Context Protocol) tool definition artifacts.
 *
 * MCP tools are JSON documents that describe tools available to AI agents following the MCP specification.
 * This accepter validates that the content is valid JSON and contains required MCP tool fields.
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/tools/">MCP Tools</a>
 */
public class McpToolContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            // Must be parseable JSON
            if (content.getContentType() != null && content.getContentType().toLowerCase().contains("json")
                    && !ContentTypeUtil.isParsableJson(content.getContent())) {
                return false;
            }

            JsonNode tree = ContentTypeUtil.parseJson(content.getContent());

            // An MCP tool definition must have "name" and "inputSchema" fields
            if (tree.isObject() && tree.has("name") && tree.has("inputSchema")) {
                return true;
            }
        } catch (Exception e) {
            // Error - invalid syntax
        }
        return false;
    }
}
