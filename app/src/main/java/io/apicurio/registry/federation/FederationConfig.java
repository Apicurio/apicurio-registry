package io.apicurio.registry.federation;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_A2A;

/**
 * Configuration for federated agent discovery.
 *
 * <p>SPIKE: peers are supplied as a static list of base URLs. A real implementation stores them
 * through the storage layer so they can be managed at runtime and survive across all storage
 * variants.
 */
@Singleton
public class FederationConfig {

    @ConfigProperty(name = "apicurio.federation.enabled", defaultValue = "false")
    @Info(category = CATEGORY_A2A, description = "Enable federated agent discovery across peer registries", availableSince = "3.3.2", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.federation.peers")
    @Info(category = CATEGORY_A2A, description = "Comma separated base URLs of peer registries to federate agent searches across", availableSince = "3.3.2")
    Optional<List<String>> peers;

    @ConfigProperty(name = "apicurio.federation.timeout.ms", defaultValue = "2000")
    @Info(category = CATEGORY_A2A, description = "Milliseconds to wait for peer registries before returning partial results", availableSince = "3.3.2")
    long timeoutMs;

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getPeers() {
        return peers.orElse(Collections.emptyList());
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }
}
