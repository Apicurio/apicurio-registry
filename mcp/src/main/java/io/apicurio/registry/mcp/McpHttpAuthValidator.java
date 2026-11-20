package io.apicurio.registry.mcp;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URL;

/**
 * Validates HTTP transport and OIDC configuration at startup.
 */
@ApplicationScoped
public class McpHttpAuthValidator {

    private static final Logger log = LoggerFactory.getLogger(McpHttpAuthValidator.class);

    private static final int REGISTRY_CONNECT_TIMEOUT_MS = 3_000;

    @Inject
    McpConfig config;

    @ConfigProperty(name = "quarkus.oidc.tenant-enabled", defaultValue = "false")
    boolean oidcTenantEnabled;

    @ConfigProperty(name = "quarkus.mcp.server.http.enabled", defaultValue = "false")
    boolean mcpHttpTransportEnabled;

    @ConfigProperty(name = "registry.url", defaultValue = "localhost:8080")
    String registryUrl;

    /**
     * Pluggable TCP probe so unit tests can avoid real network I/O.
     */
    ConnectivityProbe connectivityProbe = McpHttpAuthValidator::tcpConnect;

    void onStart(@Observes StartupEvent event) {
        if (!config.http().enabled()) {
            return;
        }

        validateHttpAuthConfig();
        validateRegistryUrl();

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

    /**
     * Validates {@code registry.url} format and, when the resolver skips its authenticated
     * {@code system.info()} probe (HTTP mode without client-credentials auth), checks that the
     * host/port is reachable so misconfiguration fails at startup instead of the first tool call.
     */
    void validateRegistryUrl() {
        HostPort hostPort = parseRegistryHostPort(registryUrl);
        if (!config.auth().enabled()) {
            try {
                connectivityProbe.connect(hostPort.host(), hostPort.port());
                log.info("Verified registry.url is reachable at {}:{}", hostPort.host(), hostPort.port());
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Cannot reach Apicurio Registry at registry.url=" + registryUrl
                                + " (" + hostPort.host() + ":" + hostPort.port() + "). "
                                + "Check REGISTRY_URL / registry.url and that Registry is running. "
                                + "Details: " + e.getMessage(),
                        e);
            }
        }
    }

    /**
     * Parses {@code registry.url} values such as {@code localhost:8080} or
     * {@code http://localhost:8080/apis/registry/v3} into a host/port pair using
     * {@link URI} / {@link URL} (including {@link URL#getDefaultPort()}).
     */
    static HostPort parseRegistryHostPort(String rawRegistryUrl) {
        if (rawRegistryUrl == null || rawRegistryUrl.isBlank()) {
            throw new IllegalStateException(
                    "registry.url must be configured when apicurio.mcp.http.enabled=true");
        }

        // registry.url often omits the scheme (e.g. "localhost:8080"); URI/URL require one.
        String value = rawRegistryUrl.trim();
        if (!value.contains("://")) {
            log.info("registry.url has no scheme; assuming http:// for host/port parsing: {}", rawRegistryUrl);
            value = "http://" + value;
        }

        try {
            URL url = URI.create(value).toURL();
            String host = url.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalStateException(
                        "registry.url is invalid (could not determine host): " + rawRegistryUrl);
            }
            int port = url.getPort() >= 0 ? url.getPort() : url.getDefaultPort();
            if (port < 0) {
                throw new IllegalStateException(
                        "registry.url is invalid (could not determine port): " + rawRegistryUrl);
            }
            log.debug("Parsed registry.url {} as {}:{}", rawRegistryUrl, host, port);
            return new HostPort(host, port);
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw new IllegalStateException(
                    "registry.url is not a valid URL: " + rawRegistryUrl + ". Details: " + e.getMessage(),
                    e);
        }
    }

    private static void tcpConnect(String host, int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), REGISTRY_CONNECT_TIMEOUT_MS);
        }
    }

    @FunctionalInterface
    interface ConnectivityProbe {
        void connect(String host, int port) throws IOException;
    }

    record HostPort(String host, int port) {
    }
}
