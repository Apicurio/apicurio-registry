package io.apicurio.registry.storage.impl.search;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_SEARCH;

/**
 * Configuration for Lucene-based content search indexing.
 */
@ApplicationScoped
public class LuceneSearchConfig {

    private static final Logger log = LoggerFactory.getLogger(LuceneSearchConfig.class);

    @ConfigProperty(name = "apicurio.search.lucene.enabled", defaultValue = "false")
    @Info(category = CATEGORY_SEARCH, description = "Enable search indexing", availableSince = "3.2.0")
    boolean enabled;

    @ConfigProperty(name = "apicurio.search.lucene.index-path")
    @Info(category = CATEGORY_SEARCH, description = "Path to where the search index file should be written", availableSince = "3.2.0")
    Optional<String> indexPath;

    @ConfigProperty(name = "apicurio.search.lucene.update-mode", defaultValue = "AUTO")
    @Info(category = CATEGORY_SEARCH, description = "Search index update mode (AUTO, SYNCHRONOUS, ASYNCHRONOUS)", availableSince = "3.2.0")
    String updateModeConfig;

    @ConfigProperty(name = "apicurio.search.lucene.polling-interval", defaultValue = "30s")
    @Info(category = CATEGORY_SEARCH, description = "Asynchronous search index update polling interval", availableSince = "3.2.0")
    Duration pollingInterval;

    @ConfigProperty(name = "apicurio.search.lucene.full-rebuild-threshold", defaultValue = "1000")
    @Info(category = CATEGORY_SEARCH, description = "Threshold to reach before a full search index rebuild is required", availableSince = "3.2.0")
    int fullRebuildThreshold;

    @ConfigProperty(name = "apicurio.search.lucene.ram-buffer-size-mb", defaultValue = "256")
    @Info(category = CATEGORY_SEARCH, description = "Search index RAM buffer size (in MB)", availableSince = "3.2.0")
    int ramBufferSizeMB;

    @Inject
    @Current
    RegistryStorage storage;

    private IndexUpdateMode resolvedMode;
    private String resolvedIndexPath;

    @PostConstruct
    void initialize() {
        if (enabled) {
            resolvedMode = determineUpdateMode();
            resolvedIndexPath = indexPath.orElse(System.getProperty("java.io.tmpdir")
                    + "/apicurio-registry-lucene");
            log.info("Lucene search index ENABLED");
            log.info("  - Update mode: {}", resolvedMode);
            log.info("  - Index path: {}", resolvedIndexPath);
            log.info("  - Storage type: {}", storage.storageName());
            if (resolvedMode == IndexUpdateMode.ASYNCHRONOUS) {
                log.info("  - Polling interval: {}", pollingInterval);
            }
        } else {
            log.info("Lucene search index DISABLED");
        }
    }

    /**
     * Determines the index update mode based on configuration and storage type.
     */
    private IndexUpdateMode determineUpdateMode() {
        // Explicit configuration takes precedence
        if (!"AUTO".equalsIgnoreCase(updateModeConfig)) {
            try {
                IndexUpdateMode mode = IndexUpdateMode.valueOf(updateModeConfig.toUpperCase());
                log.info("Using explicitly configured update mode: {}", mode);
                return mode;
            } catch (IllegalArgumentException e) {
                log.warn("Invalid update mode '{}', falling back to AUTO detection",
                        updateModeConfig);
            }
        }

        // Auto-detect based on storage type
        String storageType = storage.storageName().toLowerCase();

        switch (storageType) {
            case "kafkasql":
            case "gitops":
            case "inmemory":
                log.info("Storage type '{}' is inherently single-node, using SYNCHRONOUS indexing mode",
                        storageType);
                return IndexUpdateMode.SYNCHRONOUS;
            case "sql":
                log.info("Multi-node SQL deployment possible, using ASYNCHRONOUS indexing mode");
                return IndexUpdateMode.ASYNCHRONOUS;
            default:
                log.warn("Unknown storage type '{}', defaulting to ASYNCHRONOUS indexing mode", storageType);
                return IndexUpdateMode.ASYNCHRONOUS;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public IndexUpdateMode getUpdateMode() {
        return resolvedMode;
    }

    public String getIndexPath() {
        return resolvedIndexPath;
    }

    public Duration getPollingInterval() {
        return pollingInterval;
    }

    public int getFullRebuildThreshold() {
        return fullRebuildThreshold;
    }

    public int getRamBufferSizeMB() {
        return ramBufferSizeMB;
    }
}
