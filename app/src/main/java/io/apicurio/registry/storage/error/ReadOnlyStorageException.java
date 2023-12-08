package io.apicurio.registry.storage.error;

public class ReadOnlyStorageException extends RegistryStorageException {

    private static final long serialVersionUID = 3167774388141291585L;

    public ReadOnlyStorageException(String reason) {
        super(reason);
    }

    public ReadOnlyStorageException(Throwable cause) {
        super(cause);
    }

    public ReadOnlyStorageException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
