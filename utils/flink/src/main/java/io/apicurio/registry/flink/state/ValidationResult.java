package io.apicurio.registry.flink.state;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result of savepoint validation.
 */
public final class ValidationResult {

    /** Whether the validation passed. */
    private final boolean valid;

    /** Overall message. */
    private final String message;

    /** Compatible operators. */
    private final List<String> compatibleOperators;

    /** Incompatible operators with reasons. */
    private final Map<String, String> incompatibleOperators;

    private ValidationResult(
            final boolean isValid,
            final String resultMessage,
            final List<String> compatible,
            final Map<String, String> incompatible) {
        this.valid = isValid;
        this.message = resultMessage;
        this.compatibleOperators = compatible != null
                ? Collections.unmodifiableList(compatible)
                : Collections.emptyList();
        this.incompatibleOperators = incompatible != null
                ? Collections.unmodifiableMap(incompatible)
                : Collections.emptyMap();
    }

    /**
     * Creates a valid result.
     *
     * @param operators the compatible operators
     * @return valid result
     */
    public static ValidationResult valid(final List<String> operators) {
        return new ValidationResult(
                true,
                "All operators are compatible",
                operators,
                null);
    }

    /**
     * Creates an invalid result.
     *
     * @param compatible   compatible operators
     * @param incompatible incompatible operators with reasons
     * @return invalid result
     */
    public static ValidationResult invalid(
            final List<String> compatible,
            final Map<String, String> incompatible) {
        return new ValidationResult(
                false,
                incompatible.size() + " operator(s) incompatible",
                compatible,
                incompatible);
    }

    /**
     * Checks if validation passed.
     *
     * @return true if valid
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Gets the message.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the compatible operators.
     *
     * @return the list
     */
    public List<String> getCompatibleOperators() {
        return compatibleOperators;
    }

    /**
     * Gets the incompatible operators.
     *
     * @return map of operator to reason
     */
    public Map<String, String> getIncompatibleOperators() {
        return incompatibleOperators;
    }

    @Override
    public String toString() {
        return "ValidationResult{"
                + "valid=" + valid
                + ", message='" + message + '\''
                + ", compatibleOperators=" + compatibleOperators
                + ", incompatibleOperators=" + incompatibleOperators
                + '}';
    }
}
