package io.apicurio.registry.types;

/**
 * Generic checked project exception.
 * <p>
 * Use this exception if you expect the caller would want to handle the exception, possibly making different
 * decisions based on circumstances.
 * <p>
 * This class is intended for extension. Create a more specific exception.
 */
public abstract class CheckedRegistryException extends Exception {

    private static final long serialVersionUID = 378403778033312738L;

    protected CheckedRegistryException(String message) {
        super(message);
    }

    protected CheckedRegistryException(String message, Throwable cause) {
        super(message, cause);
    }

    protected CheckedRegistryException(Throwable cause) {
        super(cause);
    }
}
