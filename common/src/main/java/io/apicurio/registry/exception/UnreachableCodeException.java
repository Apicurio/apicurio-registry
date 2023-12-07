package io.apicurio.registry.exception;

public class UnreachableCodeException extends RuntimeAssertionFailedException {

    private static final String PREFIX = "Unreachable code";


    public UnreachableCodeException() {
        super(PREFIX);
    }


    public UnreachableCodeException(String message) {
        super(PREFIX + ": " + message);
    }


    public UnreachableCodeException(Throwable cause) {
        super(PREFIX + ": Unexpected exception", cause);
    }


    public UnreachableCodeException(String message, Throwable cause) {
        super(PREFIX + ": " + message, cause);
    }
}
