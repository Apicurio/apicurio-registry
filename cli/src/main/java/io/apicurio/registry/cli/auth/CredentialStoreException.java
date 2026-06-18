package io.apicurio.registry.cli.auth;

public class CredentialStoreException extends RuntimeException {

    public CredentialStoreException(final String message) {
        super(message);
    }

    CredentialStoreException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
