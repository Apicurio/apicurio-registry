package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.storage.RegistryStorage;

public interface RegistryStorageDecorator extends RegistryStorage {

    boolean isEnabled();

    /**
     * Decorators are ordered by natural int ordering, e.g. one with a lower order value is executed first.
     * <p>
     * Use {@link RegistryStorageDecoratorOrderConstants}.
     */
    int order();

    void setDelegate(RegistryStorage delegate);
}
