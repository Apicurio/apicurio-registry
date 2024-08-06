package io.apicurio.registry.config;

import io.apicurio.common.apps.config.DynamicConfigStorage;
import io.apicurio.common.apps.config.DynamicConfigStorageAccessor;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RegistryConfigStorageAccessor implements DynamicConfigStorageAccessor {

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigStorageAccessor#getConfigStorage()
     */
    @Override
    public DynamicConfigStorage getConfigStorage() {
        return storage;
    }

}
