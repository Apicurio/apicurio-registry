package io.apicurio.registry.config;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_SYSTEM;

/**
 * Configuration for the global experimental features gate. When disabled (default), all features marked as
 * experimental will be unavailable regardless of their individual toggle.
 */
@Singleton
public class ExperimentalFeaturesConfig {

    @ConfigProperty(name = "apicurio.features.experimental.enabled", defaultValue = "false")
    @Info(category = CATEGORY_SYSTEM, description = "Enable experimental features. When disabled, all features marked as experimental will be unavailable regardless of their individual configuration.", availableSince = "3.0.0")
    boolean experimentalFeaturesEnabled;

    public boolean isExperimentalFeaturesEnabled() {
        return experimentalFeaturesEnabled;
    }
}
