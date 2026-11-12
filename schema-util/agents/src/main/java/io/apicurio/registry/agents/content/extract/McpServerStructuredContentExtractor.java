package io.apicurio.registry.agents.content.extract;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;
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

    private static final String TRANSPORT = "transport";

    private static final Logger log = LoggerFactory.getLogger(McpServerStructuredContentExtractor.class);

    private final ObjectMapper objectMapper = new JsonMapper();

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
            addTextual(pkg, "registryType", "packageRegistry", elements);
            addTextual(pkg, "identifier", "package", elements);
            JsonNode transport = pkg.path(TRANSPORT);
            if (transport.isString()) {
                elements.add(new StructuredElement(TRANSPORT, transport.asString()));
            } else {
                addTextual(transport, "type", TRANSPORT, elements);
            }
        }
    }

    private void extractRemotes(JsonNode root, List<StructuredElement> elements) {
        JsonNode remotes = root.path("remotes");
        if (!remotes.isArray()) {
            return;
        }
        for (JsonNode remote : remotes) {
            addTextual(remote, "type", TRANSPORT, elements);
            addTextual(remote, "url", "remote", elements);
        }
    }

    private void addTextual(JsonNode node, String field, String elementType,
            List<StructuredElement> elements) {
        JsonNode value = node.path(field);
        if (value.isString() && !value.asString().isBlank()) {
            elements.add(new StructuredElement(elementType, value.asString()));
        }
    }
}
