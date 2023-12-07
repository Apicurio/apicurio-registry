package io.apicurio.registry.storage.error;

import lombok.Getter;

public class ConfigPropertyNotFoundException extends NotFoundException {

    private static final long serialVersionUID = -9088094366735526214L;

    @Getter
    private final String propertyName;


    public ConfigPropertyNotFoundException(String propertyName) {
        super("No configuration property named '" + propertyName + "' was found.");
        this.propertyName = propertyName;
    }
}
