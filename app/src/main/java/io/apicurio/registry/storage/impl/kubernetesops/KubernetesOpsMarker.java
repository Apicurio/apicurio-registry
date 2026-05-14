package io.apicurio.registry.storage.impl.kubernetesops;

import io.apicurio.registry.storage.impl.polling.SourceMarker;

import java.time.Instant;
import java.util.Map;

/**
 * Marker tracking the state of Kubernetes ConfigMap data sources.
 * Wraps the Kubernetes list resourceVersion, the source namespace, and
 * the most recent ConfigMap timestamp.
 */
class KubernetesOpsMarker implements SourceMarker {

    private final String sourceId;
    private final String resourceVersion;
    private final Instant timestamp;

    KubernetesOpsMarker(String sourceId, String resourceVersion, Instant timestamp) {
        this.sourceId = sourceId;
        this.resourceVersion = resourceVersion;
        this.timestamp = timestamp;
    }

    String getResourceVersion() {
        return resourceVersion;
    }

    @Override
    public Map<String, String> toSources() {
        return Map.of(sourceId, resourceVersion);
    }

    @Override
    public Instant getCommitTime() {
        return timestamp;
    }

    @Override
    public String toString() {
        return toDisplayString();
    }
}
