package io.apicurio.registry.storage.impl.gitops;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.util.JsonObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Self-describing validation task identifier. The ID encodes the validation request
 * (type, repoId, ref, createdAt) as base64url(version_byte || json || hmac_16bytes),
 * so any replica can decode it and run the validation without shared storage.
 */
public record ValidationTaskId(String value) {

    private static final Pattern VALID_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{20,500}$");
    private static final Pattern LOCAL_FILENAME_FORMAT = Pattern.compile("^[a-f0-9]{12}$");
    private static final byte VERSION_1 = 0x01;
    private static final int HMAC_LENGTH = 16;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long MAX_FUTURE_SKEW_SECONDS = 60;
    private static final int MAX_REF_LENGTH = 256;

    public ValidationTaskId {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid task ID format");
        }
    }

    public static boolean isValid(String value) {
        return value != null && VALID_FORMAT.matcher(value).matches();
    }

    public static boolean isValidLocalFileName(String name) {
        return name != null && LOCAL_FILENAME_FORMAT.matcher(name).matches();
    }

    public static ValidationTaskId encode(String type, String repoId, String ref,
            Instant createdAt, byte[] hmacKey) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(repoId, "repoId");
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(hmacKey, "hmacKey");
        if (ref.length() > MAX_REF_LENGTH) {
            throw new IllegalArgumentException("Ref exceeds maximum length of " + MAX_REF_LENGTH);
        }

        final var payload = new ValidationTaskPayload(type, repoId, ref, createdAt.getEpochSecond());
        final byte[] jsonBytes;
        try {
            jsonBytes = JsonObjectMapper.MAPPER.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize task payload", e);
        }

        final byte[] raw = new byte[1 + jsonBytes.length + HMAC_LENGTH];
        raw[0] = VERSION_1;
        System.arraycopy(jsonBytes, 0, raw, 1, jsonBytes.length);

        final byte[] hmac = computeHmac(hmacKey, raw, 0, 1 + jsonBytes.length);
        System.arraycopy(hmac, 0, raw, 1 + jsonBytes.length, HMAC_LENGTH);

        final String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        return new ValidationTaskId(encoded);
    }

    public ValidationTaskPayload decode(byte[] hmacKey, int ttlSeconds) {
        Objects.requireNonNull(hmacKey, "hmacKey");

        final byte[] raw;
        try {
            raw = Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64url encoding");
        }

        if (raw.length < 1 + HMAC_LENGTH + 2) {
            throw new IllegalArgumentException("Task ID too short");
        }

        if (raw[0] != VERSION_1) {
            throw new IllegalArgumentException("Unsupported task ID version: " + raw[0]);
        }

        final int jsonLength = raw.length - 1 - HMAC_LENGTH;
        final byte[] expectedHmac = computeHmac(hmacKey, raw, 0, 1 + jsonLength);
        final byte[] actualHmac = Arrays.copyOfRange(raw, 1 + jsonLength, raw.length);

        if (!MessageDigest.isEqual(expectedHmac, actualHmac)) {
            throw new IllegalArgumentException("HMAC verification failed");
        }

        final byte[] jsonBytes = Arrays.copyOfRange(raw, 1, 1 + jsonLength);
        final ValidationTaskPayload payload;
        try {
            payload = JsonObjectMapper.MAPPER.readValue(jsonBytes, ValidationTaskPayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse task payload", e);
        }

        final Instant createdAt = payload.createdAtInstant();
        final Instant now = Instant.now();

        if (createdAt.isAfter(now.plusSeconds(MAX_FUTURE_SKEW_SECONDS))) {
            throw new IllegalArgumentException("Task ID createdAt is in the future");
        }

        if (createdAt.plusSeconds(ttlSeconds).isBefore(now)) {
            throw new IllegalArgumentException("Task ID has expired");
        }

        return payload;
    }

    public String localFileName() {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder(12);
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public String toString() {
        return value;
    }

    private static byte[] computeHmac(byte[] key, byte[] data, int offset, int length) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            mac.update(data, offset, length);
            final byte[] fullHmac = mac.doFinal();
            return Arrays.copyOf(fullHmac, HMAC_LENGTH);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}
