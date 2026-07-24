package io.apicurio.registry.cli;

import io.apicurio.registry.cli.auth.CredentialProvider;
import io.apicurio.registry.cli.auth.OidcDiscovery;
import io.apicurio.registry.cli.config.ConfigModel;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class AuthCommandTest extends AbstractCLITest {

    @Inject
    CredentialProvider credentialProvider;

    @Inject
    OidcDiscovery oidcDiscovery;

    private static final String TOKEN_PATH = "/token";
    private static final String DISCOVERED_TOKEN_PATH = "/protocol/openid-connect/token";
    private static final String TEST_CONTEXT = "test";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "testpass";
    private static final String TEST_CLIENT_ID = "test-client";
    private static final String TEST_CLIENT_SECRET = "test-secret";
    private static final String TEST_SCOPE = "openid profile";
    private static final String TEST_AUTH_SERVER_URL = "http://keycloak.example.com/realms/test";

    // -- Help --

    @Test
    public void testLoginHelp() {
        testHelpCommand("login");
    }

    @Test
    public void testLogoutHelp() {
        testHelpCommand("logout");
    }

    // -- Validation / error cases --

    @Test
    public void testLoginNoArgs() {
        executeAndAssertFailure("login");
    }

    @Test
    public void testLoginPasswordWithoutUsername() {
        executeAndAssertFailure("login", "--password", TEST_PASSWORD);
    }

    @Test
    public void testLoginOAuth2MissingClientId() {
        out.getBuffer().setLength(0);
        executeAndAssertFailure("login", "--token-endpoint", tokenEndpoint());
        assertThat(err.toString())
                .as(withCliOutput("Should require --client-id"))
                .contains("--client-id");
    }

    @Test
    public void testLoginOAuth2ClientIdWithoutEndpoint() {
        out.getBuffer().setLength(0);
        executeAndAssertFailure("login", "--client-id", TEST_CLIENT_ID,
                "--client-secret", TEST_CLIENT_SECRET);
        assertThat(err.toString())
                .as(withCliOutput("Should require --token-endpoint or --auth-server-url"))
                .contains("--token-endpoint")
                .contains("--auth-server-url");
    }

    @Test
    public void testLoginNoContext() {
        executeAndAssertSuccess("context", "delete", "--all");
        executeAndAssertFailure("login", "--username", TEST_USERNAME, "--password", TEST_PASSWORD);
    }

    @Test
    public void testLogoutNoContext() {
        executeAndAssertSuccess("context", "delete", "--all");
        executeAndAssertFailure("logout");
    }

    @Test
    public void testLogoutWhenNotLoggedIn() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("logout");
        assertThat(out.toString())
                .as(withCliOutput("Should indicate not logged in"))
                .contains("is not logged in");
    }

    // -- Basic auth --

    @Test
    public void testLoginBasicAuth() {
        out.getBuffer().setLength(0);
        loginBasic();
        assertThat(out.toString())
                .as(withCliOutput("Should confirm basic auth login"))
                .contains("Logged in to context")
                .contains(TEST_USERNAME);

        var context = getTestContext();
        assertThat(context.getAuthType()).isEqualTo(ConfigModel.AUTH_TYPE_BASIC);
        assertThat(context.getUsername()).isEqualTo(TEST_USERNAME);
        assertThat(context.getTokenEndpoint()).isNull();
        assertThat(context.getClientId()).isNull();
    }

    @Test
    public void testLogoutAfterLogin() {
        loginBasic();

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("logout");
        assertThat(out.toString())
                .as(withCliOutput("Should confirm logout"))
                .contains("Logged out of context");

        var context = getTestContext();
        assertThat(context.getAuthType()).isNull();
        assertThat(context.getUsername()).isNull();
    }

    // -- OAuth2 --

    @Test
    public void testLoginOAuth2() {
        out.getBuffer().setLength(0);
        loginOAuth2();
        assertThat(out.toString())
                .as(withCliOutput("Should confirm OAuth2 login"))
                .contains("Logged in to context")
                .contains("OAuth2");

        var context = getTestContext();
        assertThat(context.getAuthType()).isEqualTo(ConfigModel.AUTH_TYPE_OAUTH2);
        assertThat(context.getTokenEndpoint()).isEqualTo(tokenEndpoint());
        assertThat(context.getClientId()).isEqualTo(TEST_CLIENT_ID);
        assertThat(context.getUsername()).isNull();
    }

    @Test
    public void testLoginOAuth2WithScope() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("login", "--token-endpoint", tokenEndpoint(),
                "--client-id", TEST_CLIENT_ID, "--client-secret", TEST_CLIENT_SECRET,
                "--scope", TEST_SCOPE);
        assertThat(out.toString())
                .as(withCliOutput("Should confirm OAuth2 login"))
                .contains("Logged in to context")
                .contains("OAuth2");

        var context = getTestContext();
        assertThat(context.getScope()).isEqualTo(TEST_SCOPE);
    }

    @Test
    public void testLogoutAfterOAuth2Login() {
        loginOAuth2();

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("logout");
        assertThat(out.toString())
                .as(withCliOutput("Should confirm logout"))
                .contains("Logged out of context");

        var context = getTestContext();
        assertThat(context.getAuthType()).isNull();
        assertThat(context.getTokenEndpoint()).isNull();
        assertThat(context.getClientId()).isNull();
        assertThat(context.getScope()).isNull();
    }

    // -- OIDC discovery --

    @Test
    public void testLoginOAuth2WithAuthServerUrl() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("login", "--client-id", TEST_CLIENT_ID,
                "--client-secret", TEST_CLIENT_SECRET,
                "--auth-server-url", TEST_AUTH_SERVER_URL);
        assertThat(out.toString())
                .as(withCliOutput("Should confirm OAuth2 login via discovery"))
                .contains("Logged in to context")
                .contains("OAuth2");

        var context = getTestContext();
        assertThat(context.getAuthType()).isEqualTo(ConfigModel.AUTH_TYPE_OAUTH2);
        assertThat(context.getAuthServerUrl()).isEqualTo(TEST_AUTH_SERVER_URL);
        assertThat(context.getTokenEndpoint())
                .isEqualTo(TEST_AUTH_SERVER_URL + DISCOVERED_TOKEN_PATH);
        assertThat(context.getClientId()).isEqualTo(TEST_CLIENT_ID);
        assertThat(context.getUsername()).isNull();
    }

    @Test
    public void testLoginOAuth2TokenEndpointTakesPrecedence() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("login", "--client-id", TEST_CLIENT_ID,
                "--client-secret", TEST_CLIENT_SECRET,
                "--token-endpoint", tokenEndpoint(),
                "--auth-server-url", TEST_AUTH_SERVER_URL);

        var context = getTestContext();
        assertThat(context.getTokenEndpoint())
                .as(withCliOutput("--token-endpoint should take precedence over --auth-server-url"))
                .isEqualTo(tokenEndpoint());
        assertThat(context.getAuthServerUrl())
                .as("authServerUrl should not be stored when --token-endpoint was explicit")
                .isNull();
    }

    @Test
    public void testLoginOAuth2WithAuthServerUrlAndScope() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("login", "--client-id", TEST_CLIENT_ID,
                "--client-secret", TEST_CLIENT_SECRET,
                "--auth-server-url", TEST_AUTH_SERVER_URL,
                "--scope", TEST_SCOPE);

        var context = getTestContext();
        assertThat(context.getScope()).isEqualTo(TEST_SCOPE);
        assertThat(context.getAuthServerUrl()).isEqualTo(TEST_AUTH_SERVER_URL);
    }

    @Test
    public void testLogoutClearsAuthServerUrl() {
        executeAndAssertSuccess("login", "--client-id", TEST_CLIENT_ID,
                "--client-secret", TEST_CLIENT_SECRET,
                "--auth-server-url", TEST_AUTH_SERVER_URL);

        executeAndAssertSuccess("logout");

        var context = getTestContext();
        assertThat(context.getAuthServerUrl()).isNull();
        assertThat(context.getTokenEndpoint()).isNull();
    }

    @Test
    public void testLoginOAuth2DiscoveryFailure() {
        final var testDiscovery = (TestOidcDiscovery) oidcDiscovery;
        try {
            testDiscovery.setFailOnDiscover(true);

            out.getBuffer().setLength(0);
            executeAndAssertFailure("login", "--client-id", TEST_CLIENT_ID,
                    "--client-secret", TEST_CLIENT_SECRET,
                    "--auth-server-url", TEST_AUTH_SERVER_URL);
            assertThat(err.toString())
                    .as(withCliOutput("Should report discovery failure"))
                    .contains("OIDC discovery failed");

            var context = getTestContext();
            assertThat(context.getAuthType())
                    .as("Auth type should not be set on failure")
                    .isNull();
        } finally {
            testDiscovery.setFailOnDiscover(false);
        }
    }

    @Test
    public void testLoginOAuth2AuthServerUrlWithoutClientId() {
        out.getBuffer().setLength(0);
        executeAndAssertFailure("login", "--auth-server-url", TEST_AUTH_SERVER_URL);
        assertThat(err.toString())
                .as(withCliOutput("Should require --client-id"))
                .contains("--client-id");
    }

    // -- OIDC discovery edge cases --

    @Test
    public void testLoginOAuth2InvalidAuthServerUrl() {
        out.getBuffer().setLength(0);
        executeAndAssertFailure("login", "--client-id", TEST_CLIENT_ID,
                "--client-secret", TEST_CLIENT_SECRET,
                "--auth-server-url", "not-a-url");
        assertThat(err.toString())
                .as(withCliOutput("Should reject invalid URL format"))
                .contains("Invalid --auth-server-url");
    }

    @Test
    public void testLoginOAuth2AuthServerUrlMissingScheme() {
        out.getBuffer().setLength(0);
        executeAndAssertFailure("login", "--client-id", TEST_CLIENT_ID,
                "--client-secret", TEST_CLIENT_SECRET,
                "--auth-server-url", "keycloak.example.com/realms/test");
        assertThat(err.toString())
                .as(withCliOutput("Should reject URL without http/https scheme"))
                .contains("http://")
                .contains("https://");
    }

    @Test
    public void testLoginOAuth2DiscoveryReturnsNoTokenEndpoint() {
        final var testDiscovery = (TestOidcDiscovery) oidcDiscovery;
        try {
            testDiscovery.setFailOnDiscover(true,
                    "OIDC discovery response does not contain a 'token_endpoint' field.");

            out.getBuffer().setLength(0);
            executeAndAssertFailure("login", "--client-id", TEST_CLIENT_ID,
                    "--client-secret", TEST_CLIENT_SECRET,
                    "--auth-server-url", TEST_AUTH_SERVER_URL);
            assertThat(err.toString())
                    .as(withCliOutput("Should report missing token_endpoint"))
                    .contains("token_endpoint");
        } finally {
            testDiscovery.setFailOnDiscover(false);
        }
    }

    @Test
    public void testLoginOAuth2DiscoveryReturns404() {
        final var testDiscovery = (TestOidcDiscovery) oidcDiscovery;
        try {
            testDiscovery.setFailOnDiscover(true,
                    "OIDC discovery failed: HTTP 404 from " + TEST_AUTH_SERVER_URL);

            out.getBuffer().setLength(0);
            executeAndAssertFailure("login", "--client-id", TEST_CLIENT_ID,
                    "--client-secret", TEST_CLIENT_SECRET,
                    "--auth-server-url", TEST_AUTH_SERVER_URL);
            assertThat(err.toString())
                    .as(withCliOutput("Should report HTTP 404"))
                    .contains("404");
        } finally {
            testDiscovery.setFailOnDiscover(false);
        }
    }

    // -- State transitions --

    @Test
    public void testLoginUsernameWithTokenEndpointConflicts() {
        out.getBuffer().setLength(0);
        executeAndAssertFailure("login", "--username", TEST_USERNAME, "--password", TEST_PASSWORD,
                "--token-endpoint", tokenEndpoint());
        assertThat(err.toString())
                .as(withCliOutput("Should reject conflicting --username and --token-endpoint"))
                .contains("Cannot combine")
                .contains("basic auth")
                .contains("OAuth2");
    }

    @Test
    public void testLoginUsernameWithAuthServerUrlConflicts() {
        out.getBuffer().setLength(0);
        executeAndAssertFailure("login", "--username", TEST_USERNAME, "--password", TEST_PASSWORD,
                "--auth-server-url", TEST_AUTH_SERVER_URL);
        assertThat(err.toString())
                .as(withCliOutput("Should reject conflicting --username and --auth-server-url"))
                .contains("Cannot combine")
                .contains("basic auth")
                .contains("OAuth2");
    }

    @Test
    public void testLoginOverwriteBasicWithOAuth2() {
        loginBasic();

        out.getBuffer().setLength(0);
        loginOAuth2();

        var context = getTestContext();
        assertThat(context.getAuthType()).isEqualTo(ConfigModel.AUTH_TYPE_OAUTH2);
        assertThat(context.getUsername()).isNull();
        assertThat(context.getTokenEndpoint()).isEqualTo(tokenEndpoint());
    }

    @Test
    public void testLoginReloginBasicDifferentUser() {
        executeAndAssertSuccess("login", "--username", "user1", "--password", "pass1");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("login", "--username", "user2", "--password", "pass2");
        assertThat(out.toString())
                .as(withCliOutput("Should confirm login as new user"))
                .contains("user2");

        var context = getTestContext();
        assertThat(context.getUsername()).isEqualTo("user2");
    }

    // -- Unsafe credential storage --

    @Test
    public void testLoginBasicWithUnsafeFlagKeychainAvailable() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("login", "--username", TEST_USERNAME, "--password", TEST_PASSWORD,
                "--allow-unsafe-credential-storage");
        assertThat(out.toString())
                .as(withCliOutput("Should login without warning when keychain works"))
                .contains("Logged in to context")
                .doesNotContain("not recommended for production");

        var context = getTestContext();
        assertThat(context.isUnsafeCredentialStorage())
                .as("Should be false — keychain succeeded, fallback not used")
                .isFalse();
    }

    @Test
    public void testLoginOAuth2WithUnsafeFlagKeychainAvailable() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("login", "--token-endpoint", tokenEndpoint(),
                "--client-id", TEST_CLIENT_ID, "--client-secret", TEST_CLIENT_SECRET,
                "--allow-unsafe-credential-storage");
        assertThat(out.toString())
                .as(withCliOutput("Should login without warning when keychain works"))
                .contains("Logged in to context")
                .doesNotContain("not recommended for production");

        var context = getTestContext();
        assertThat(context.isUnsafeCredentialStorage())
                .as("Should be false — keychain succeeded, fallback not used")
                .isFalse();
    }

    @Test
    public void testLogoutClearsUnsafeFlag() {
        executeAndAssertSuccess("login", "--username", TEST_USERNAME, "--password", TEST_PASSWORD,
                "--allow-unsafe-credential-storage");

        executeAndAssertSuccess("logout");

        var context = getTestContext();
        assertThat(context.isUnsafeCredentialStorage())
                .as("Logout should clear unsafeCredentialStorage flag")
                .isFalse();
    }

    @Test
    public void testLoginFallsBackToFileWhenKeychainFails() {
        final var testProvider = (TestCredentialProvider) credentialProvider;
        try {
            testProvider.setFailOnStore(true);

            out.getBuffer().setLength(0);
            executeAndAssertSuccess("login", "--username", TEST_USERNAME, "--password", TEST_PASSWORD,
                    "--allow-unsafe-credential-storage");
            assertThat(out.toString())
                    .as(withCliOutput("Should warn about file storage"))
                    .contains("not recommended for production");

            var context = getTestContext();
            assertThat(context.isUnsafeCredentialStorage())
                    .as("Should be true — file fallback was used")
                    .isTrue();
        } finally {
            testProvider.setFailOnStore(false);
        }
    }

    @Test
    public void testLoginOAuth2FallsBackToFileWhenKeychainFails() {
        final var testProvider = (TestCredentialProvider) credentialProvider;
        try {
            testProvider.setFailOnStore(true);

            out.getBuffer().setLength(0);
            executeAndAssertSuccess("login", "--token-endpoint", tokenEndpoint(),
                    "--client-id", TEST_CLIENT_ID, "--client-secret", TEST_CLIENT_SECRET,
                    "--allow-unsafe-credential-storage");
            assertThat(out.toString())
                    .as(withCliOutput("Should warn about file storage"))
                    .contains("not recommended for production");

            var context = getTestContext();
            assertThat(context.isUnsafeCredentialStorage())
                    .as("Should be true — file fallback was used")
                    .isTrue();
        } finally {
            testProvider.setFailOnStore(false);
        }
    }

    @Test
    public void testLoginFailsWithoutFlagWhenKeychainFails() {
        final var testProvider = (TestCredentialProvider) credentialProvider;
        try {
            testProvider.setFailOnStore(true);

            out.getBuffer().setLength(0);
            executeAndAssertFailure("login", "--username", TEST_USERNAME, "--password", TEST_PASSWORD);
            assertThat(err.toString())
                    .as(withCliOutput("Should suggest --allow-unsafe-credential-storage"))
                    .contains("allow-unsafe-credential-storage");
        } finally {
            testProvider.setFailOnStore(false);
        }
    }

    @Test
    public void testReloginHonorsSavedUnsafePreference() {
        final var testProvider = (TestCredentialProvider) credentialProvider;
        try {
            testProvider.setFailOnStore(true);

            // First login — file fallback used, preference saved
            executeAndAssertSuccess("login", "--username", TEST_USERNAME, "--password", TEST_PASSWORD,
                    "--allow-unsafe-credential-storage");
            assertThat(getTestContext().isUnsafeCredentialStorage()).isTrue();

            // Re-login without flag — should succeed via saved preference
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("login", "--username", TEST_USERNAME, "--password", TEST_PASSWORD);
            assertThat(out.toString())
                    .as(withCliOutput("Re-login should succeed without flag"))
                    .contains("Logged in to context");
        } finally {
            testProvider.setFailOnStore(false);
        }
    }

    // -- Helpers --

    private void loginBasic() {
        executeAndAssertSuccess("login", "--username", TEST_USERNAME, "--password", TEST_PASSWORD);
    }

    private void loginOAuth2() {
        executeAndAssertSuccess("login", "--token-endpoint", tokenEndpoint(),
                "--client-id", TEST_CLIENT_ID, "--client-secret", TEST_CLIENT_SECRET);
    }

    private String tokenEndpoint() {
        return registryUrl + TOKEN_PATH;
    }

    private ConfigModel.Context getTestContext() {
        return config.read().getContext().get(TEST_CONTEXT);
    }
}
