package io.apicurio.registry.auth.grants;

import java.util.Optional;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_AUTH;

@Singleton
public class GrantsAccessControllerConfig {

    @ConfigProperty(name = "apicurio.auth.resource-based-authorization.enabled", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Enable per-resource (fine-grained) authorization of groups and artifacts, evaluated from a grants file. Requires authentication.", availableSince = "3.4.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.auth.resource-based-authorization.grants.path")
    @Info(category = CATEGORY_AUTH, description = "Path to the JSON grants file. Required when per-resource authorization is enabled; startup fails if it is missing or invalid.", availableSince = "3.4.0", experimental = true)
    Optional<String> dataPath;

    @ConfigProperty(name = "apicurio.auth.resource-based-authorization.grants.reload-every", defaultValue = "5s")
    @Info(category = CATEGORY_AUTH, description = "Interval for checking grants file changes. Must be a valid duration (e.g. '5s', '1m'). Use apicurio.auth.resource-based-authorization.grants.reload-enabled to disable hot-reload.", availableSince = "3.4.0", experimental = true)
    String reloadEvery;

    @ConfigProperty(name = "apicurio.auth.resource-based-authorization.grants.reload-enabled", defaultValue = "true")
    @Info(category = CATEGORY_AUTH, description = "Enable periodic hot-reload of the grants file. Set to 'false' to disable polling for changes.", availableSince = "3.4.0", experimental = true)
    boolean reloadEnabled;

    public boolean isEnabled() {
        return enabled;
    }

    public String getDataPath() {
        return dataPath.orElse(null);
    }

    public boolean isReloadEnabled() {
        return reloadEnabled;
    }

    public String getReloadEvery() {
        return reloadEvery;
    }
}
