package io.apicurio.registry.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link McpHttpAuthValidator} startup configuration checks.
 */
class McpHttpAuthValidatorTest {

    private McpHttpAuthValidator validator;
    private TestHttp http;
    private TestAuth auth;

    @BeforeEach
    void setUp() {
        validator = new McpHttpAuthValidator();
        http = new TestHttp(false, true);
        auth = new TestAuth(false);
        validator.config = new ValidatorMcpConfig(http, auth);
        validator.oidcTenantEnabled = false;
        validator.mcpHttpTransportEnabled = false;
        validator.registryUrl = "localhost:8080";
        validator.connectivityProbe = (host, port) -> {
            // no-op: unit tests do not require a live Registry
        };
    }

    @Test
    void skipsValidationWhenHttpModeDisabled() {
        http.enabled = false;
        validator.mcpHttpTransportEnabled = false;
        validator.oidcTenantEnabled = false;

        assertDoesNotThrow(() -> validator.onStart(null));
    }

    @Test
    void failsWhenHttpModeEnabledButTransportDisabled() {
        http.enabled = true;
        validator.mcpHttpTransportEnabled = false;
        validator.oidcTenantEnabled = true;

        IllegalStateException error = assertThrows(IllegalStateException.class,
                validator::validateHttpAuthConfig);
        assertTrue(error.getMessage().contains("quarkus.mcp.server.http.enabled"));
    }

    @Test
    void failsWhenHttpModeEnabledButOidcDisabled() {
        http.enabled = true;
        validator.mcpHttpTransportEnabled = true;
        validator.oidcTenantEnabled = false;

        IllegalStateException error = assertThrows(IllegalStateException.class,
                validator::validateHttpAuthConfig);
        assertTrue(error.getMessage().contains("quarkus.oidc.tenant-enabled"));
    }

    @Test
    void succeedsWhenHttpTransportAndOidcEnabled() {
        http.enabled = true;
        validator.mcpHttpTransportEnabled = true;
        validator.oidcTenantEnabled = true;

        assertDoesNotThrow(validator::validateHttpAuthConfig);
    }

    @Test
    void succeedsWhenForwardTokenAndClientCredentialsBothConfigured() {
        http.enabled = true;
        http.forwardToken = true;
        auth.enabled = true;
        validator.mcpHttpTransportEnabled = true;
        validator.oidcTenantEnabled = true;

        assertDoesNotThrow(validator::validateHttpAuthConfig);
    }

    @Test
    void succeedsWhenForwardTokenDisabledWithHttpAndOidc() {
        http.enabled = true;
        http.forwardToken = false;
        validator.mcpHttpTransportEnabled = true;
        validator.oidcTenantEnabled = true;

        assertDoesNotThrow(validator::validateHttpAuthConfig);
    }

    @Test
    void parseRegistryHostPortAcceptsHostPortAndFullUrl() {
        assertEquals(new McpHttpAuthValidator.HostPort("localhost", 8080),
                McpHttpAuthValidator.parseRegistryHostPort("localhost:8080"));
        assertEquals(new McpHttpAuthValidator.HostPort("registry.example.com", 443),
                McpHttpAuthValidator.parseRegistryHostPort("https://registry.example.com"));
        assertEquals(new McpHttpAuthValidator.HostPort("localhost", 8080),
                McpHttpAuthValidator.parseRegistryHostPort("http://localhost:8080/apis/registry/v3"));
    }

    @Test
    void parseRegistryHostPortRejectsBlankAndInvalid() {
        IllegalStateException blank = assertThrows(IllegalStateException.class,
                () -> McpHttpAuthValidator.parseRegistryHostPort("  "));
        assertTrue(blank.getMessage().contains("registry.url must be configured"));

        IllegalStateException invalid = assertThrows(IllegalStateException.class,
                () -> McpHttpAuthValidator.parseRegistryHostPort("not a url"));
        assertTrue(invalid.getMessage().contains("registry.url"));
    }

    @Test
    void validateRegistryUrlProbesReachabilityWhenAuthDisabled() {
        http.enabled = true;
        auth.enabled = false;
        validator.registryUrl = "localhost:8080";

        boolean[] probed = {false};
        validator.connectivityProbe = (host, port) -> {
            probed[0] = true;
            assertEquals("localhost", host);
            assertEquals(8080, port);
        };

        assertDoesNotThrow(validator::validateRegistryUrl);
        assertTrue(probed[0]);
    }

    @Test
    void validateRegistryUrlSkipsReachabilityWhenAuthEnabled() {
        http.enabled = true;
        auth.enabled = true;
        validator.registryUrl = "localhost:8080";
        validator.connectivityProbe = (host, port) -> {
            throw new IOException("should not be called when client-credentials probe covers startup");
        };

        assertDoesNotThrow(validator::validateRegistryUrl);
    }

    @Test
    void validateRegistryUrlFailsWhenHostUnreachable() {
        http.enabled = true;
        auth.enabled = false;
        validator.registryUrl = "registry.invalid:9999";
        validator.connectivityProbe = (host, port) -> {
            throw new IOException("Connection refused");
        };

        IllegalStateException error = assertThrows(IllegalStateException.class,
                validator::validateRegistryUrl);
        assertTrue(error.getMessage().contains("Cannot reach Apicurio Registry"));
        assertTrue(error.getMessage().contains("registry.invalid:9999"));
    }

    @Test
    void onStartValidatesRegistryUrlWhenHttpEnabled() {
        http.enabled = true;
        validator.mcpHttpTransportEnabled = true;
        validator.oidcTenantEnabled = true;
        validator.registryUrl = "localhost:8080";
        boolean[] probed = {false};
        validator.connectivityProbe = (host, port) -> probed[0] = true;

        assertDoesNotThrow(() -> validator.onStart(null));
        assertTrue(probed[0]);
    }

    private static final class TestHttp implements McpConfig.Http {
        private boolean enabled;
        private boolean forwardToken;

        private TestHttp(boolean enabled, boolean forwardToken) {
            this.enabled = enabled;
            this.forwardToken = forwardToken;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public boolean forwardToken() {
            return forwardToken;
        }
    }

    private static final class TestAuth implements McpConfig.Auth {
        private boolean enabled;

        private TestAuth(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public Optional<String> tokenEndpoint() {
            return Optional.empty();
        }

        @Override
        public Optional<String> clientId() {
            return Optional.empty();
        }

        @Override
        public Optional<String> clientSecret() {
            return Optional.empty();
        }

        @Override
        public Optional<String> scope() {
            return Optional.empty();
        }
    }

    private static final class ValidatorMcpConfig implements McpConfig {
        private final TestHttp http;
        private final TestAuth auth;

        private ValidatorMcpConfig(TestHttp http, TestAuth auth) {
            this.http = http;
            this.auth = auth;
        }

        @Override
        public boolean safeMode() {
            return true;
        }

        @Override
        public Paging paging() {
            return new Paging() {
                @Override
                public int limit() {
                    return 200;
                }

                @Override
                public boolean limitError() {
                    return true;
                }
            };
        }

        @Override
        public Auth auth() {
            return auth;
        }

        @Override
        public Tls tls() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http http() {
            return http;
        }
    }
}
