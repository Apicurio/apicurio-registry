package io.apicurio.registry.storage.impl.polling;

import io.apicurio.common.apps.config.Info;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.List;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;

/**
 * Abstract base class for polling-based storage configuration.
 * Provides shared configuration properties under the {@code apicurio.polling-storage.*} prefix
 * that are common to all polling storage implementations (GitOps, KubernetesOps, etc.).
 */
public abstract class AbstractPollingStorageConfig implements PollingStorageConfig {

    @ConfigProperty(name = "apicurio.polling-storage.id", defaultValue = "main")
    @Info(category = CATEGORY_STORAGE, experimental = true, description = """
            Identifier of this Registry instance. \
            Only data that references this identifier will be loaded.""", availableSince = "3.2.0")
    @Getter
    String registryId;

    @ConfigProperty(name = "apicurio.polling-storage.deterministic-ids-enabled", defaultValue = "true")
    @Info(category = CATEGORY_STORAGE, experimental = true, description = """
            When enabled, contentId and globalId are deterministically generated \
            from content hashes and artifact coordinates if not explicitly specified. \
            When disabled, missing IDs cause an error. \
            Generated IDs use the upper half of positive longs (above Long.MAX_VALUE/2), \
            leaving the lower half for user-specified IDs.""", availableSince = "3.2.0")
    @Getter
    boolean deterministicIdGenerationEnabled;

    @ConfigProperty(name = "apicurio.polling-storage.poll-period", defaultValue = "PT10S")
    @Info(category = CATEGORY_STORAGE, experimental = true, description = """
            Minimum period between data source polls. \
            The actual poll (reading from Git or Kubernetes) only happens \
            when at least this much time has passed since the last poll.""", availableSince = "3.2.0")
    @Getter
    Duration pollPeriod;

    @ConfigProperty(name = "apicurio.polling-storage.debounce.quiet-period", defaultValue = "PT3S")
    @Info(category = CATEGORY_STORAGE, experimental = true, description = """
            After detecting a change, wait until no new changes are seen for this duration \
            before loading data. Absorbs rapid successive pushes. \
            Set to PT0S to disable debouncing.""", availableSince = "3.2.0")
    @Getter
    Duration debounceQuietPeriod;

    @ConfigProperty(name = "apicurio.polling-storage.debounce.max-wait-period", defaultValue = "PT90S")
    @Info(category = CATEGORY_STORAGE, experimental = true, description = """
            Maximum time to wait for changes to settle before forcing a load. \
            Prevents indefinite delay when changes keep arriving. \
            Set to PT0S for no maximum.""", availableSince = "3.2.0")
    @Getter
    Duration debounceMaxWaitPeriod;

    @ConfigProperty(name = "apicurio.polling-storage.file-suffixes", defaultValue = "registry,ar")
    @Info(category = CATEGORY_STORAGE, experimental = true, description = """
            Comma-separated list of file suffixes that identify registry metadata files. \
            Each suffix is combined with supported extensions (.yaml, .yml, .json) to form \
            the full pattern. For example, suffix "registry" matches *.registry.yaml, \
            *.registry.yml, and *.registry.json.""", availableSince = "3.2.0")
    @Getter
    List<String> fileSuffixes;

    @ConfigProperty(name = "apicurio.polling-storage.require-registry-config", defaultValue = "true")
    @Info(category = CATEGORY_STORAGE, experimental = true, description = """
            When enabled, a load is rejected if the data source does not contain \
            a registry configuration file matching the configured registry ID. \
            This prevents accidental data loss from force-pushes or empty branches. \
            To intentionally clear all data, first push a commit with only the \
            registry configuration file (no artifacts), then remove it.""", availableSince = "3.2.0")
    @Getter
    boolean requireRegistryConfig;

    @ConfigProperty(name = "apicurio.polling-storage.source-label-key", defaultValue = "system:source")
    @Info(category = CATEGORY_STORAGE, experimental = true, description = """
            Label key used to tag imported artifacts and groups with their source ID \
            (e.g., repository ID). Set to empty string to disable.""", availableSince = "3.2.0")
    @Getter
    String sourceLabelKey;
}
