package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.restassured.builder.ResponseBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRegistryRequestsTest {

    @Test
    void retainsInlineQueriesAndEncodesParameterValuesExactlyOnce() {
        AtomicReference<String> captured = new AtomicReference<>();
        McpRegistryRequests.given().queryParam("search", "a b&c+d")
                .filter((request, response, context) -> {
                    captured.set(request.getURI());
                    return new ResponseBuilder().setStatusCode(200).build();
                })
                .get("http://localhost/apis/mcp-registry/v0.1/servers/io.github.example/weather"
                        + "?include_deleted=true");
        URI uri = URI.create(captured.get());
        assertEquals("/apis/mcp-registry/v0.1/servers/io.github.example%2Fweather", uri.getRawPath());
        String[] parameters = uri.getRawQuery().split("&");
        assertEquals(2, parameters.length);
        boolean foundSearch = false;
        boolean foundDeleted = false;
        for (String parameter : parameters) {
            if (parameter.startsWith("search=")) {
                assertEquals("a b&c+d", URLDecoder.decode(parameter.substring(7), StandardCharsets.UTF_8));
                foundSearch = true;
            } else if (parameter.equals("include_deleted=true")) {
                foundDeleted = true;
            }
        }
        assertTrue(foundSearch);
        assertTrue(foundDeleted);
    }
}
