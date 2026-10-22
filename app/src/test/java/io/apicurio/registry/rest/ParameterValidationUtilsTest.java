package io.apicurio.registry.rest;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the pagination normalization helpers in {@link ParameterValidationUtils}.
 * Covers issue #9369: negative and overflowing offset/limit values must be normalized rather
 * than propagated to the storage layer, and v2's normalizeLimitUnbounded must not apply v3's
 * 1000-row cap.
 */
class ParameterValidationUtilsTest {

    @Test
    void normalizeOffsetClampsNegativeToZero() {
        assertEquals(0, ParameterValidationUtils.normalizeOffset(BigInteger.valueOf(-1)));
    }

    @Test
    void normalizeOffsetClampsOverflowToMaxInt() {
        BigInteger overflow = BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE);
        assertEquals(Integer.MAX_VALUE, ParameterValidationUtils.normalizeOffset(overflow));
    }

    @Test
    void normalizeOffsetPassesThroughValidValue() {
        assertEquals(42, ParameterValidationUtils.normalizeOffset(BigInteger.valueOf(42)));
    }

    @Test
    void normalizeLimitNormalizesNegativeToOne() {
        assertEquals(1, ParameterValidationUtils.normalizeLimit(BigInteger.valueOf(-1)));
    }

    @Test
    void normalizeLimitPreservesZero() {
        assertEquals(0, ParameterValidationUtils.normalizeLimit(BigInteger.ZERO));
    }

    @Test
    void normalizeLimitCapsAboveOneThousand() {
        assertEquals(1000, ParameterValidationUtils.normalizeLimit(BigInteger.valueOf(5000)));
    }

    @Test
    void normalizeLimitUnboundedNormalizesNegativeToOne() {
        assertEquals(1, ParameterValidationUtils.normalizeLimitUnbounded(BigInteger.valueOf(-1)));
    }

    @Test
    void normalizeLimitUnboundedPreservesZero() {
        assertEquals(0, ParameterValidationUtils.normalizeLimitUnbounded(BigInteger.ZERO));
    }

    @Test
    void normalizeLimitUnboundedDoesNotCapAboveOneThousand() {
        assertEquals(5000, ParameterValidationUtils.normalizeLimitUnbounded(BigInteger.valueOf(5000)));
    }

    @Test
    void normalizeLimitUnboundedClampsOverflowToMaxInt() {
        BigInteger overflow = BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE);
        assertEquals(Integer.MAX_VALUE, ParameterValidationUtils.normalizeLimitUnbounded(overflow));
    }

}
