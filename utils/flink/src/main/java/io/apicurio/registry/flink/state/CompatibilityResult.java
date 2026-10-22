package io.apicurio.registry.flink.state;

import java.util.Collections;
import java.util.List;

/**
 * Result of schema compatibility check.
 */
public final class CompatibilityResult {

    /**
     * Compatibility status.
     */
    public enum Status {
        /** Schemas are compatible. */
        COMPATIBLE,
        /** Schemas are incompatible. */
        INCOMPATIBLE,
        /** Schemas can be made compatible with migration. */
        NEEDS_MIGRATION
    }

    /** The compatibility status. */
    private final Status status;

    /** Human-readable message. */
    private final String message;

    /** Field-level differences. */
    private final List<String> differences;

    private CompatibilityResult(
            final Status resultStatus,
            final String resultMessage,
            final List<String> diffs) {
        this.status = resultStatus;
        this.message = resultMessage;
        this.differences = diffs != null
                ? Collections.unmodifiableList(diffs)
                : Collections.emptyList();
    }

    /**
     * Creates a compatible result.
     *
     * @return compatible result
     */
    public static CompatibilityResult compatible() {
        return new CompatibilityResult(
                Status.COMPATIBLE,
                "Schemas are compatible",
                null);
    }

    /**
     * Creates an incompatible result.
     *
     * @param message the reason
     * @param diffs   field differences
     * @return incompatible result
     */
    public static CompatibilityResult incompatible(
            final String message,
            final List<String> diffs) {
        return new CompatibilityResult(
                Status.INCOMPATIBLE,
                message,
                diffs);
    }

    /**
     * Creates a needs-migration result.
     *
     * @param message the reason
     * @param diffs   field differences
     * @return needs-migration result
     */
    public static CompatibilityResult needsMigration(
            final String message,
            final List<String> diffs) {
        return new CompatibilityResult(
                Status.NEEDS_MIGRATION,
                message,
                diffs);
    }

    /**
     * Checks if schemas are compatible.
     *
     * @return true if compatible
     */
    public boolean isCompatible() {
        return status == Status.COMPATIBLE;
    }

    /**
     * Gets the status.
     *
     * @return the status
     */
    public Status getStatus() {
        return status;
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
     * Gets the field differences.
     *
     * @return the differences
     */
    public List<String> getDifferences() {
        return differences;
    }

    @Override
    public String toString() {
        return "CompatibilityResult{"
                + "status=" + status
                + ", message='" + message + '\''
                + ", differences=" + differences
                + '}';
    }
}
