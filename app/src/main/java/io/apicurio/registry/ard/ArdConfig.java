package io.apicurio.registry.ard;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_ARD;

/**
 * Configuration properties for the ARD (Agentic Resource Discovery) well-known API endpoints.
 */
@Singleton
public class ArdConfig {

    @ConfigProperty(name = "apicurio.ard.enabled", defaultValue = "false")
    @Info(category = CATEGORY_ARD, description = "Enable the ARD (Agentic Resource Discovery) well-known API endpoints", availableSince = "3.3.3", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.ard.federation.default", defaultValue = "none")
    @Info(category = CATEGORY_ARD, description = "Default ARD federation mode advertised by this registry. Only 'none' (no federation) is currently implemented.", availableSince = "3.3.3")
    String federationDefault;

    public boolean isEnabled() {
        return enabled;
    }

    public String getFederationDefault() {
        return federationDefault;
    }
}
