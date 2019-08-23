package io.apicurio.registry.types;

/**
 * Generic project exception.
 *
 * @author Ales Justin
 */
public class RegistryException extends RuntimeException {
    private static final long serialVersionUID = 7551763806044016474L;

    public RegistryException() {
    }

    public RegistryException(String message) {
        super(message);
    }

    public RegistryException(String message, Throwable cause) {
        super(message, cause);
    }

    public RegistryException(Throwable cause) {
        super(cause);
    }

    public RegistryException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
