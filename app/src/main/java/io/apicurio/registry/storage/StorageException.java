package io.apicurio.registry.storage;

/**
 * Represents a failure of a storage operation.
 */
public class StorageException extends RuntimeException { // TODO Runtime or not?


    public StorageException() {
    }

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
