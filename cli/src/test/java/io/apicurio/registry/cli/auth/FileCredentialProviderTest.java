package io.apicurio.registry.cli.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.cli.config.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class FileCredentialProviderTest {

    private static final String ACCOUNT = "test/client-secret";
    private static final String SECRET = "s3cr3t-value";

    @TempDir
    Path home;

    private FileCredentialProvider newProvider() {
        var config = new Config();
        config.setAcrCurrentHomePath(home);
        return new FileCredentialProvider(config);
    }

    @Test
    public void testStoredSecretIsEncryptedOnDisk() throws Exception {
        newProvider().store(ACCOUNT, SECRET);

        var raw = Files.readString(home.resolve("credentials.json"));
        assertThat(raw)
                .as("Secret must not appear in plain text on disk")
                .doesNotContain(SECRET);
        assertThat(raw)
                .as("Stored value should use the encrypted format")
                .contains(CredentialEncryption.ENCRYPTED_PREFIX);
    }

    @Test
    public void testRetrieveDecryptsTransparently() {
        var provider = newProvider();
        provider.store(ACCOUNT, SECRET);

        assertThat(provider.retrieve(ACCOUNT)).isEqualTo(SECRET);
    }

    @Test
    public void testKeyFileHasRestrictedPermissions() throws Exception {
        newProvider().store(ACCOUNT, SECRET);

        var keyFile = home.resolve("credentials.key");
        assertThat(Files.exists(keyFile)).isTrue();
        var view = Files.getFileAttributeView(keyFile, PosixFileAttributeView.class);
        if (view != null) {
            var permissions = Files.getPosixFilePermissions(keyFile);
            assertThat(permissions)
                    .as("Key file should only be readable/writable by the owner")
                    .containsExactlyInAnyOrder(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE);
        }
    }

    @Test
    public void testLegacyPlainTextSecretIsMigratedOnRetrieve() throws Exception {
        // Simulate an existing credentials.json from before encryption was introduced.
        Map<String, String> legacy = new HashMap<>();
        legacy.put(ACCOUNT, SECRET);
        Files.writeString(home.resolve("credentials.json"), new ObjectMapper().writeValueAsString(legacy));

        var provider = newProvider();

        // Reading it transparently returns the original secret...
        assertThat(provider.retrieve(ACCOUNT)).isEqualTo(SECRET);

        // ...and migrates the on-disk value to the encrypted format.
        var raw = Files.readString(home.resolve("credentials.json"));
        assertThat(raw).doesNotContain(SECRET);
        assertThat(raw).contains(CredentialEncryption.ENCRYPTED_PREFIX);

        // A second read (fresh provider instance, same key file) still resolves correctly.
        assertThat(newProvider().retrieve(ACCOUNT)).isEqualTo(SECRET);
    }

    @Test
    public void testRetrieveMissingAccountReturnsNull() {
        assertThat(newProvider().retrieve("does/not-exist")).isNull();
    }

    @Test
    public void testDeleteRemovesSecret() {
        var provider = newProvider();
        provider.store(ACCOUNT, SECRET);

        provider.delete(ACCOUNT);

        assertThat(provider.retrieve(ACCOUNT)).isNull();
    }

    @Test
    public void testRetrieveReturnsNullForTamperedCiphertext() throws Exception {
        newProvider().store(ACCOUNT, SECRET);

        var path = home.resolve("credentials.json");
        Map<String, String> credentials = new ObjectMapper().readValue(path.toFile(), new TypeReference<>() {});
        var encrypted = credentials.get(ACCOUNT);
        var prefixEnd = CredentialEncryption.ENCRYPTED_PREFIX.length();
        var separator = encrypted.indexOf(':', prefixEnd);
        var iv = encrypted.substring(prefixEnd, separator);
        var ciphertext = encrypted.substring(separator + 1);
        // Flip a byte inside the ciphertext (keeping base64 length/padding intact) so
        // GCM tag verification fails on decrypt rather than base64 decoding.
        var decoded = Base64.getDecoder().decode(ciphertext);
        decoded[0] ^= 0x01;
        var tamperedCiphertext = Base64.getEncoder().encodeToString(decoded);
        credentials.put(ACCOUNT, CredentialEncryption.ENCRYPTED_PREFIX + iv + ":" + tamperedCiphertext);
        Files.writeString(path, new ObjectMapper().writeValueAsString(credentials));

        // A corrupted/tampered credential must not surface a stack trace to the caller —
        // retrieve() reports it as absent so the CLI can prompt the user to log in again.
        assertThat(newProvider().retrieve(ACCOUNT)).isNull();
    }

    @Test
    public void testRetrieveReturnsNullForMalformedEncryptedValueMissingSeparator() throws Exception {
        Map<String, String> credentials = new HashMap<>();
        credentials.put(ACCOUNT, CredentialEncryption.ENCRYPTED_PREFIX + "not-a-valid-payload");
        Files.writeString(home.resolve("credentials.json"), new ObjectMapper().writeValueAsString(credentials));

        assertThat(newProvider().retrieve(ACCOUNT)).isNull();
    }

    @Test
    public void testRetrieveReturnsNullForMalformedEncryptedValueInvalidBase64() throws Exception {
        Map<String, String> credentials = new HashMap<>();
        credentials.put(ACCOUNT, CredentialEncryption.ENCRYPTED_PREFIX + "not-base64!!:also-not-base64!!");
        Files.writeString(home.resolve("credentials.json"), new ObjectMapper().writeValueAsString(credentials));

        assertThat(newProvider().retrieve(ACCOUNT)).isNull();
    }
}
