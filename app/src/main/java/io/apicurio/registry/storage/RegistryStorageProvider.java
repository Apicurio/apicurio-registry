package io.apicurio.registry.storage;

/**
 * Provider interface for non-default storage interfaces.
 *
 * It's mandatory to implement this interface for non-default storage implementations.
 *
 */
public interface RegistryStorageProvider {

    RegistryStorage storage();

}
