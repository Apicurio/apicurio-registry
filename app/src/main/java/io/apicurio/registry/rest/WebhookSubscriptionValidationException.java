package io.apicurio.registry.rest;

import io.apicurio.registry.types.RegistryException;

public class WebhookSubscriptionValidationException extends RegistryException {

    private static final long serialVersionUID = 2286054217466340551L;

    public WebhookSubscriptionValidationException(String message) {
        super(message);
    }
}
