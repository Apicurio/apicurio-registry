package io.apicurio.registry.storage.impl.sql;

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
     */
    public static String computeHash(String source, String eventId) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);
        byte[] idBytes = eventId.getBytes(StandardCharsets.UTF_8);

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
