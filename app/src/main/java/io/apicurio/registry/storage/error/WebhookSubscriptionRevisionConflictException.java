package io.apicurio.registry.storage.error;

import io.apicurio.registry.rest.ConflictException;

/**
 * A conditional update or delete was rejected because the subscription's current revision does not
 * match the revision supplied by the caller.
 */
public class WebhookSubscriptionRevisionConflictException extends ConflictException {

    private static final long serialVersionUID = -3047713518862390514L;

    public WebhookSubscriptionRevisionConflictException() {
        super("The webhook subscription was modified concurrently. Fetch the current revision and retry.");
    }
}
