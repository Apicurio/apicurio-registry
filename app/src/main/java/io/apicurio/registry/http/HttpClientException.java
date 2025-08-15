package io.apicurio.registry.http;

import io.apicurio.registry.types.RegistryException;

public class HttpClientException extends RegistryException {

    private static final long serialVersionUID = 0;

    protected HttpClientException(Throwable cause) {
        super(cause);
    }

    protected HttpClientException(String reason, Throwable cause) {
        super(reason, cause);
    }

    protected HttpClientException(String reason) {
        super(reason);
    }

}
