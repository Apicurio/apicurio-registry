package io.apicurio.registry.agents.ard;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_ARD;

/**
 * Configuration properties for the ARD (Agentic Resource Discovery) well-known API endpoints.
 */
@Singleton
public class ArdConfig {

    @ConfigProperty(name = "apicurio.ard.enabled")
    @Info(category = CATEGORY_ARD, description = "Enable the ARD (Agentic Resource Discovery) well-known API endpoints. Defaults to apicurio.agents.enabled", dependsOn = "apicurio.agents.enabled", availableSince = "3.3.3", experimental = true)
    boolean enabled;


    public boolean isEnabled() {
        return enabled;
    }

}
