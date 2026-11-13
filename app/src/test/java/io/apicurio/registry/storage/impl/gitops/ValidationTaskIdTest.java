package io.apicurio.registry.storage.impl.gitops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationTaskIdTest {

    private static final byte[] TEST_KEY = "test-hmac-secret-key-1234567890".getBytes(StandardCharsets.UTF_8);

    @Test
    void encodeDecodeRoundTrip() {
        final Instant now = Instant.now();
        final ValidationTaskId id = ValidationTaskId.encode("pull", "default", "refs/pull/123/head", now, TEST_KEY);
        final ValidationTaskPayload payload = id.decode(TEST_KEY, 3600);
        assertEquals("pull", payload.type());
        assertEquals("default", payload.repoId());
        assertEquals("refs/pull/123/head", payload.ref());
        assertEquals(now.getEpochSecond(), payload.createdAt());
    }

    @Test
    void decodeRejectsWrongHmacKey() {
        final byte[] key2 = "wrong-hmac-secret-key-123456789".getBytes(StandardCharsets.UTF_8);
        final ValidationTaskId id = ValidationTaskId.encode("pull", "default", "main", Instant.now(), TEST_KEY);
        assertThrows(IllegalArgumentException.class, () -> id.decode(key2, 3600));
    }

    @Test
    void decodeRejectsExpiredCreatedAt() {
        final Instant old = Instant.now().minusSeconds(7200);
        final ValidationTaskId id = ValidationTaskId.encode("pull", "default", "main", old, TEST_KEY);
        assertThrows(IllegalArgumentException.class, () -> id.decode(TEST_KEY, 3600));
    }

    @Test
    void decodeRejectsFutureCreatedAt() {
        final Instant future = Instant.now().plusSeconds(7200);
        final ValidationTaskId id = ValidationTaskId.encode("pull", "default", "main", future, TEST_KEY);
        assertThrows(IllegalArgumentException.class, () -> id.decode(TEST_KEY, 3600));
    }

    @Test
    void decodeAcceptsMinorClockSkew() {
        final Instant slightFuture = Instant.now().plusSeconds(30);
        final ValidationTaskId id = ValidationTaskId.encode("pull", "default", "main", slightFuture, TEST_KEY);
        assertDoesNotThrow(() -> id.decode(TEST_KEY, 3600));
    }

    @Test
    void isValidAcceptsSelfDescribingIds() {
        final ValidationTaskId id = ValidationTaskId.encode("pull", "default", "main", Instant.now(), TEST_KEY);
        assertTrue(ValidationTaskId.isValid(id.value()));
    }

    @Test
    void isValidRejectsOldFormatIds() {
        assertFalse(ValidationTaskId.isValid("abcdef012345"));
    }

    @Test
    void isValidRejectsExcessivelyLongIds() {
        final String tooLong = "A".repeat(501);
        assertFalse(ValidationTaskId.isValid(tooLong));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nullAndEmptyAreRejected(String value) {
        assertFalse(ValidationTaskId.isValid(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../etc/passwd",
            "/etc/passwd/",
            "abcdef 12345",
            "has spaces in it 123456789"
    })
    void pathTraversalAndSpecialCharsRejected(String value) {
        assertFalse(ValidationTaskId.isValid(value));
    }

    @Test
    void localFileNameIsStable() {
        final ValidationTaskId id = ValidationTaskId.encode("pull", "default", "main", Instant.now(), TEST_KEY);
        final String name = id.localFileName();
        assertEquals(12, name.length());
        assertTrue(name.matches("^[a-f0-9]{12}$"));
        assertEquals(name, id.localFileName());
    }

    @Test
    void localFileNameDiffersForDifferentIds() {
        final ValidationTaskId id1 = ValidationTaskId.encode("pull", "default", "main", Instant.now(), TEST_KEY);
        final ValidationTaskId id2 = ValidationTaskId.encode("pull", "default", "develop", Instant.now(), TEST_KEY);
        assertNotEquals(id1.localFileName(), id2.localFileName());
    }

    @Test
    void decodeRejectsTruncatedId() {
        final String shortBase64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new byte[]{0x01, '{', '}'});
        assertFalse(ValidationTaskId.isValid(shortBase64));
    }

    @Test
    void decodeRejectsCraftedBase64WithBadHmac() {
        final byte[] fakePayload = "{\"type\":\"pull\",\"repoId\":\"x\",\"ref\":\"m\",\"createdAt\":0}".getBytes(StandardCharsets.UTF_8);
        final byte[] raw = new byte[1 + fakePayload.length + 16];
        raw[0] = 0x01;
        System.arraycopy(fakePayload, 0, raw, 1, fakePayload.length);
        final String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        final var id = new ValidationTaskId(encoded);
        assertThrows(IllegalArgumentException.class, () -> id.decode(TEST_KEY, 3600));
    }

    @Test
    void encodeRejectsNullType() {
        final Instant now = Instant.now();
        assertThrows(NullPointerException.class,
                () -> ValidationTaskId.encode(null, "default", "main", now, TEST_KEY));
    }

    @Test
    void encodeRejectsNullRepoId() {
        final Instant now = Instant.now();
        assertThrows(NullPointerException.class,
                () -> ValidationTaskId.encode("pull", null, "main", now, TEST_KEY));
    }

    @Test
    void encodeRejectsNullRef() {
        final Instant now = Instant.now();
        assertThrows(NullPointerException.class,
                () -> ValidationTaskId.encode("pull", "default", null, now, TEST_KEY));
    }

    @Test
    void encodeRejectsTooLongRef() {
        final String longRef = "refs/heads/" + "a".repeat(250);
        final Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> ValidationTaskId.encode("pull", "default", longRef, now, TEST_KEY));
    }

    @Test
    void constructorRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new ValidationTaskId(null));
    }

    @Test
    void toStringReturnsValue() {
        final ValidationTaskId id = ValidationTaskId.encode("pull", "default", "main", Instant.now(), TEST_KEY);
        assertNotNull(id.toString());
        assertEquals(id.value(), id.toString());
    }

    @Test
    void encodedIdIsUrlSafe() {
        final ValidationTaskId id = ValidationTaskId.encode("pull", "default", "refs/pull/999/head", Instant.now(), TEST_KEY);
        final String value = id.value();
        assertTrue(value.matches("^[A-Za-z0-9_-]+$"));
        assertFalse(value.contains("="));
        assertFalse(value.contains("+"));
        assertFalse(value.contains("/"));
    }

    @Test
    void differentKeysProduceDifferentIds() {
        final byte[] key2 = "another-hmac-key-for-testing-12".getBytes(StandardCharsets.UTF_8);
        final Instant now = Instant.now();
        final ValidationTaskId id1 = ValidationTaskId.encode("pull", "default", "main", now, TEST_KEY);
        final ValidationTaskId id2 = ValidationTaskId.encode("pull", "default", "main", now, key2);
        assertNotEquals(id1.value(), id2.value());
    }
}
