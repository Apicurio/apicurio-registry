package io.apicurio.registry.content.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Extracts structured elements from MCP tool definition content for search indexing. Parses the MCP tool JSON
 * and extracts input parameter names as structured elements.
 */
public class McpToolStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(McpToolStructuredContentExtractor.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            JsonNode root = objectMapper.readTree(content.content());
            List<StructuredElement> elements = new ArrayList<>();

            extractParameters(root, elements);

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from MCP tool: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts parameter names from the inputSchema.properties object.
     */
    private void extractParameters(JsonNode root, List<StructuredElement> elements) {
        JsonNode inputSchema = root.path("inputSchema");
        if (!inputSchema.isMissingNode() && inputSchema.isObject()) {
            JsonNode properties = inputSchema.path("properties");
            if (!properties.isMissingNode() && properties.isObject()) {
                Iterator<String> fieldNames = properties.fieldNames();
                while (fieldNames.hasNext()) {
                    elements.add(new StructuredElement("parameter", fieldNames.next()));
                }
            }
        }
    }
}
