package io.apicurio.registry.agents.rest.wellknown;

import io.apicurio.registry.agents.a2a.A2AConfig;
import io.apicurio.registry.agents.mcptools.McpToolsConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serves the JSON Schemas of the AI artifact types under {@code /.well-known/schemas}.
 */
@ApplicationScoped
public class AgentSchemaProvider {

    @Inject
    A2AConfig a2aConfig;

    @Inject
    McpToolsConfig mcpToolsConfig;

    public Response getSchema(String schemaType, String version) {
        if (!a2aConfig.isEnabled() && !mcpToolsConfig.isEnabled()) {
            throw new NotFoundException("Schema not found: " + schemaType + "/" + version);
        }

        // Validate and normalize the type
        String schemaResourcePath = getSchemaResourcePath(schemaType, version);
        if (schemaResourcePath == null) {
            throw new NotFoundException("Schema not found: " + schemaType + "/" + version);
        }

        try {
            String schemaContent = loadSchemaFromClasspath(schemaResourcePath);
            return Response.ok(schemaContent, "application/schema+json")
                    .header("Content-Disposition", "inline; filename=\"" + schemaType + "-" + version + ".json\"")
                    .header("Cache-Control", "public, max-age=86400")
                    .build();
        } catch (IOException e) {
            throw new NotFoundException("Schema not found: " + schemaType + "/" + version);
        }
    }

    private String getSchemaResourcePath(String type, String version) {
        // Only allow known schema types and versions
        if ("prompt-template".equals(type) && "v1".equals(version)) {
            return "schemas/prompt-template-v1.json";
        } else if ("model-schema".equals(type) && "v1".equals(version)) {
            return "schemas/model-schema-v1.json";
        } else if ("mcp-tool".equals(type) && "v1".equals(version)) {
            return "schemas/mcp-tool-v1.json";
        }
        return null;
    }

    private String loadSchemaFromClasspath(String resourcePath) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
