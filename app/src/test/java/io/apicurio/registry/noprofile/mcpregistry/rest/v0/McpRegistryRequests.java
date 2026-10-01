package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

import java.util.regex.Pattern;
import java.util.ArrayList;

/** Builds the standard encoded serverName URL from readable namespace/server test coordinates. */
public final class McpRegistryRequests {
    private static final Pattern COORDINATES = Pattern.compile(
            "(/mcp-registry/v0\\.1/servers/)([a-zA-Z0-9.-]+)/([a-zA-Z0-9._-]+)(?=/|\\?|$)");

    private McpRegistryRequests() {
    }

    public static RequestSpecification given() {
        return RestAssured.given().filter((request, response, context) -> {
            // Resolve path parameters and encode query values first. Only then replace the name
            // separator, retaining the complete URI (including inline query parameters).
            String uri = request.getURI();
            String encoded = COORDINATES.matcher(uri).replaceFirst("$1$2%2F$3");
            if (!uri.equals(encoded)) {
                new ArrayList<>(request.getQueryParams().keySet()).forEach(request::removeQueryParam);
                request.urlEncodingEnabled(false);
                request.path(encoded);
            }
            return context.next(request, response);
        });
    }
}
