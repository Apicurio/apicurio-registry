package io.apicurio.registry.iceberg.rest.v1.impl;

import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_ICEBERG;

/**
 * Configuration for the Iceberg REST Catalog API.
 */
@ApplicationScoped
public class IcebergConfig {

    @ConfigProperty(name = "apicurio.iceberg.enabled", defaultValue = "true")
    @Info(category = CATEGORY_ICEBERG, description = "Enable the Iceberg REST Catalog API", availableSince = "3.0.0")
    boolean enabled;

    @ConfigProperty(name = "apicurio.iceberg.warehouse", defaultValue = "")
    @Info(category = CATEGORY_ICEBERG, description = "Default warehouse location for Iceberg tables", availableSince = "3.0.0")
    String defaultWarehouse;

    @ConfigProperty(name = "apicurio.iceberg.default-prefix", defaultValue = "default")
    @Info(category = CATEGORY_ICEBERG, description = "Default prefix (catalog identifier) for the Iceberg REST API", availableSince = "3.0.0")
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
