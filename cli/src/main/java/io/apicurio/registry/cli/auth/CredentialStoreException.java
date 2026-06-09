package io.apicurio.registry.cli.auth;

class CredentialStoreException extends RuntimeException {

    CredentialStoreException(final String message) {
        super(message);
    }

    CredentialStoreException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
