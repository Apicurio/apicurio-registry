package io.apicurio.registry.agents.mcpimport;

import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;

import java.io.IOException;

/**
 * The MCP SDK's JSON mapping, backed by Jackson.
 * <p>
 * The SDK ships one in mcp-json-jackson2, which is not used because it needs a version of the
 * networknt JSON Schema validator that conflicts with the one the registry uses.
 * <p>
 * Numbers are read exactly, so a tool's schema is stored with the values the server sent rather
 * than values rounded through a double, which would change {@code enum}, {@code const} and bound
 * values that a double cannot hold.
 */
final class JacksonMcpJsonMapper implements McpJsonMapper {

    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    @Override
    public <T> T readValue(String content, Class<T> type) throws IOException {
        return mapper.readValue(content, type);
    }

    @Override
    public <T> T readValue(byte[] content, Class<T> type) throws IOException {
        return mapper.readValue(content, type);
    }

    @Override
    public <T> T readValue(String content, TypeRef<T> type) throws IOException {
        return mapper.readValue(content, javaType(type));
    }

    @Override
    public <T> T readValue(byte[] content, TypeRef<T> type) throws IOException {
        return mapper.readValue(content, javaType(type));
    }

    @Override
    public <T> T convertValue(Object fromValue, Class<T> type) {
        return mapper.convertValue(fromValue, type);
    }

    @Override
    public <T> T convertValue(Object fromValue, TypeRef<T> type) {
        return mapper.convertValue(fromValue, javaType(type));
    }

    @Override
    public String writeValueAsString(Object value) throws IOException {
        return mapper.writeValueAsString(value);
    }

    @Override
    public byte[] writeValueAsBytes(Object value) throws IOException {
        return mapper.writeValueAsBytes(value);
    }

    private JavaType javaType(TypeRef<?> type) {
        return mapper.getTypeFactory().constructType(type.getType());
    }
}
