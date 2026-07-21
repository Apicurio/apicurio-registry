package io.apicurio.registry.mcp;

import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.regex.Pattern;

/**
 * Blocks MCP HTTP endpoints unless {@code apicurio.mcp.http.enabled} is true.
 * <p>
 * The Quarkus MCP HTTP transport is compiled in at build time, but HTTP mode is only
 * intended to be reachable when explicitly enabled at runtime with OIDC configured.
 */
@ApplicationScoped
public class McpHttpGateFilter {

    @Inject
    McpConfig config;

    @Inject
    Router router;

    @ConfigProperty(name = "quarkus.mcp.server.http.root-path", defaultValue = "/mcp")
    String mcpRootPath;

    void register(@Observes StartupEvent event) {
        String escapedRootPath = Pattern.quote(mcpRootPath);
        router.routeWithRegex(escapedRootPath + "/?.*")
                .order(Integer.MIN_VALUE)
                .handler(ctx -> blockUnlessEnabled(ctx));
        router.route("/.well-known/oauth-protected-resource")
                .order(Integer.MIN_VALUE)
                .handler(ctx -> blockUnlessEnabled(ctx));
    }

    void blockUnlessEnabled(io.vertx.ext.web.RoutingContext ctx) {
        if (!config.http().enabled()) {
            ctx.response()
                    .setStatusCode(503)
                    .putHeader("Content-Type", "text/plain; charset=UTF-8")
                    .end("MCP HTTP transport is disabled. Set apicurio.mcp.http.enabled=true "
                            + "with OIDC configured to enable it.");
        } else {
            ctx.next();
        }
    }
}
