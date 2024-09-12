package io.apicurio.registry.storage.error;

public abstract class AlreadyExistsException extends RegistryStorageException {

    private static final long serialVersionUID = 5055445625652989500L;

    protected AlreadyExistsException(Throwable cause) {
        super(cause);
    }

    protected AlreadyExistsException(String reason, Throwable cause) {
        super(reason, cause);
    }

    protected AlreadyExistsException(String reason) {
        super(reason);
    }
}
