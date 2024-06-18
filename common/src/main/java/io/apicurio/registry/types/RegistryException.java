package io.apicurio.registry.types;

/**
 * Generic project exception.
 * <p>
 * Use this exception if you expect the caller would NOT want to handle the exception, possibly letting it
 * bubble up and return a generic 500 error to the user, or there is a special mechanism to deal with it.
 */
// TODO Should be abstract and more specific exception should be used
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

    public RegistryException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
