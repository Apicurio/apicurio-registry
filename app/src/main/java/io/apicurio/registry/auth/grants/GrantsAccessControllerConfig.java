package io.apicurio.registry.auth.grants;

import java.util.Optional;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_AUTH;

@Singleton
public class GrantsAccessControllerConfig {

    @ConfigProperty(name = "apicurio.auth.resource-based-authorization.enabled", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Enable per-resource (fine-grained) authorization of groups and artifacts, evaluated from an access rules file in the Kroxylicious ACL rules format. Requires authentication.", availableSince = "3.4.0")
    boolean enabled;

    @ConfigProperty(name = "apicurio.auth.resource-based-authorization.rules.path")
    @Info(category = CATEGORY_AUTH, description = "Path to the access rules file, in the Kroxylicious ACL rules format. Required when per-resource authorization is enabled; startup fails if it is missing or invalid.", availableSince = "3.4.0")
    Optional<String> dataPath;

    @ConfigProperty(name = "apicurio.auth.resource-based-authorization.rules.reload-every", defaultValue = "5s")
    @Info(category = CATEGORY_AUTH, description = "Interval for checking access rules file changes. Must be a valid duration (e.g. '5s', '1m'). Use apicurio.auth.resource-based-authorization.rules.reload-enabled to disable hot-reload.", availableSince = "3.4.0")
    String reloadEvery;

    @ConfigProperty(name = "apicurio.auth.resource-based-authorization.rules.reload-enabled", defaultValue = "true")
    @Info(category = CATEGORY_AUTH, description = "Enable periodic hot-reload of the access rules file. Set to 'false' to disable polling for changes.", availableSince = "3.4.0")
    boolean reloadEnabled;

    @ConfigProperty(name = "apicurio.auth.resource-based-authorization.owner-bypass.enabled", defaultValue = "true")
    @Info(category = CATEGORY_AUTH, description = "Let the recorded owner of an artifact or group access it regardless of the access rules. Set to 'false' to make the access rules the only per-resource authority.", availableSince = "3.4.0")
    boolean ownerBypassEnabled;

    public boolean isEnabled() {
        return enabled;
    }

    public String getDataPath() {
        return dataPath.orElse(null);
    }

    public boolean isReloadEnabled() {
        return reloadEnabled;
    }

    public boolean isOwnerBypassEnabled() {
        return ownerBypassEnabled;
    }

    public String getReloadEvery() {
        return reloadEvery;
    }
}
