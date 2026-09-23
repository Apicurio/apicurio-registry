package io.apicurio.registry.storage.impl.polling;

import java.time.Duration;
import java.util.List;

/**
 * Configuration interface shared by all polling-based storage implementations.
 * Each implementation (GitOps, KubernetesOps, etc.) provides its own config properties
 * that map to this interface.
 */
public interface PollingStorageConfig {

    /**
     * Returns the name of this storage implementation (e.g., "gitops", "kubernetesops").
     */
    String getStorageName();

    /**
     * Returns the registry ID used to filter data belonging to this instance.
     */
    String getRegistryId();

    /**
     * Returns whether deterministic ID generation is enabled.
     * When enabled, contentId and globalId are derived from content/coordinate hashes
     * if not explicitly specified. When disabled, missing IDs cause an error.
     */
    boolean isDeterministicIdGenerationEnabled();

    Duration getPollPeriod();

    /**
     * Returns the debounce quiet period. After detecting a change, the system waits until
     * no new changes are seen for this duration before loading data.
     * Set to Duration.ZERO to disable debouncing.
     */
    Duration getDebounceQuietPeriod();

    /**
     * Returns the debounce max wait time. If changes keep arriving, loading is forced
     * after this duration. Set to Duration.ZERO for no maximum.
     */
    Duration getDebounceMaxWaitPeriod();

    /**
     * Returns whether a registry configuration file matching the registry ID is required.
     * When true, loads are rejected if no matching registry config is found,
     * protecting against accidental data loss.
     */
    boolean isRequireRegistryConfig();

    /**
     * Returns the list of file suffixes that identify registry metadata files.
     * Each suffix is combined with supported extensions (.yaml, .yml, .json) to form
     * the full pattern. For example, suffix "registry" matches "*.registry.yaml",
     * "*.registry.yml", and "*.registry.json".
     */
    List<String> getFileSuffixes();

    /**
     * Returns the label key to use for tagging artifacts with their source ID (e.g., repo ID).
     * Empty string means disabled.
     */
    String getSourceLabelKey();

    /**
     * Checks if a file path matches the registry metadata file convention
     * based on the configured suffixes.
     */
    default boolean isMetadataFile(String path) {
        if (path == null) {
            return false;
        }
        String lowerPath = path.toLowerCase();
        for (String suffix : getFileSuffixes()) {
            String lowerSuffix = "." + suffix.toLowerCase();
            if (lowerPath.endsWith(lowerSuffix + ".yaml")
                    || lowerPath.endsWith(lowerSuffix + ".yml")
                    || lowerPath.endsWith(lowerSuffix + ".json")) {
                return true;
            }
        }
        return false;
    }
}
