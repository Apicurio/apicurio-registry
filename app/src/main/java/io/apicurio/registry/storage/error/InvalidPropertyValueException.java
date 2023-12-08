package io.apicurio.registry.storage.error;

import io.apicurio.registry.types.RegistryException;

public class InvalidPropertyValueException extends RegistryException {

    private static final long serialVersionUID = 4930984250014469626L;

    public InvalidPropertyValueException(String message) {
        super(message);
    }

}