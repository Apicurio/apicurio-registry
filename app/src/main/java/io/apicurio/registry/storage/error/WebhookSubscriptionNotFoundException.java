package io.apicurio.registry.storage.error;

public class WebhookSubscriptionNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    public WebhookSubscriptionNotFoundException(String subscriptionId) {
        super("No webhook subscription '" + subscriptionId + "' was found.");
    }

    public WebhookSubscriptionNotFoundException(String subscriptionId, Throwable cause) {
        super("No webhook subscription '" + subscriptionId + "' was found.", cause);
    }
}