package io.apicurio.registry.cli;

import io.apicurio.registry.cli.config.ConfigModel;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class AuthCommandTest extends AbstractCLITest {

    private static final String TOKEN_PATH = "/token";
    private static final String TEST_CONTEXT = "test";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "testpass";
    private static final String TEST_CLIENT_ID = "test-client";
    private static final String TEST_CLIENT_SECRET = "test-secret";
    private static final String TEST_SCOPE = "openid profile";

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
        executeAndAssertFailure("login", "--token-endpoint", tokenEndpoint());
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

    // -- State transitions --

    @Test
    public void testLoginUsernameAndTokenEndpoint() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("login", "--username", TEST_USERNAME, "--password", TEST_PASSWORD,
                "--token-endpoint", tokenEndpoint());

        var context = getTestContext();
        assertThat(context.getAuthType())
                .as(withCliOutput("--username should take precedence over --token-endpoint"))
                .isEqualTo(ConfigModel.AUTH_TYPE_BASIC);
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
    public void testLoginBasicWithUnsafeStorage() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("login", "--username", TEST_USERNAME, "--password", TEST_PASSWORD,
                "--allow-unsafe-credential-storage");
        assertThat(out.toString())
                .as(withCliOutput("Should warn about unsafe storage"))
                .contains("Logged in to context")
                .contains("not recommended for production");

        var context = getTestContext();
        assertThat(context.isUnsafeCredentialStorage()).isTrue();
    }

    @Test
    public void testLoginOAuth2WithUnsafeStorage() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("login", "--token-endpoint", tokenEndpoint(),
                "--client-id", TEST_CLIENT_ID, "--client-secret", TEST_CLIENT_SECRET,
                "--allow-unsafe-credential-storage");
        assertThat(out.toString())
                .as(withCliOutput("Should warn about unsafe storage"))
                .contains("Logged in to context")
                .contains("not recommended for production");

        var context = getTestContext();
        assertThat(context.isUnsafeCredentialStorage()).isTrue();
    }

    @Test
    public void testLogoutClearsUnsafeStorage() {
        executeAndAssertSuccess("login", "--username", TEST_USERNAME, "--password", TEST_PASSWORD,
                "--allow-unsafe-credential-storage");

        executeAndAssertSuccess("logout");

        var context = getTestContext();
        assertThat(context.isUnsafeCredentialStorage()).isFalse();
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
