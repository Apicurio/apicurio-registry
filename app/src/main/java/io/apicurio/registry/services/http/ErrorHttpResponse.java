package io.apicurio.registry.services.http;

import jakarta.ws.rs.core.Response;

import io.apicurio.registry.rest.v2.beans.Error;


public class ErrorHttpResponse {

    private int status;
    private Error error;
    private Response jaxrsResponse;

    public ErrorHttpResponse(int status, Error error, Response jaxrsResponse) {
        this.status = status;
        this.error = error;
        this.jaxrsResponse = jaxrsResponse;
    }

    /**
     * @return the status
     */
    public int getStatus() {
        return status;
    }

    /**
     * @return the error
     */
    public Error getError() {
        return error;
    }

    /**
     * @return the jaxrsResponse
     */
    public Response getJaxrsResponse() {
        return jaxrsResponse;
    }

}
