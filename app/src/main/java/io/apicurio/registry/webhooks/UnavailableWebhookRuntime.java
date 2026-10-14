package io.apicurio.registry.webhooks;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * The runtime used until webhook delivery is implemented (#8569): nothing is delivered, so no subscription
 * can be enabled. The delivery engine replaces this bean.
 */
@ApplicationScoped
public class UnavailableWebhookRuntime implements WebhookRuntime {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean signingKeyResolves(String keyName) {
        return false;
    }
}
