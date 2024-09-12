package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.RegistryStorage;

public interface KafkaSqlMessage {

    KafkaSqlMessageKey getKey();

    Object dispatchTo(RegistryStorage storage);

}
