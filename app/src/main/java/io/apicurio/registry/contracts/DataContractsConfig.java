package io.apicurio.registry.contracts;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_CONTRACTS;

/**
 * Configuration properties for Data Contracts support.
 * Data Contracts provide governance, lifecycle management, and quality
 * metadata for schemas and APIs stored in the registry.
 */
@Singleton
public class DataContractsConfig {

    @ConfigProperty(name = "apicurio.contracts.enabled", defaultValue = "false")
    @Info(category = CATEGORY_CONTRACTS, description = "Enable data contracts support", availableSince = "3.2.0", experimental = true)
    boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }
}
