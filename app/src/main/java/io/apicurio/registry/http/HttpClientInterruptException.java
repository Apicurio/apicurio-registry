package io.apicurio.registry.http;

/**
 * Thrown when an HTTP request is interrupted. Extends {@link RuntimeException} directly
 * (rather than {@link HttpClientException}) to stay within the project's class-hierarchy
 * depth limit. Used with {@code @Retry(abortOn)} so that SmallRye Fault Tolerance aborts
 * immediately on interrupts rather than cycling through all retry attempts.
 */
public class HttpClientInterruptException extends RuntimeException {

    private static final long serialVersionUID = 0;

    protected HttpClientInterruptException(Throwable cause) {
        super(cause);
    }
}
