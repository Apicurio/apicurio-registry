package io.apicurio.registry.cli.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

/**
 * Encrypts and decrypts secrets stored by {@link FileCredentialProvider} using an
 * AES-256-GCM key kept in a separate, permission-restricted file next to credentials.json.
 * This keeps a leaked or backed-up credentials.json from exposing secrets on its own.
 */
class CredentialEncryption {

    static final String ENCRYPTED_PREFIX = "enc:v1:";

    private static final String KEY_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE_BITS = 256;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

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
            new SecureRandom().nextBytes(iv);
            final var cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            final var ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ENCRYPTED_PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (GeneralSecurityException | IOException ex) {
            throw new CredentialStoreException("Failed to encrypt credential: " + ex.getMessage(), ex);
        }
    }

    String decrypt(final String encoded) {
        try {
            final var parts = encoded.substring(ENCRYPTED_PREFIX.length()).split(":", 2);
            final var iv = Base64.getDecoder().decode(parts[0]);
            final var ciphertext = Base64.getDecoder().decode(parts[1]);
            final var cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, loadOrCreateKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IOException ex) {
            throw new CredentialStoreException("Failed to decrypt credential: " + ex.getMessage(), ex);
        }
    }

    private synchronized SecretKey loadOrCreateKey() throws IOException, GeneralSecurityException {
        if (key != null) {
            return key;
        }
        if (!Files.exists(keyPath)) {
            generateKeyFile();
        }
        key = new SecretKeySpec(Files.readAllBytes(keyPath), KEY_ALGORITHM);
        return key;
    }

    private void generateKeyFile() throws IOException, GeneralSecurityException {
        final KeyGenerator generator = KeyGenerator.getInstance(KEY_ALGORITHM);
        generator.init(KEY_SIZE_BITS);
        final var temp = Files.createTempFile(keyPath.getParent(), "credentials-key", ".tmp");
        try {
            Files.write(temp, generator.generateKey().getEncoded());
            restrictFilePermissions(temp);
            Files.move(temp, keyPath, StandardCopyOption.ATOMIC_MOVE);
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
