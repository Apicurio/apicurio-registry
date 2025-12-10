package io.apicurio.registry.content;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.util.ContentTypeUtil;

import java.util.Map;

/**
 * Content accepter for A2A Agent Card artifacts.
 *
 * Agent Cards are JSON documents that describe AI agents following the A2A (Agent2Agent) protocol.
 * This accepter validates that the content is valid JSON and contains required Agent Card fields.
 *
 * @see <a href="https://a2a-protocol.org/">A2A Protocol</a>
 */
public class AgentCardContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            // Must be parseable JSON
            if (content.getContentType() != null && content.getContentType().toLowerCase().contains("json")
                    && !ContentTypeUtil.isParsableJson(content.getContent())) {
                return false;
            }

            JsonNode tree = ContentTypeUtil.parseJson(content.getContent());

            // Check for A2A Agent Card structure
            // An Agent Card must have a "name" field at minimum
            // Optional but common fields: "description", "version", "url", "capabilities", "skills"
            if (tree.isObject() && tree.has("name")) {
                // Additional heuristics to identify an Agent Card vs regular JSON
                // Look for A2A-specific fields
                if (tree.has("capabilities") || tree.has("skills") || tree.has("url")
                        || tree.has("authentication") || tree.has("defaultInputModes")
                        || tree.has("defaultOutputModes") || tree.has("provider")) {
                    return true;
                }
                // If it has name and description, and looks like a service descriptor, accept it
                if (tree.has("description") && tree.has("version")) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Error - invalid syntax
        }
        return false;
    }
}
