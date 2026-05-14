package io.apicurio.registry.exception;

public class ConfigurationException extends RuntimeException {

    private static final String MESSAGE = "Required configuration property '%s' has not been set.";

    public ConfigurationException(String message) {
        super(message);
    }

    public static ConfigurationException required(String configurationProperty) {
        return new ConfigurationException(String.format(MESSAGE, configurationProperty));
    }
}
