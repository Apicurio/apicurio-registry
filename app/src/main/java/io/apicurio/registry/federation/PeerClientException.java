package io.apicurio.registry.federation;

/**
 * Raised when a peer registry cannot be queried. Callers convert this into a non-OK
 * {@link PeerSearchOutcome} rather than propagating it, so one unreachable peer degrades the
 * result instead of failing the request.
 */
public class PeerClientException extends Exception {

    private static final long serialVersionUID = 1L;

    public PeerClientException(String message) {
        super(message);
    }

    public PeerClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
