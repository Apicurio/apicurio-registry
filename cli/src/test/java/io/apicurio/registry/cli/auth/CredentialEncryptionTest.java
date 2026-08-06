package io.apicurio.registry.cli.auth;

import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CredentialEncryptionTest {

    private static final String SECRET = "s3cr3t-value";

    @TempDir
    Path home;

    private CredentialEncryption newEncryption() {
        return new CredentialEncryption(home.resolve("credentials.key"));
    }

    @Test
    public void testEncryptDecryptRoundTrip() {
        var encryption = newEncryption();

        var encrypted = encryption.encrypt(SECRET);

        assertThat(CredentialEncryption.isEncrypted(encrypted)).isTrue();
        assertThat(encryption.decrypt(encrypted)).isEqualTo(SECRET);
    }

    @Test
    public void testDecryptRejectsTamperedCiphertext() {
        var encryption = newEncryption();
        var encrypted = encryption.encrypt(SECRET);

        var prefixEnd = CredentialEncryption.ENCRYPTED_PREFIX.length();
        var separator = encrypted.indexOf(':', prefixEnd);
        var iv = encrypted.substring(prefixEnd, separator);
        var ciphertext = encrypted.substring(separator + 1);
        // Flip a byte inside the ciphertext (keeping base64 length/padding intact) so
        // GCM tag verification fails on decrypt rather than base64 decoding.
        var decoded = Base64.getDecoder().decode(ciphertext);
        decoded[0] ^= 0x01;
        var tamperedCiphertext = Base64.getEncoder().encodeToString(decoded);
        var tampered = CredentialEncryption.ENCRYPTED_PREFIX + iv + ":" + tamperedCiphertext;

        assertThatThrownBy(() -> encryption.decrypt(tampered))
                .isInstanceOf(CredentialStoreException.class)
                .hasMessage("Failed to decrypt credential");
    }

    @Test
    public void testDecryptRejectsMalformedValueMissingSeparator() {
        var encryption = newEncryption();
        var malformed = CredentialEncryption.ENCRYPTED_PREFIX + "not-a-valid-payload";

        assertThatThrownBy(() -> encryption.decrypt(malformed))
                .isInstanceOf(CredentialStoreException.class)
                .hasMessage("Corrupted encrypted credential — re-run login");
    }

    @Test
    public void testDecryptRejectsMalformedValueInvalidBase64() {
        var encryption = newEncryption();
        var malformed = CredentialEncryption.ENCRYPTED_PREFIX + "not-base64!!:also-not-base64!!";

        assertThatThrownBy(() -> encryption.decrypt(malformed))
                .isInstanceOf(CredentialStoreException.class)
                .hasMessage("Corrupted encrypted credential — re-run login");
    }
}
