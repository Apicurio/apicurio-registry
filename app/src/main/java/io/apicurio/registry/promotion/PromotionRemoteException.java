package io.apicurio.registry.promotion;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Raised when a remote source registry cannot be reached or returns an unexpected error.
 */
public class PromotionRemoteException extends WebApplicationException {

    public PromotionRemoteException(String message) {
        super(message, Response.Status.BAD_GATEWAY);
    }

    public PromotionRemoteException(String message, Throwable cause) {
        super(message, cause, Response.Status.BAD_GATEWAY);
    }
}
