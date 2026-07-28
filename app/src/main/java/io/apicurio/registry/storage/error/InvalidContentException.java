package io.apicurio.registry.storage.error;

import io.apicurio.registry.types.RegistryException;

public class InvalidContentException extends RegistryException {

    private static final long serialVersionUID = 1L;

    public InvalidContentException(String message) {
        super(message);
    }

}
