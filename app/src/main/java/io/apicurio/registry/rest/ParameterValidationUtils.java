package io.apicurio.registry.rest;

/**
 * Utility class providing common parameter validation methods for REST API resources.
 * Centralizes validation logic that was previously duplicated across multiple resource implementations.
 */
public final class ParameterValidationUtils {

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
            throw new InvalidParameterValueException(parameterName, actualValue, expectedValue);
        }
    }

}
