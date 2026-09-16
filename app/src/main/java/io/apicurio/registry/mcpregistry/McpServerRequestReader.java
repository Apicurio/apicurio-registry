package io.apicurio.registry.mcpregistry;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.mcpregistry.rest.v0.beans.Server;
import io.apicurio.registry.rules.validity.McpServerContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ContentTypes;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;

/** Validates original JSON types before Jackson can coerce numbers/booleans into string fields. */
@Provider
@Consumes(MediaType.APPLICATION_JSON)
public class McpServerRequestReader implements MessageBodyReader<Server> {

    @Inject
    ObjectMapper mapper;

    @Inject
    McpRegistryConfig config;

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return type == Server.class;
    }

    @Override
    public Server readFrom(Class<Server> type, Type genericType, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, String> headers, InputStream stream) throws IOException {
        // Manifest metadata is bounded independently of transport-level body limits.
        byte[] content = stream.readNBytes(1024 * 1024 + 1);
        if (content.length > 1024 * 1024) {
            throw new ClientErrorException("Server definition exceeds 1 MiB", 413);
        }
        if (!config.isEnabled()) {
            return new Server();
        }
        try {
            new McpServerContentValidator().validate(ValidityLevel.FULL,
                    TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_JSON), Map.of());
            return mapper.readValue(content, Server.class);
        } catch (RuleViolationException e) {
            throw new BadRequestException("Invalid MCP server definition");
        } catch (IOException e) {
            throw new BadRequestException("Invalid MCP server definition JSON");
        }
    }
}
