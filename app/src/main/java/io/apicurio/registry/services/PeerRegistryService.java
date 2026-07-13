package io.apicurio.registry.services;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

/**
 * Temporary stub for Peer Registry Management (Issue #8574 is not yet implemented).
 * Provides a list of enabled peer registries (base URLs) to query for A2A federated search.
 */
@ApplicationScoped
public class PeerRegistryService {

    @ConfigProperty(name = "registry.peers.enabled-list", defaultValue = "")
    Optional<List<String>> enabledPeers;

    public List<String> getEnabledPeers() {
        if (enabledPeers == null || enabledPeers.isEmpty() || enabledPeers.get().isEmpty()) {
            return List.of();
        }
        return enabledPeers.get().stream()
                .filter(p -> p != null && !p.trim().isEmpty())
                .toList();
    }
}
