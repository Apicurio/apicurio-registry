package io.apicurio.registry.content;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.util.ContentTypeUtil;

import java.util.Locale;
import java.util.Map;

/**
 * Content accepter for MCP (Model Context Protocol) server definition artifacts, also known as
 * <code>server.json</code> documents.
 *
 * A server definition is distinguished from an MCP tool definition by the shape of its identity fields: it
 * carries a reverse-DNS <code>name</code> and a <code>version</code>, and it does not carry the
 * <code>inputSchema</code> that every tool definition has.
 *
 * @see <a href="https://github.com/modelcontextprotocol/registry">MCP Registry</a>
 */
public class McpServerContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            if (content.getContentType() != null
                    && content.getContentType().toLowerCase(Locale.ROOT).contains("json")
                    && !ContentTypeUtil.isParsableJson(content.getContent())) {
                return false;
            }

            JsonNode tree = ContentTypeUtil.parseJson(content.getContent());
            if (!tree.isObject()) {
                return false;
            }

            // An MCP tool definition also has a "name", so require the fields that only a server
            // definition has, and rule out the tool shape explicitly.
            if (tree.has("inputSchema")) {
                return false;
            }
            return tree.has("name") && tree.get("name").isTextual() && tree.has("version");
        } catch (Exception e) {
            // Error - invalid syntax
        }
        return false;
    }
}
