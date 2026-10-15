package io.apicurio.registry.storage.impl.sql;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public final class WebhookEventIdentity {

    private WebhookEventIdentity() {
    }

    /**
     * Computes the identity hash for the given CloudEvent (source, id) pair.
     *
     * @param source  the CloudEvent {@code source} attribute
     * @param eventId the CloudEvent {@code id} attribute
     * @return 64-character lowercase hexadecimal SHA-256 digest
     * @throws IllegalArgumentException if source or eventId is null, or contains malformed
     *         UTF-16 (e.g., an unpaired surrogate)
     */
    public static String computeHash(String source, String eventId) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        byte[] sourceBytes = encodeUtf8Strict(source, "source");
        byte[] idBytes = encodeUtf8Strict(eventId, "eventId");

        byte[] input = new byte[4 + sourceBytes.length + 4 + idBytes.length];
        int pos = 0;
        pos = writeLen4(input, pos, sourceBytes.length);
        System.arraycopy(sourceBytes, 0, input, pos, sourceBytes.length);
        pos += sourceBytes.length;
        pos = writeLen4(input, pos, idBytes.length);
        System.arraycopy(idBytes, 0, input, pos, idBytes.length);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Encodes {@code value} to UTF-8 bytes using strict error handling. Unlike
     * {@link String#getBytes(java.nio.charset.Charset)}, this method does not silently
     * replace malformed input (e.g., unpaired surrogates) with the replacement character;
     * it throws {@link IllegalArgumentException} instead.
     */
    private static byte[] encodeUtf8Strict(String value, String fieldName) {
        try {
            ByteBuffer buf = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(
                    fieldName + " contains malformed UTF-16 (unpaired surrogate): " + e.getMessage(), e);
        }
    }

    private static int writeLen4(byte[] buf, int pos, int len) {
        buf[pos] = (byte) ((len >>> 24) & 0xFF);
        buf[pos + 1] = (byte) ((len >>> 16) & 0xFF);
        buf[pos + 2] = (byte) ((len >>> 8) & 0xFF);
        buf[pos + 3] = (byte) (len & 0xFF);
        return pos + 4;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            sb.append("0123456789abcdef".charAt(v >>> 4));
            sb.append("0123456789abcdef".charAt(v & 0x0F));
        }
        return sb.toString();
    }
}
