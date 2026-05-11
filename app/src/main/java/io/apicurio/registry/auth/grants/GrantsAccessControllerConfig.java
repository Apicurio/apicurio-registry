package io.apicurio.registry.auth.grants;

import java.util.Optional;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_AUTH;

@Singleton
public class GrantsAccessControllerConfig {

    @ConfigProperty(name = "apicurio.auth.resource-based-authorization.enabled", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Enable per-resource authorization using grants file", availableSince = "3.0.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.auth.resource-based-authorization.grants.path")
    @Info(category = CATEGORY_AUTH, description = "Path to the JSON grants data file (hot-reloaded on change)", availableSince = "3.0.0", experimental = true)
    Optional<String> dataPath;

    @ConfigProperty(name = "apicurio.auth.resource-based-authorization.grants.reload-every", defaultValue = "5s")
    @Info(category = CATEGORY_AUTH, description = "Interval for checking grants file changes. Set to 'off' to disable hot-reload.", availableSince = "3.0.0", experimental = true)
    String reloadEvery;

    public boolean isEnabled() {
        return enabled;
    }

    public String getDataPath() {
        return dataPath.orElse(null);
    }
}
