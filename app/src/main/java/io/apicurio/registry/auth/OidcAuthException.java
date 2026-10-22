package io.apicurio.registry.auth;

/**
 * Exception thrown when OIDC authentication fails (e.g., token endpoint errors).
 * Used as the retry target for {@link org.eclipse.microprofile.faulttolerance.Retry}.
 */
public class OidcAuthException extends RuntimeException {

    public OidcAuthException(String message) {
        super(message);
    }

    public OidcAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
