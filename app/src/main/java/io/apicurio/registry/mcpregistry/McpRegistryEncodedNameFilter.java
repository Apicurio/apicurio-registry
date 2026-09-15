package io.apicurio.registry.mcpregistry;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Accepts the standard URL-encoded server name without enabling encoded slashes globally. */
@Provider
@PreMatching
public class McpRegistryEncodedNameFilter implements ContainerRequestFilter {

    private static final Pattern NAME = Pattern.compile(
            "^(/apis/mcp-registry/v0\\.1/servers/)([a-zA-Z0-9.-]+)%2[fF]([a-zA-Z0-9._-]+)(/.*)?$");

    @Override
    public void filter(ContainerRequestContext context) {
        URI uri = context.getUriInfo().getRequestUri();
        Matcher matcher = NAME.matcher(uri.getRawPath());
        if (matcher.matches()) {
            // Reuse the same identity validation as the resource. Only the single separator between
            // namespace and server id is rewritten; encoded version/path segments remain untouched.
            McpServerName.of(matcher.group(2), matcher.group(3));
            String path = matcher.group(1) + matcher.group(2) + "/" + matcher.group(3)
                    + (matcher.group(4) == null ? "" : matcher.group(4));
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            context.setRequestUri(URI.create(uri.getScheme() + "://" + uri.getRawAuthority() + path + query));
        }
    }
}
