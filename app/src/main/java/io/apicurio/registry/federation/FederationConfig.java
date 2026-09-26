package io.apicurio.registry.federation;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_FEDERATION;

/**
 * Configuration properties for federated agent search across peer registries.
 */
@Singleton
public class FederationConfig {

    @ConfigProperty(name = "apicurio.federation.enabled", defaultValue = "false")
    @Info(category = CATEGORY_FEDERATION, description = "Enable federation with peer registries, including the peer management API", availableSince = "3.4.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.federation.peer.insecure-http.enabled", defaultValue = "false")
    @Info(category = CATEGORY_FEDERATION, description = "Allow peer registry URLs that use plain HTTP instead of HTTPS", availableSince = "3.4.0")
    boolean insecureHttpEnabled;

    @ConfigProperty(name = "apicurio.federation.peer.loopback.enabled", defaultValue = "false")
    @Info(category = CATEGORY_FEDERATION, description = "Allow peer registry URLs that point at a loopback address or a localhost name, for development and tests", availableSince = "3.4.0")
    boolean loopbackEnabled;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isInsecureHttpEnabled() {
        return insecureHttpEnabled;
    }

    public boolean isLoopbackEnabled() {
        return loopbackEnabled;
    }
}
