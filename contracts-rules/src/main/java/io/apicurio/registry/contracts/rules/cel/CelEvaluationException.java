package io.apicurio.registry.contracts.rules.cel;

public class CelEvaluationException extends RuntimeException {
    public CelEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
