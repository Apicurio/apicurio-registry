package io.apicurio.registry.cli;

import io.apicurio.registry.cli.auth.OidcDiscovery;
import io.apicurio.registry.cli.common.CliException;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;

/**
 * Test OIDC discovery that returns a canned token endpoint derived from the auth server URL.
 * Replaces the real discovery bean so tests do not need a running OIDC provider.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class TestOidcDiscovery extends OidcDiscovery {

    private static final String DISCOVERED_TOKEN_PATH = "/protocol/openid-connect/token";

    private boolean failOnDiscover;
    private String failMessage;

    public void setFailOnDiscover(final boolean fail) {
        this.failOnDiscover = fail;
        this.failMessage = null;
    }

    public void setFailOnDiscover(final boolean fail, final String message) {
        this.failOnDiscover = fail;
        this.failMessage = message;
    }

    @Override
    public String discoverTokenEndpoint(String authServerUrl) {
        final var uri = OidcDiscovery.validateAuthServerUrl(authServerUrl);

        if (failOnDiscover) {
            final var msg = failMessage != null ? failMessage
                    : "OIDC discovery failed for '" + authServerUrl + "': simulated failure";
            throw new CliException(msg, APPLICATION_ERROR_RETURN_CODE);
        }
        var path = uri.getPath() != null ? uri.getPath() : "";
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() != -1 ? ":" + uri.getPort() : "")
                + path + DISCOVERED_TOKEN_PATH;
    }
}
