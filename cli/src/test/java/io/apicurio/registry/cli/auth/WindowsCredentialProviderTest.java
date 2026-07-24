package io.apicurio.registry.cli.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real Windows Credential Manager. A unique service name is used per test
 * so nothing collides with, or is left behind in, the credentials of the user running the build.
 */
@EnabledOnOs(OS.WINDOWS)
public class WindowsCredentialProviderTest {

    private static final String SECRET = "s3cr3t-value";

    private WindowsCredentialProvider provider;
    private List<String> storedAccounts;

    @BeforeEach
    public void setUp() {
        provider = new WindowsCredentialProvider("apicurio-registry-cli-test-" + UUID.randomUUID());
        storedAccounts = new ArrayList<>();
    }

    @AfterEach
    public void tearDown() {
        storedAccounts.forEach(account -> provider.delete(account));
    }

    @Test
    public void testStoreAndRetrieve() {
        provider.store(account("dev/password"), SECRET);

        assertThat(provider.retrieve("dev/password")).isEqualTo(SECRET);
    }

    @Test
    public void testStoreOverwritesPreviousSecret() {
        provider.store(account("dev/password"), SECRET);
        provider.store("dev/password", "replacement");

        assertThat(provider.retrieve("dev/password")).isEqualTo("replacement");
    }

    @Test
    public void testStoreAndRetrieveNonAsciiSecret() {
        // The blob is written as UTF-16LE; an ASCII-only test would not catch a wrong charset.
        final String secret = "pä$$wörd-é中文";
        provider.store(account("dev/password"), secret);

        assertThat(provider.retrieve("dev/password")).isEqualTo(secret);
    }

    @Test
    public void testAccountsAreStoredIndependently() {
        provider.store(account("dev/password"), SECRET);
        provider.store(account("prod/password"), "other-secret");

        assertThat(provider.retrieve("dev/password")).isEqualTo(SECRET);
        assertThat(provider.retrieve("prod/password")).isEqualTo("other-secret");
    }

    @Test
    public void testRetrieveReturnsNullWhenNotFound() {
        assertThat(provider.retrieve("dev/missing")).isNull();
    }

    @Test
    public void testDeleteRemovesTheSecret() {
        provider.store(account("dev/password"), SECRET);

        provider.delete("dev/password");

        assertThat(provider.retrieve("dev/password")).isNull();
    }

    @Test
    public void testDeleteIsIdempotent() {
        provider.store(account("dev/password"), SECRET);

        provider.delete("dev/password");
        provider.delete("dev/password");

        assertThat(provider.retrieve("dev/password")).isNull();
    }

    @Test
    public void testStoreRejectsSecretLargerThanTheCredentialManagerLimit() {
        // The limit is 2560 bytes, and each character of the blob takes two bytes.
        final String secret = "x".repeat(1281);

        assertThatThrownBy(() -> provider.store("dev/password", secret))
                .isInstanceOf(CredentialStoreException.class)
                .hasMessageContaining("2560")
                .hasMessageNotContaining(secret);
    }

    private String account(final String account) {
        storedAccounts.add(account);
        return account;
    }
}
