package io.apicurio.registry.agents.inventory;

import jakarta.ws.rs.WebApplicationException;

/**
 * An inventory source that could not be read. Messages are fixed sentences: they never include a
 * URL, header, or text sent by the source, so a failed sync does not reveal what the registry can
 * reach or what it received. It never carries a cause, because the REST error mapper reports the
 * root cause's message to the client.
 * <p>
 * It is a {@link WebApplicationException}, whose status the core error mapper uses as is: 502, or
 * 400 when the source refused the request's credentials or scope.
 */
public class InventorySourceException extends WebApplicationException {

    private static final long serialVersionUID = 6208144271543869035L;

    public InventorySourceException(String message) {
        super(message, 502);
    }

    public InventorySourceException(String message, int status) {
        super(message, status);
    }
}
