package io.apicurio.registry.operator;

public class OperatorException extends RuntimeException {

    public OperatorException(String message, Throwable cause) {
        super(message, cause);
    }

    public OperatorException(String message) {
        super(message);
    }
}
