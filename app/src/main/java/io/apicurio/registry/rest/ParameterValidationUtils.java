package io.apicurio.registry.rest;

import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.RuleType;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Utility class providing common parameter validation methods for REST API resources.
 * Centralizes validation logic that was previously duplicated across multiple resource implementations.
 */
public final class ParameterValidationUtils {

    private static final BigInteger MAX_INT_VALUE = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger MAX_LIMIT = BigInteger.valueOf(1000);
    private static final String CONFIG_PARAM = "config";

    private ParameterValidationUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates that a required parameter is not null.
     *
     * @param parameterName the name of the parameter (for error messages)
     * @param parameterValue the value to validate
     * @throws MissingRequiredParameterException if the parameter value is null
     */
    public static void requireParameter(String parameterName, Object parameterValue) {
        if (parameterValue == null) {
            throw new MissingRequiredParameterException(parameterName);
        }
    }

    /**
     * Validates that at most one of two mutually exclusive parameters is provided.
     *
     * @param parameterOneName the name of the first parameter
     * @param parameterOneValue the value of the first parameter
     * @param parameterTwoName the name of the second parameter
     * @param parameterTwoValue the value of the second parameter
     * @throws ParametersConflictException if both parameters are non-null
     */
    public static void maxOneOf(String parameterOneName, Object parameterOneValue,
            String parameterTwoName, Object parameterTwoValue) {
        if (parameterOneValue != null && parameterTwoValue != null) {
            throw new ParametersConflictException(parameterOneName, parameterTwoName);
        }
    }

    /**
     * Validates that if a parameter is provided, it matches an expected value.
     *
     * @param parameterName the name of the parameter
     * @param expectedValue the expected value
     * @param actualValue the actual value provided
     * @throws InvalidParameterValueException if both values are non-null and don't match
     */
    public static void requireParameterValue(String parameterName, String expectedValue, String actualValue) {
        if (actualValue != null && expectedValue != null && !actualValue.equals(expectedValue)) {
            throw new InvalidParameterValueException(parameterName, expectedValue, actualValue);
        }
    }

    /**
     * Validates that the configuration for a rule is valid for the specified rule type.
     *
     * @param ruleType the type of rule
     * @param config the rule configuration to validate
     * @throws MissingRequiredParameterException if config is null or empty, or ruleType is null
     * @throws InvalidParameterValueException if the configuration is invalid for the rule type
     * @throws IllegalArgumentException if the rule type is not supported
     */
    public static void validateRuleConfig(RuleType ruleType, String config) {
        if (config == null || config.trim().isEmpty()) {
            throw new MissingRequiredParameterException(CONFIG_PARAM);
        }
        if (ruleType == null) {
            throw new MissingRequiredParameterException("ruleType");
        }
        switch (ruleType) {
            case VALIDITY -> validateEnumValue(ValidityLevel.class, config, config);
            case COMPATIBILITY -> validateEnumValue(CompatibilityLevel.class, config, config);
            case INTEGRITY -> validateIntegrityConfig(config);
            default -> throw new IllegalArgumentException("Unsupported rule type: " + ruleType);
        }
    }

    private static <E extends Enum<E>> void validateEnumValue(Class<E> enumClass, String value, String fullConfig) {
        try {
            Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterValueException(
                    CONFIG_PARAM, Arrays.toString(enumClass.getEnumConstants()), fullConfig);
        }
    }

    private static void validateIntegrityConfig(String config) {
        for (String cvalue : config.split(",", -1)) {
            String trimmed = cvalue.trim();
            if (trimmed.isEmpty()) {
                throw new InvalidParameterValueException(
                        CONFIG_PARAM, Arrays.toString(IntegrityLevel.values()), config);
            }
            validateEnumValue(IntegrityLevel.class, trimmed, config);
        }
    }

    /**
     * Normalizes a pagination offset so it never reaches the storage layer as an invalid value.
     * Clamps the offset to the range [0, Integer.MAX_VALUE].
     *
     * @param offset the raw offset value provided by the client
     * @return the normalized offset, safe to pass to the storage layer
     */
    public static int normalizeOffset(BigInteger offset) {
        return offset.max(BigInteger.ZERO).min(MAX_INT_VALUE).intValue();
    }

    /**
     * Normalizes a pagination limit so it never reaches the storage layer as an invalid value.
     * A negative limit is normalized to 1 (limit=0 keeps its existing empty-page semantics);
     * otherwise the limit is capped to bound the size of the result set.
     *
     * @param limit the raw limit value provided by the client; must not be {@code null}
     * @return the normalized limit, safe to pass to the storage layer
     */
    public static int normalizeLimit(BigInteger limit) {
        return limit.signum() < 0 ? 1 : limit.min(MAX_LIMIT).intValue();
    }

    /**
     * Normalizes a pagination limit so it never reaches the storage layer as an invalid value,
     * without imposing an upper bound on the result set size. A negative limit is normalized to 1
     * (limit=0 keeps its existing empty-page semantics); otherwise the limit is clamped to
     * Integer.MAX_VALUE to prevent BigInteger-to-int overflow.
     *
     * @param limit the raw limit value provided by the client; must not be {@code null}
     * @return the normalized limit, safe to pass to the storage layer
     */
    public static int normalizeLimitUnbounded(BigInteger limit) {
        return limit.signum() < 0 ? 1 : limit.min(MAX_INT_VALUE).intValue();
    }

}
