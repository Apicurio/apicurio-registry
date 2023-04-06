package io.apicurio.registry.exception;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public class UnreachableCodeException extends RuntimeAssertionFailedException {

    public UnreachableCodeException() {
        super("Unreachable code");
    }
}
