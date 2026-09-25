package io.apicurio.registry.storage.impl.polling;

import io.apicurio.registry.content.ContentHandle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Generates deterministic IDs for content and artifact versions based on stable inputs.
 * <p>
 * IDs are derived from SHA-256 hashes and mapped to the upper half of the positive long range
 * ({@link #GENERATED_ID_MIN} to {@link Long#MAX_VALUE}), leaving the lower half
 * (1 to {@link #GENERATED_ID_MIN} - 1) available for user-specified explicit IDs.
 * <p>
 * This ensures IDs are stable across reloads regardless of file processing order,
 * and avoids collisions with user-specified IDs.
 */
public final class DeterministicIdGenerator {

    /**
     * Lower bound (inclusive) of the generated ID range.
     * Manual IDs should be in the range [1, GENERATED_ID_MIN - 1].
     */
    public static final long GENERATED_ID_MIN = Long.MAX_VALUE / 2 + 1;

    private DeterministicIdGenerator() {
    }

    /**
     * Generates a deterministic contentId from the content bytes.
     * Same content always produces the same ID.
     *
     * @param content the content bytes
     * @return a deterministic ID in the upper half of positive longs
     */
    public static long contentId(ContentHandle content) {
        return hashToId(content.bytes());
    }

    /**
     * Generates a deterministic globalId from artifact version coordinates.
     * Same (groupId, artifactId, version) always produces the same ID.
     *
     * @param groupId    the group ID
     * @param artifactId the artifact ID
     * @param version    the version string
     * @return a deterministic ID in the upper half of positive longs
     */
    public static long globalId(String groupId, String artifactId, String version) {
        String key = groupId + "\0" + artifactId + "\0" + version;
        return hashToId(key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Maps a byte array to a deterministic long in the range [GENERATED_ID_MIN, Long.MAX_VALUE].
     */
    private static long hashToId(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);

            // Take first 8 bytes of the hash as a long
            long value = 0;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (hash[i] & 0xFF);
            }

            // Map to the upper half of positive longs: [GENERATED_ID_MIN, Long.MAX_VALUE]
            // Ensure positive by clearing the sign bit, then shift into the upper half range
            value = value & Long.MAX_VALUE; // ensure positive
            long range = Long.MAX_VALUE - GENERATED_ID_MIN + 1;
            return GENERATED_ID_MIN + (value % range);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
