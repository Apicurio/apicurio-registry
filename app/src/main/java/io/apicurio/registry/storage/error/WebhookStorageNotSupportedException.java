package io.apicurio.registry.storage.error;

import io.apicurio.registry.rest.ConflictException;

/**
 * Thrown by every storage variant other than SQL when a webhook subscription operation is attempted.
 */
public class WebhookStorageNotSupportedException extends ConflictException {

    private static final long serialVersionUID = 6820193367354017112L;

    public WebhookStorageNotSupportedException() {
        super("Webhook management requires SQL storage.");
    }
}
