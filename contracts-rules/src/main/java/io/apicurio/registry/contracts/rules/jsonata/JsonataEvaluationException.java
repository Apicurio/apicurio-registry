package io.apicurio.registry.contracts.rules.jsonata;

public class JsonataEvaluationException extends RuntimeException {
    public JsonataEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
