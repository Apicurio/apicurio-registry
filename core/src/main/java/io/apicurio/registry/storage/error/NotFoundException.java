package io.apicurio.registry.storage.error;

public abstract class NotFoundException extends RegistryStorageException {

    private static final long serialVersionUID = 7134307797211927863L;

    protected NotFoundException(Throwable cause) {
        super(cause);
    }

    protected NotFoundException(String reason, Throwable cause) {
        super(reason, cause);
    }

    protected NotFoundException(String reason) {
        super(reason);
    }
}
