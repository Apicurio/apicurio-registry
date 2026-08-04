package io.apicurio.registry.http;

/**
 * Thrown when an HTTP request is interrupted. Extends {@link RuntimeException} directly
 * (rather than {@link HttpClientException}) to stay within the project's class-hierarchy
 * depth limit. {@code @Retry(abortOn = HttpClientInterruptException.class)} documents intent
 * for readers and callers, but synchronous retry termination does not depend on it in practice:
 * SmallRye Fault Tolerance already treats interruption as non-retryable before
 * {@code abortOn}/{@code retryOn} is evaluated.
 */
public class HttpClientInterruptException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected HttpClientInterruptException(Throwable cause) {
        super(cause);
    }
}
