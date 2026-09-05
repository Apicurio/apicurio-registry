package io.apicurio.registry.mcpregistry;

import jakarta.ws.rs.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The MCP Registry API paginates with an opaque cursor, while the registry's storage layer paginates by
 * offset. This translates between the two: a cursor is an offset plus a fingerprint of the filters that
 * produced it.
 *
 * The fingerprint is what makes the offset safe to hand out. An offset is only meaningful against the query
 * that produced it, so a cursor presented alongside different filters is rejected rather than silently
 * returning a page from somewhere else in a different result set.
 */
public final class McpRegistryCursor {

    private static final String PREFIX = "v1";
    private static final String SEPARATOR = ":";
    private static final int FINGERPRINT_LENGTH = 16;

    private McpRegistryCursor() {
    }

    /**
     * Encodes the offset of the next page.
     *
     * @param offset the offset the next page starts at
     * @param fingerprintSource a stable string describing the filters of the current query
     */
    public static String encode(int offset, String fingerprintSource) {
        String raw = PREFIX + SEPARATOR + offset + SEPARATOR + fingerprint(fingerprintSource);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a cursor into the offset it carries.
     *
     * @param cursor the cursor, which may be null or empty to mean "start at the beginning"
     * @param fingerprintSource a stable string describing the filters of the current query
     * @return the offset to start the page at
     * @throws BadRequestException if the cursor is malformed or was issued for different filters
     */
    public static int decode(String cursor, String fingerprintSource) {
        if (cursor == null || cursor.isEmpty()) {
            return 0;
        }

        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid cursor");
        }

        String[] parts = raw.split(SEPARATOR);
        if (parts.length != 3 || !PREFIX.equals(parts[0])) {
            throw new BadRequestException("Invalid cursor");
        }
        if (!fingerprint(fingerprintSource).equals(parts[2])) {
            throw new BadRequestException(
                    "Invalid cursor: it was issued for a different set of search filters");
        }

        int offset;
        try {
            offset = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid cursor");
        }
        if (offset < 0) {
            throw new BadRequestException("Invalid cursor");
        }
        return offset;
    }

    private static String fingerprint(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, FINGERPRINT_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every JRE
            throw new IllegalStateException(e);
        }
    }
}
