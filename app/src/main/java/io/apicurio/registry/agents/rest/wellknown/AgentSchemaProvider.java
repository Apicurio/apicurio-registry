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

    private static final java.util.Set<String> ALLOWED_SCHEMAS = java.util.Set.of(
            "prompt-template-v1",
            "model-schema-v1",
            "mcp-tool-v1",
            "agent-card-v1"
    );

    private String getSchemaResourcePath(String type, String version) {
        String schemaId = type + "-" + version;
        if (ALLOWED_SCHEMAS.contains(schemaId)) {
            return "schemas/" + schemaId + ".json";
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
