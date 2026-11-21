package io.apicurio.registry.http;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_HTTP;

/**
 * Server-wide defaults for the features that connect to other servers on a caller's behalf, such
 * as importing tools from MCP servers and federation with peer registries. Each feature's own
 * property takes precedence (see {@code application.properties}). Both default to off: they relax
 * the outbound address checks, for development and tests.
 */
@Singleton
public class OutboundConfig {

    @ConfigProperty(name = "apicurio.outbound.insecure-http.enabled", defaultValue = "false")
    @Info(category = CATEGORY_HTTP, description = "Default for the features that connect to other servers: allow URLs that use plain HTTP instead of HTTPS. Used by apicurio.mcp-import.insecure-http.enabled and apicurio.federation.peer.insecure-http.enabled when they are not set", availableSince = "3.4.0")
    boolean insecureHttpEnabled;

    @ConfigProperty(name = "apicurio.outbound.loopback.enabled", defaultValue = "false")
    @Info(category = CATEGORY_HTTP, description = "Default for the features that connect to other servers: allow URLs that point at a loopback address or a localhost name, for development and tests. Used by apicurio.mcp-import.loopback.enabled and apicurio.federation.peer.loopback.enabled when they are not set", availableSince = "3.4.0")
    boolean loopbackEnabled;

    public boolean isInsecureHttpEnabled() {
        return insecureHttpEnabled;
    }

    public boolean isLoopbackEnabled() {
        return loopbackEnabled;
    }
}
