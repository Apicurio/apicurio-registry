package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.storage.RegistryStorage;

/**
 * Minimal base class for storage decorators. Only holds the delegate reference. The proxy infrastructure
 * ({@link RegistryStorageProxyFactory}) handles routing unoverridden methods directly to the delegate.
 */
public abstract class RegistryStorageDecoratorBase implements RegistryStorageDecorator {

    protected RegistryStorage delegate;

    @Override
    public void setDelegate(RegistryStorage delegate) {
        this.delegate = delegate;
    }
}
