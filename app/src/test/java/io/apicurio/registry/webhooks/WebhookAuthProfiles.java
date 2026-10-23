package io.apicurio.registry.webhooks;

import java.util.HashMap;
import java.util.Map;

/**
 * Basic authentication with embedded users for the webhook auth-configuration test profiles.
 * {@code alice} holds the admin role, {@code bob1} the developer role.
 */
final class WebhookAuthProfiles {

    private WebhookAuthProfiles() {
    }

    static Map<String, String> basicAuth(Map<String, String> authorization) {
        Map<String, String> config = new HashMap<>();
        config.put("quarkus.oidc.tenant-enabled", "false");
        config.put("quarkus.http.auth.basic", "true");
        config.put("quarkus.security.users.embedded.enabled", "true");
        config.put("quarkus.security.users.embedded.plain-text", "true");
        config.put("quarkus.security.users.embedded.users.alice", "alice");
        config.put("quarkus.security.users.embedded.users.bob1", "bob1");
        config.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
        config.put("quarkus.security.users.embedded.roles.bob1", "sr-developer");
        config.putAll(authorization);
        return config;
    }
}
