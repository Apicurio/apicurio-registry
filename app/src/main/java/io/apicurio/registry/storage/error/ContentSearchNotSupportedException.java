package io.apicurio.registry.storage.error;

import io.apicurio.registry.types.RegistryException;

public class ContentSearchNotSupportedException extends RegistryException {

    private static final long serialVersionUID = 1L;

    public ContentSearchNotSupportedException(String message) {
        super(message);
    }

}
