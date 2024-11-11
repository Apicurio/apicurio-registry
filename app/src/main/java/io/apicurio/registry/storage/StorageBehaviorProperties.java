package io.apicurio.registry.storage;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.types.VersionState;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Set;

@ApplicationScoped
public class StorageBehaviorProperties {

    @ConfigProperty(name = "artifacts.skip.disabled.latest", defaultValue = "true")
    @Info(category = "storage", description = "Skip artifact versions with DISABLED state when retrieving latest artifact version", availableSince = "2.4.2")
    boolean skipLatestDisabledArtifacts;

    public Set<VersionState> getDefaultArtifactRetrievalBehavior() {
        if (skipLatestDisabledArtifacts) {
            return RetrievalBehavior.SKIP_DISABLED_LATEST;
        } else {
            return RetrievalBehavior.ALL_STATES;
        }
    }
}
