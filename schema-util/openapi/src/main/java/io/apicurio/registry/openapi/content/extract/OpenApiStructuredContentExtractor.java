package io.apicurio.registry.openapi.content.extract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;

/**
 * Extracts structured elements from OpenAPI content for search indexing. Parses the OpenAPI document and
 * extracts schemas, paths, operationIds, tags, parameters, security schemes, and servers.
 */
public class OpenApiStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(OpenApiStructuredContentExtractor.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            JsonNode root = objectMapper.readTree(content.content());
            List<StructuredElement> elements = new ArrayList<>();

            extractSchemas(root, elements);
            extractPaths(root, elements);
            extractTags(root, elements);
            extractParameters(root, elements);
            extractSecuritySchemes(root, elements);
            extractServers(root, elements);

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from OpenAPI: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts schema names from components/schemas.
     */
    private void extractSchemas(JsonNode root, List<StructuredElement> elements) {
        JsonNode schemas = root.path("components").path("schemas");
        if (!schemas.isMissingNode() && schemas.isObject()) {
            schemas.fieldNames().forEachRemaining(name -> {
                elements.add(new StructuredElement("schema", name));
            });
        }
    }

    /**
     * Extracts paths and operationIds from paths.
     */
    private void extractPaths(JsonNode root, List<StructuredElement> elements) {
        JsonNode paths = root.path("paths");
        if (!paths.isMissingNode() && paths.isObject()) {
            paths.fieldNames().forEachRemaining(path -> {
                elements.add(new StructuredElement("path", path));

                // Extract operationIds from each HTTP method
                JsonNode pathItem = paths.get(path);
                extractOperationIds(pathItem, elements);
            });
        }
    }

    /**
     * Extracts operationIds from a path item's HTTP methods.
     */
    private void extractOperationIds(JsonNode pathItem, List<StructuredElement> elements) {
        String[] httpMethods = { "get", "put", "post", "delete", "options", "head", "patch", "trace" };
        for (String method : httpMethods) {
            JsonNode operation = pathItem.path(method);
            if (!operation.isMissingNode() && operation.has("operationId")) {
                elements.add(new StructuredElement("operation", operation.get("operationId").asText()));
            }
        }
    }

    /**
     * Extracts tag names from the top-level tags array.
     */
    private void extractTags(JsonNode root, List<StructuredElement> elements) {
        JsonNode tags = root.path("tags");
        if (!tags.isMissingNode() && tags.isArray()) {
            for (JsonNode tag : tags) {
                if (tag.has("name")) {
                    elements.add(new StructuredElement("tag", tag.get("name").asText()));
                }
            }
        }
    }

    /**
     * Extracts parameter names from components/parameters.
     */
    private void extractParameters(JsonNode root, List<StructuredElement> elements) {
        JsonNode parameters = root.path("components").path("parameters");
        if (!parameters.isMissingNode() && parameters.isObject()) {
            parameters.fieldNames().forEachRemaining(name -> {
                elements.add(new StructuredElement("parameter", name));
            });
        }
    }

    /**
     * Extracts security scheme names from components/securitySchemes.
     */
    private void extractSecuritySchemes(JsonNode root, List<StructuredElement> elements) {
        JsonNode securitySchemes = root.path("components").path("securitySchemes");
        if (!securitySchemes.isMissingNode() && securitySchemes.isObject()) {
            securitySchemes.fieldNames().forEachRemaining(name -> {
                elements.add(new StructuredElement("security_scheme", name));
            });
        }
    }

    /**
     * Extracts server URLs from the top-level servers array.
     */
    private void extractServers(JsonNode root, List<StructuredElement> elements) {
        JsonNode servers = root.path("servers");
        if (!servers.isMissingNode() && servers.isArray()) {
            for (JsonNode server : servers) {
                if (server.has("url")) {
                    elements.add(new StructuredElement("server", server.get("url").asText()));
                }
            }
        }
    }
}
