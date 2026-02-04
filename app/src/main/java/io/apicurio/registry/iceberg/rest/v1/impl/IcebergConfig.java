package io.apicurio.registry.iceberg.rest.v1.impl;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Configuration for the Iceberg REST Catalog API.
 */
@ApplicationScoped
public class IcebergConfig {

    @ConfigProperty(name = "apicurio.iceberg.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "apicurio.iceberg.warehouse", defaultValue = "")
    String defaultWarehouse;

    @ConfigProperty(name = "apicurio.iceberg.default-prefix", defaultValue = "default")
    String defaultPrefix;

    public boolean isEnabled() {
        return enabled;
    }

    public String getDefaultWarehouse() {
        return defaultWarehouse;
    }

    public String getDefaultPrefix() {
        return defaultPrefix;
    }
}
