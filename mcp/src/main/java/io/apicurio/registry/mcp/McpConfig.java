package io.apicurio.registry.mcp;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * Configuration properties for the MCP server.
 */
@ConfigMapping(prefix = "apicurio.mcp")
public interface McpConfig {

    /**
     * Enable safe mode which restricts certain operations.
     */
    @WithName("safe-mode")
    @WithDefault("true")
    boolean safeMode();

    /**
     * Paging configuration.
     */
    Paging paging();

    /**
     * Authentication configuration.
     */
    Auth auth();

    /**
     * TLS configuration.
     */
    Tls tls();

    interface Paging {
        /**
         * Maximum number of items to return in a single page.
         */
        @WithDefault("200")
        int limit();

        /**
         * Whether to throw an error when the paging limit is exceeded.
         */
        @WithName("limit-error")
        @WithDefault("true")
        boolean limitError();
    }

    interface Auth {
        /**
         * Enable OAuth2 authentication.
         */
        @WithDefault("false")
        boolean enabled();

        /**
         * OAuth2 token endpoint URL.
         */
        @WithName("token-endpoint")
        Optional<String> tokenEndpoint();

        /**
         * OAuth2 client ID.
         */
        @WithName("client-id")
        Optional<String> clientId();

        /**
         * OAuth2 client secret.
         */
        @WithName("client-secret")
        Optional<String> clientSecret();

        /**
         * OAuth2 scope (optional).
         */
        Optional<String> scope();
    }

    interface Tls {
        /**
         * Trust all certificates. Only use in development environments.
         */
        @WithName("trust-all")
        @WithDefault("false")
        boolean trustAll();

        /**
         * Verify the hostname in the server certificate.
         */
        @WithName("verify-host")
        @WithDefault("true")
        boolean verifyHost();

        /**
         * Trust store configuration.
         */
        Truststore truststore();

        /**
         * Key store configuration for mTLS (client certificate).
         */
        Keystore keystore();
    }

    interface Truststore {
        /**
         * Trust store type: JKS, PKCS12, or PEM.
         */
        Optional<String> type();

        /**
         * Path to the trust store file.
         */
        Optional<String> path();

        /**
         * Trust store password.
         */
        Optional<String> password();
    }

    interface Keystore {
        /**
         * Key store type: JKS or PKCS12.
         */
        Optional<String> type();

        /**
         * Path to the key store file.
         */
        Optional<String> path();

        /**
         * Key store password.
         */
        Optional<String> password();
    }
}
