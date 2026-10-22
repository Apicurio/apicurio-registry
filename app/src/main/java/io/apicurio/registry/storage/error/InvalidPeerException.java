package io.apicurio.registry.storage.error;

import io.apicurio.registry.types.RegistryException;

/**
 * Exception thrown when a peer registry definition is invalid.
 */
public class InvalidPeerException extends RegistryException {

    private static final long serialVersionUID = 1L;

    public InvalidPeerException(String message) {
        super(message);
    }

    public InvalidPeerException(String message, Throwable cause) {
        super(message, cause);
    }
}
