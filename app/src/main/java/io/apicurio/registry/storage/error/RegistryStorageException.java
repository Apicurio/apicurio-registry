package io.apicurio.registry.storage.error;

import io.apicurio.registry.types.RegistryException;

/**
 * This exception (not subclasses) should be used for general, major, and unexpected (e.g. SQL) errors.
 */
// TODO Should be abstract and more specific exception should be used
public class RegistryStorageException extends RegistryException {

    private static final long serialVersionUID = 708084955101638005L;

    public RegistryStorageException(Throwable cause) {
        super(cause);
    }

    public RegistryStorageException(String reason, Throwable cause) {
        super(reason, cause);
    }

    public RegistryStorageException(String reason) {
        super(reason);
    }
}
