package io.apicurio.registry.storage;

public class LoggingConfigurationNotFoundException extends StorageException {

    /**
     *
     */
    private static final long serialVersionUID = -2406230675956374910L;

    private String logger;

    public LoggingConfigurationNotFoundException(String logger, Throwable cause) {
        super(cause);
    }

    public LoggingConfigurationNotFoundException(String logger) {
        super();
    }

    /**
     * @see java.lang.Throwable#getMessage()
     */
    @Override
    public String getMessage() {
        return "No loggingConfiguration found for logger '" + logger + "'";
    }

}
