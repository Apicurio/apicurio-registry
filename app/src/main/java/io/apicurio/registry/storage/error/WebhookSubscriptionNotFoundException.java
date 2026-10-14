package io.apicurio.registry.storage.error;

/**
 * The webhook subscription does not exist or has been soft-deleted. The message deliberately does not
 * echo the requested ID.
 */
public class WebhookSubscriptionNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 5412268316209784530L;

    public WebhookSubscriptionNotFoundException() {
        super("No webhook subscription with this ID.");
    }
}
