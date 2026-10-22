package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.storage.RegistryStorage;

/**
 * A decorator that can intercept specific {@link RegistryStorage} methods. Decorators only need to override
 * the methods they care about; the proxy infrastructure ({@link RegistryStorageProxyFactory}) routes
 * unoverridden methods directly to the underlying storage.
 */
public interface RegistryStorageDecorator {

    boolean isEnabled();

    /**
     * Decorators are ordered by natural int ordering, e.g. one with a lower order value is executed first.
     * <p>
     * Use {@link RegistryStorageDecoratorOrderConstants}.
     */
    int order();

    void setDelegate(RegistryStorage delegate);
}
