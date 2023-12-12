package io.apicurio.registry.exception;

public class RuntimeAssertionFailedException extends RuntimeException {

    private static final String PREFIX = "Runtime assertion failed: ";

    public RuntimeAssertionFailedException(String message) {
        super(PREFIX + message);
    }

    public RuntimeAssertionFailedException(String message, Throwable cause) {
        super(PREFIX + message, cause);
    }
}
