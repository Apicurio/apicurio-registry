package io.apicurio.registry.storage.error;

import lombok.Getter;


public class LogConfigurationNotFoundException extends NotFoundException {

    private static final long serialVersionUID = -2406230675956374910L;

    @Getter
    private final String logger;


    public LogConfigurationNotFoundException(String logger, Throwable cause) {
        super(message(logger), cause);
        this.logger = logger;
    }


    public LogConfigurationNotFoundException(String logger) {
        super(message(logger));
        this.logger = logger;
    }


    private static String message(String logger) {
        return "No configuration found for logger '" + logger + "'";
    }
}
