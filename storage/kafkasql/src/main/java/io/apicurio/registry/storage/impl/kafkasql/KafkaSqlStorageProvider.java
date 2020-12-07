package io.apicurio.registry.storage.impl.kafkasql;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageProvider;

@ApplicationScoped
public class KafkaSqlStorageProvider implements RegistryStorageProvider {

    @Inject
    KafkaSqlRegistryStorage storage;

    @Override
    public RegistryStorage storage() {
        return storage;
    }

}
