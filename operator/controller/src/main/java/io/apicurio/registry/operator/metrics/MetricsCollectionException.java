package io.apicurio.registry.operator.metrics;

/**
 * Raised when the operator cannot collect metrics from the operand.
 * <p>
 * This is deliberately not an {@code OperatorException}. A registry that is unreachable, still starting, or
 * serving its management interface over TLS is not an operator error, and it must not fail reconciliation.
 */
public class MetricsCollectionException extends Exception {

    public MetricsCollectionException(String message) {
        super(message);
    }

    public MetricsCollectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
