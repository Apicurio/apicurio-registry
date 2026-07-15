package io.apicurio.registry.mcp;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates HTTP transport and OIDC configuration at startup.
 */
@ApplicationScoped
public class McpHttpAuthValidator {

    private static final Logger log = LoggerFactory.getLogger(McpHttpAuthValidator.class);

    @Inject
    McpConfig config;

    @ConfigProperty(name = "quarkus.oidc.tenant-enabled", defaultValue = "false")
    boolean oidcTenantEnabled;

    @ConfigProperty(name = "quarkus.mcp.server.http.enabled", defaultValue = "false")
    boolean mcpHttpTransportEnabled;

    void onStart(@Observes StartupEvent event) {
        if (!config.http().enabled()) {
            return;
        }

        validateHttpAuthConfig();

        if (config.http().forwardToken() && config.auth().enabled()) {
            log.warn("Both apicurio.mcp.http.forward-token and apicurio.mcp.auth.enabled are set. "
                    + "HTTP requests will forward the caller's bearer token; client credentials are used "
                    + "only for stdio transport.");
        }

        log.info("MCP HTTP transport enabled with inbound OIDC authentication"
                + (config.http().forwardToken() ? " and bearer token forwarding to Registry" : ""));
    }

    /**
     * Validates that HTTP mode has the required transport and OIDC settings.
     *
     * @throws IllegalStateException if required configuration is missing
     */
    void validateHttpAuthConfig() {
        if (!mcpHttpTransportEnabled) {
            throw new IllegalStateException(
                    "apicurio.mcp.http.enabled is true but quarkus.mcp.server.http.enabled is false. "
                            + "HTTP transport must be enabled at build time (quarkus.mcp.server.http.enabled=true "
                            + "in application.properties) and the MCP server must be rebuilt.");
        }

        if (!oidcTenantEnabled) {
            throw new IllegalStateException(
                    "apicurio.mcp.http.enabled is true but quarkus.oidc.tenant-enabled is false. "
                            + "Configure Quarkus OIDC (QUARKUS_OIDC_TENANT_ENABLED, QUARKUS_OIDC_AUTH_SERVER_URL) "
                            + "to secure the MCP HTTP endpoint.");
        }
    }
}
