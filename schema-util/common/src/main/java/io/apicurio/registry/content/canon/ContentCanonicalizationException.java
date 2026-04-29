package io.apicurio.registry.content.canon;

import io.apicurio.registry.types.RegistryException;

/**
 * Exception thrown when an error occurs during content canonicalization.
 * This exception provides consistent error handling across all ContentCanonicalizer implementations.
 */
public class ContentCanonicalizationException extends RegistryException {

    private static final long serialVersionUID = 1L;

    public ContentCanonicalizationException() {
    }

    public ContentCanonicalizationException(String reason) {
        super(reason);
    }

    public ContentCanonicalizationException(Throwable cause) {
        super(cause);
    }

    public ContentCanonicalizationException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
