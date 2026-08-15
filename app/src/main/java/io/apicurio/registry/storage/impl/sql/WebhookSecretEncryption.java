package io.apicurio.registry.storage.impl.sql;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts and decrypts the per-subscription HMAC secret before it is written to or after it is read
 * from the SQL storage layer. Uses AES-256-GCM with a 12-byte IV and a 128-bit tag. The encoded form
 * is self-contained: {@code enc:v1:<base64-iv>:<base64-ciphertext>}.
 */
public class WebhookSecretEncryption {

    public static final String ENCRYPTED_PREFIX = "enc:v1:";

    private static final String KEY_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE_BITS = 256;
    private static final int KEY_SIZE_BYTES = KEY_SIZE_BITS / 8;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    // SecureRandom is deliberately an instance field. A static SecureRandom is initialized during
    // native-image class init (build time), which GraalVM rejects because it would bake in a fixed
    // seed. Constructing per-instance keeps initialization at runtime.
    private final SecureRandom random = new SecureRandom();

    private final SecretKey key;

    public WebhookSecretEncryption(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != KEY_SIZE_BYTES) {
            throw new IllegalArgumentException("Webhook secret encryption key must be " + KEY_SIZE_BYTES
                    + " bytes for AES-256");
        }
        this.key = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }

    public static WebhookSecretEncryption fromBase64Key(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("Webhook secret encryption key must not be empty");
        }
        return new WebhookSecretEncryption(Base64.getDecoder().decode(base64Key));
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return ENCRYPTED_PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt webhook secret", ex);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        if (!isEncrypted(encoded)) {
            throw new IllegalArgumentException("Webhook secret is not in the expected encrypted format");
        }
        try {
            String[] parts = encoded.substring(ENCRYPTED_PREFIX.length()).split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Corrupt encrypted webhook secret");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Corrupt encrypted webhook secret", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt webhook secret", ex);
        }
    }
}
