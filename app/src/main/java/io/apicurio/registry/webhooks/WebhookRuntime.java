package io.apicurio.registry.webhooks;

/**
 * What the webhook delivery runtime can do right now. Subscription management asks this before letting a
 * subscription be enabled, so the API never accepts an enabled subscription that nothing will deliver.
 */
public interface WebhookRuntime {

    /**
     * Whether webhook deliveries are actually sent by this registry.
     */
    boolean isAvailable();

    /**
     * Whether a signing key with this name is configured.
     */
    boolean signingKeyResolves(String keyName);
}
