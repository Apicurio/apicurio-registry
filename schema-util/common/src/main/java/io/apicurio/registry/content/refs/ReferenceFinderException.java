package io.apicurio.registry.content.refs;

import io.apicurio.registry.types.RegistryException;

/**
 * Exception thrown when an error occurs while finding external references in content.
 * This exception provides consistent error handling across all ReferenceFinder implementations.
 */
public class ReferenceFinderException extends RegistryException {

    private static final long serialVersionUID = 1L;

    public ReferenceFinderException() {
    }

    public ReferenceFinderException(String reason) {
        super(reason);
    }

    public ReferenceFinderException(Throwable cause) {
        super(cause);
    }

    public ReferenceFinderException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
