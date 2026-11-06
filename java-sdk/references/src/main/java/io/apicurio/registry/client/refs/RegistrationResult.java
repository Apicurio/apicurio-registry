package io.apicurio.registry.client.refs;

import io.apicurio.registry.content.refs.resolve.PlannedArtifact;
import io.apicurio.registry.rest.client.models.VersionMetaData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The outcome of executing a registration plan.
 */
public final class RegistrationResult {

    private final Map<PlannedArtifact, VersionMetaData> registered;
    private final boolean dryRun;

    RegistrationResult(Map<PlannedArtifact, VersionMetaData> registered, boolean dryRun) {
        this.registered = Collections.unmodifiableMap(new LinkedHashMap<>(registered));
        this.dryRun = dryRun;
    }

    /**
     * Returns every registered artifact with its version metadata, in plan order.
     *
     * @return an unmodifiable map
     */
    public Map<PlannedArtifact, VersionMetaData> registered() {
        return registered;
    }

    /**
     * Returns the version metadata of the root artifacts, in plan order.
     *
     * @return the root results
     */
    public List<VersionMetaData> roots() {
        return registered.entrySet().stream().filter(e -> e.getKey().isRoot()).map(Map.Entry::getValue)
                .toList();
    }

    /**
     * Indicates whether this was a dry run.
     *
     * @return true when nothing was persisted
     */
    public boolean isDryRun() {
        return dryRun;
    }
}
