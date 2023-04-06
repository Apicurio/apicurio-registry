package io.apicurio.registry.exception;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public class RuntimeAssertionFailedException extends RuntimeException {

    public RuntimeAssertionFailedException(String message) {
        super("Runtime assertion failed: " + message);
    }
}
