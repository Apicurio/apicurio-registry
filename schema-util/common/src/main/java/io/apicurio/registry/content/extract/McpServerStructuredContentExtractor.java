package io.apicurio.registry.content.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extracts structured elements from MCP server definition content for search indexing. Indexes the package
 * registries and identifiers a server can be installed from, and the transports it speaks, so that a search
 * for e.g. "npm" or "streamable-http" finds the servers that offer it.
 */
public class McpServerStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(McpServerStructuredContentExtractor.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            JsonNode root = objectMapper.readTree(content.content());
            List<StructuredElement> elements = new ArrayList<>();

            extractPackages(root, elements);
            extractRemotes(root, elements);

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from MCP server definition: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void extractPackages(JsonNode root, List<StructuredElement> elements) {
        JsonNode packages = root.path("packages");
        if (!packages.isArray()) {
            return;
        }
        for (JsonNode pkg : packages) {
            addTextual(pkg, "registry_type", "packageRegistry", elements);
            addTextual(pkg, "identifier", "package", elements);
            JsonNode transport = pkg.path("transport");
            if (transport.isTextual()) {
                elements.add(new StructuredElement("transport", transport.asText()));
            } else {
                addTextual(transport, "type", "transport", elements);
            }
        }
    }

    private void extractRemotes(JsonNode root, List<StructuredElement> elements) {
        JsonNode remotes = root.path("remotes");
        if (!remotes.isArray()) {
            return;
        }
        for (JsonNode remote : remotes) {
            addTextual(remote, "type", "transport", elements);
            addTextual(remote, "url", "remote", elements);
        }
    }

    private void addTextual(JsonNode node, String field, String elementType,
            List<StructuredElement> elements) {
        JsonNode value = node.path(field);
        if (value.isTextual() && !value.asText().isBlank()) {
            elements.add(new StructuredElement(elementType, value.asText()));
        }
    }
}
