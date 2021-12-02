package io.apicurio.registry.rest.client.exception;

import io.apicurio.registry.rest.v2.beans.Error;

public class TenantManagerClientException extends RestClientException {

    private static final long serialVersionUID = 1L;

    public TenantManagerClientException(Error error) {
        super(error);
    }
}
