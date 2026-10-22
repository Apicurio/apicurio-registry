package io.apicurio.registry.cli.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.jboss.logging.Logger;

/**
 * Encrypts and decrypts secrets stored by {@link FileCredentialProvider} using an
 * AES-256-GCM key kept in a separate, permission-restricted file next to credentials.json.
 * This keeps a leaked or backed-up credentials.json from exposing secrets on its own.
 *
 * <p>Threat model: since {@code credentials.key} is stored unencrypted in the same directory
 * as {@code credentials.json}, this only defends against a single-file leak or backup of
 * credentials.json — it does not protect against compromise of the whole config directory,
 * which exposes both the key and the ciphertext. This is not a keychain-equivalent; it exists
 * only for the fallback path used when no OS keychain is available.
 */
class CredentialEncryption {

    private static final Logger log = Logger.getLogger(CredentialEncryption.class);

    static final String ENCRYPTED_PREFIX = "enc:v1:";

    private static final String KEY_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE_BITS = 256;
    private static final int KEY_SIZE_BYTES = KEY_SIZE_BITS / 8;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    // Deliberately an instance field, not a static final constant: a static SecureRandom is
    // initialized during native-image class-init (build time), which GraalVM rejects — building
    // it in the image heap would bake in a fixed/cached seed. Constructing it per-instance keeps
    // initialization at runtime, after the image has started.
    private final SecureRandom random = new SecureRandom();

    private final Path keyPath;
    private SecretKey key;

    CredentialEncryption(final Path keyPath) {
        this.keyPath = keyPath;
    }

    static boolean isEncrypted(final String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    String encrypt(final String plaintext) {
        try {
            final var iv = new byte[GCM_IV_LENGTH_BYTES];
            random.nextBytes(iv);
            final var cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            final var ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ENCRYPTED_PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (GeneralSecurityException | IOException ex) {
            throw new CredentialStoreException("Failed to encrypt credential", ex);
        }
    }

    String decrypt(final String encoded) {
        final byte[] iv;
        final byte[] ciphertext;
        try {
            final var parts = encoded.substring(ENCRYPTED_PREFIX.length()).split(":", 2);
            if (parts.length != 2) {
                throw new CredentialStoreException("Corrupted encrypted credential — re-run login");
            }
            iv = Base64.getDecoder().decode(parts[0]);
            ciphertext = Base64.getDecoder().decode(parts[1]);
        } catch (IllegalArgumentException ex) {
            throw new CredentialStoreException("Corrupted encrypted credential — re-run login", ex);
        }
        try {
            final var cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, loadOrCreateKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IOException ex) {
            throw new CredentialStoreException("Failed to decrypt credential", ex);
        }
    }

    private synchronized SecretKey loadOrCreateKey() throws IOException, GeneralSecurityException {
        if (key != null) {
            return key;
        }
        if (!Files.exists(keyPath)) {
            generateKeyFile();
        }
        var keyBytes = Files.readAllBytes(keyPath);
        if (keyBytes.length != KEY_SIZE_BYTES) {
            log.warnf("Encryption key file (%s) has an unexpected length — regenerating it."
                    + " Credentials encrypted with the old key will need to be re-entered.", keyPath);
            Files.delete(keyPath);
            generateKeyFile();
            keyBytes = Files.readAllBytes(keyPath);
        }
        key = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        return key;
    }

    private void generateKeyFile() throws IOException, GeneralSecurityException {
        final KeyGenerator generator = KeyGenerator.getInstance(KEY_ALGORITHM);
        generator.init(KEY_SIZE_BITS);
        final var temp = Files.createTempFile(keyPath.getParent(), "credentials-key", ".tmp");
        try {
            restrictFilePermissions(temp);
            Files.write(temp, generator.generateKey().getEncoded());
            try {
                Files.move(temp, keyPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                // Filesystem doesn't support atomic moves (e.g. NFS, some cloud mounts) —
                // fall back to a plain move, which still fails with FileAlreadyExistsException
                // if the target was created concurrently.
                Files.move(temp, keyPath);
            }
        } catch (FileAlreadyExistsException ex) {
            // Another process created the key file concurrently — use it instead.
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void restrictFilePermissions(final Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ex) {
            // Non-POSIX filesystem — best effort only.
        }
    }
}
