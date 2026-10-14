package io.apicurio.registry.rest;

import io.apicurio.registry.types.RegistryException;

/**
 * A webhook subscription request body exceeded the size limit.
 */
public class WebhookRequestTooLargeException extends RegistryException {

    private static final long serialVersionUID = 4471852091634558217L;

    public WebhookRequestTooLargeException(int maxBytes) {
        super("body: must be at most " + maxBytes + " bytes");
    }
}
