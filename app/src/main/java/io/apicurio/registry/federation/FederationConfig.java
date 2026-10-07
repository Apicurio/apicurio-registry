package io.apicurio.registry.federation;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.rest.ConflictException;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_FEDERATION;

/**
 * Configuration properties for federated agent search across peer registries.
 */
@Singleton
public class FederationConfig {

    private static final String NOT_ENABLED = "Federation is not enabled on this registry instance.";

    @ConfigProperty(name = "apicurio.federation.enabled", defaultValue = "false")
    @Info(category = CATEGORY_FEDERATION, description = "Enable federation with peer registries, including the peer management API", availableSince = "3.4.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.federation.peer.insecure-http.enabled", defaultValue = "false")
    @Info(category = CATEGORY_FEDERATION, description = "Allow peer registry URLs that use plain HTTP instead of HTTPS", availableSince = "3.4.0")
    boolean insecureHttpEnabled;

    @ConfigProperty(name = "apicurio.federation.peer.loopback.enabled", defaultValue = "false")
    @Info(category = CATEGORY_FEDERATION, description = "Allow peer registry URLs that point at a loopback address or a localhost name, for development and tests", availableSince = "3.4.0")
    boolean loopbackEnabled;

    @ConfigProperty(name = "apicurio.federation.search.deadline-ms", defaultValue = "5000")
    @Info(category = CATEGORY_FEDERATION, description = "Time in milliseconds a federated search may take in total. Peers that have not answered by then are reported as deadline_exceeded", availableSince = "3.4.0")
    long searchDeadlineMs;

    @ConfigProperty(name = "apicurio.federation.search.peer-timeout-ms", defaultValue = "3000")
    @Info(category = CATEGORY_FEDERATION, description = "Time in milliseconds a single peer registry has to answer a federated search", availableSince = "3.4.0")
    long searchPeerTimeoutMs;

    @ConfigProperty(name = "apicurio.federation.search.max-peers", defaultValue = "16")
    @Info(category = CATEGORY_FEDERATION, description = "Maximum number of peer registries that can be enabled, and so queried by one federated search", availableSince = "3.4.0")
    int searchMaxPeers;

    @ConfigProperty(name = "apicurio.federation.search.max-concurrent-calls", defaultValue = "64")
    @Info(category = CATEGORY_FEDERATION, description = "Maximum number of peer registry calls being set up at once, across all federated searches. Calls beyond it are reported as capacity_exceeded", availableSince = "3.4.0")
    int searchMaxConcurrentCalls;

    @ConfigProperty(name = "apicurio.federation.search.max-response-bytes", defaultValue = "1048576")
    @Info(category = CATEGORY_FEDERATION, description = "Maximum size in bytes of the response accepted from one peer registry. Larger responses are discarded", availableSince = "3.4.0")
    int searchMaxResponseBytes;

    @ConfigProperty(name = "apicurio.federation.search.per-source-limit.max", defaultValue = "100")
    @Info(category = CATEGORY_FEDERATION, description = "Upper bound for the perSourceLimit a federated search may ask for", availableSince = "3.4.0")
    int searchPerSourceLimitMax;

    @ConfigProperty(name = "apicurio.federation.search.breaker.request-volume-threshold", defaultValue = "4")
    @Info(category = CATEGORY_FEDERATION, description = "Number of recent calls to a peer registry that must have been made before its circuit breaker can open", availableSince = "3.4.0")
    int breakerRequestVolumeThreshold;

    @ConfigProperty(name = "apicurio.federation.search.breaker.failure-ratio", defaultValue = "0.5")
    @Info(category = CATEGORY_FEDERATION, description = "Share of recent calls to a peer registry that must have failed for its circuit breaker to open", availableSince = "3.4.0")
    double breakerFailureRatio;

    @ConfigProperty(name = "apicurio.federation.search.breaker.delay-ms", defaultValue = "30000")
    @Info(category = CATEGORY_FEDERATION, description = "Time in milliseconds an open circuit breaker waits before letting a call through to a peer registry again", availableSince = "3.4.0")
    long breakerDelayMs;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @throws ConflictException if federation is not enabled
     */
    public void requireEnabled() {
        if (!enabled) {
            throw new ConflictException(NOT_ENABLED);
        }
    }

    public boolean isInsecureHttpEnabled() {
        return insecureHttpEnabled;
    }

    public boolean isLoopbackEnabled() {
        return loopbackEnabled;
    }

    public long getSearchDeadlineMs() {
        return searchDeadlineMs;
    }

    public long getSearchPeerTimeoutMs() {
        return searchPeerTimeoutMs;
    }

    public int getSearchMaxPeers() {
        return searchMaxPeers;
    }

    public int getSearchMaxConcurrentCalls() {
        return searchMaxConcurrentCalls;
    }

    public int getSearchMaxResponseBytes() {
        return searchMaxResponseBytes;
    }

    public int getSearchPerSourceLimitMax() {
        return searchPerSourceLimitMax;
    }

    public int getBreakerRequestVolumeThreshold() {
        return breakerRequestVolumeThreshold;
    }

    public double getBreakerFailureRatio() {
        return breakerFailureRatio;
    }

    public long getBreakerDelayMs() {
        return breakerDelayMs;
    }
}
