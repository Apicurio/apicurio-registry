package io.apicurio.registry.rest;

import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.RuleType;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void validateRuleConfigValidityValid() {
        for (ValidityLevel level : ValidityLevel.values()) {
            assertDoesNotThrow(() -> ParameterValidationUtils.validateRuleConfig(RuleType.VALIDITY, level.name()));
        }
    }

    @Test
    void validateRuleConfigValidityInvalid() {
        assertThrows(InvalidParameterValueException.class,
                () -> ParameterValidationUtils.validateRuleConfig(RuleType.VALIDITY, "NOT_A_VALID_LEVEL"));
    }

    @Test
    void validateRuleConfigCompatibilityValid() {
        for (CompatibilityLevel level : CompatibilityLevel.values()) {
            assertDoesNotThrow(() -> ParameterValidationUtils.validateRuleConfig(RuleType.COMPATIBILITY, level.name()));
        }
    }

    @Test
    void validateRuleConfigCompatibilityInvalid() {
        assertThrows(InvalidParameterValueException.class,
                () -> ParameterValidationUtils.validateRuleConfig(RuleType.COMPATIBILITY, "INVALID"));
    }

    @Test
    void validateRuleConfigIntegrityValidSingle() {
        for (IntegrityLevel level : IntegrityLevel.values()) {
            assertDoesNotThrow(() -> ParameterValidationUtils.validateRuleConfig(RuleType.INTEGRITY, level.name()));
        }
    }

    @Test
    void validateRuleConfigIntegrityValidMultiple() {
        assertDoesNotThrow(() -> ParameterValidationUtils.validateRuleConfig(RuleType.INTEGRITY, "REFS_EXIST, NO_DUPLICATES"));
        assertDoesNotThrow(() -> ParameterValidationUtils.validateRuleConfig(RuleType.INTEGRITY, "ALL_REFS_MAPPED,NO_CIRCULAR_REFERENCES"));
    }

    @Test
    void validateRuleConfigIntegrityInvalid() {
        assertThrows(InvalidParameterValueException.class,
                () -> ParameterValidationUtils.validateRuleConfig(RuleType.INTEGRITY, "INVALID"));
        assertThrows(InvalidParameterValueException.class,
                () -> ParameterValidationUtils.validateRuleConfig(RuleType.INTEGRITY, "REFS_EXIST, INVALID"));
    }

    @Test
    void validateRuleConfigIntegrityMalformedList() {
        assertThrows(InvalidParameterValueException.class,
                () -> ParameterValidationUtils.validateRuleConfig(RuleType.INTEGRITY, "REFS_EXIST,"));
        assertThrows(InvalidParameterValueException.class,
                () -> ParameterValidationUtils.validateRuleConfig(RuleType.INTEGRITY, ",REFS_EXIST"));
        assertThrows(InvalidParameterValueException.class,
                () -> ParameterValidationUtils.validateRuleConfig(RuleType.INTEGRITY, ","));
    }

    @Test
    void validateRuleConfigNullOrBlankConfigThrowsMissingRequiredParameter() {
        assertThrows(MissingRequiredParameterException.class,
                () -> ParameterValidationUtils.validateRuleConfig(RuleType.VALIDITY, null));
        assertThrows(MissingRequiredParameterException.class,
                () -> ParameterValidationUtils.validateRuleConfig(RuleType.VALIDITY, ""));
        assertThrows(MissingRequiredParameterException.class,
                () -> ParameterValidationUtils.validateRuleConfig(RuleType.VALIDITY, "   "));
    }

    @Test
    void validateRuleConfigNullRuleTypeThrowsMissingRequiredParameter() {
        assertThrows(MissingRequiredParameterException.class,
                () -> ParameterValidationUtils.validateRuleConfig(null, "FULL"));
    }

}
