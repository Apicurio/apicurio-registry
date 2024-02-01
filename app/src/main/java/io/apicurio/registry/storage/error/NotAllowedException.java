package io.apicurio.registry.storage.error;

/**
 * Operation is valid, but the storage is refusing to execute it (under current circumstances).
 */
public class NotAllowedException extends RegistryStorageException {

    private static final long serialVersionUID = -7056287381918767756L;

    public NotAllowedException(String reason) {
        super(reason);
    }

    public NotAllowedException(Throwable cause) {
        super(cause);
    }

    public NotAllowedException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
