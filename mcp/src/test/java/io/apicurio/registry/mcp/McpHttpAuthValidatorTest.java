package io.apicurio.registry.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
