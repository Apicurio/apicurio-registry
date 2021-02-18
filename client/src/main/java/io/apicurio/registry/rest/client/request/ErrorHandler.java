/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rest.client.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rest.client.exception.ExceptionMapper;
import io.apicurio.registry.rest.client.exception.ForbiddenException;
import io.apicurio.registry.rest.client.exception.NotAuthorizedException;
import io.apicurio.registry.rest.client.exception.RestClientException;
import io.apicurio.registry.rest.v2.beans.Error;
import org.apache.http.HttpStatus;
import org.keycloak.authorization.client.util.HttpResponseException;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class ErrorHandler {

    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Logger logger = Logger.getLogger(BodyHandler.class.getName());

    public static RestClientException handleErrorResponse(InputStream body, HttpResponse.ResponseInfo responseInfo) {
        try {
            if (responseInfo.statusCode() == HttpStatus.SC_UNAUTHORIZED) {
                //authorization error
                Error error = new Error();
                error.setErrorCode(responseInfo.statusCode());
                throw new NotAuthorizedException(error);
            } else {
                if (responseInfo.statusCode() == HttpStatus.SC_FORBIDDEN) {
                    //forbidden error
                    Error error = new Error();
                    error.setErrorCode(responseInfo.statusCode());
                    throw new ForbiddenException(error);
                }
            }
            Error error = mapper.readValue(body, Error.class);
            return ExceptionMapper.map(new RestClientException(error));
        } catch (Exception e) {
            Throwable cause = extractRootCause(e);
            if (cause instanceof RestClientException) {
                throw (RestClientException) cause;
            } else {
                // completely unknown exception
                Error error = new Error();
                error.setMessage(cause.getMessage());
                error.setErrorCode(0);
                logger.log(Level.SEVERE, "Unkown client exception", cause);
                return new RestClientException(error);
            }
        }
    }

    public static RestClientException parseInputSerializingError(JsonProcessingException ex) {
        final Error error = new Error();
        error.setName(ex.getClass().getSimpleName());
        error.setDetail(ex.getMessage());
        error.setMessage("Error trying to parse request body");
        return new RestClientException(new Error());
    }

    public static RestClientException parseError(Exception ex) {
        if (ex instanceof HttpResponseException) {
            //authorization error since something went wrong in the auth provider
            HttpResponseException hre = (HttpResponseException) ex;
            Error error = new Error();
            error.setErrorCode(hre.getStatusCode());
            error.setMessage(hre.getMessage());
            error.setDetail(hre.getReasonPhrase());
            if (hre.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
                return new NotAuthorizedException(error);
            } else {
                return new RestClientException(error);
            }
        }
        final Error error = new Error();
        error.setName(ex.getClass().getSimpleName());
        return new RestClientException(error);
    }

    private static Throwable extractRootCause(Throwable e) {
        Throwable cause = null;
        while (true) {
            if (cause == null) {
                cause = e;
            } else {
                if (cause.getCause() == null || cause.getCause().equals(cause)) {
                    break;
                }
                cause = cause.getCause();
            }
        }
        if (cause.getSuppressed().length != 0) {
            cause = cause.getSuppressed()[0];
        }
        return cause;
    }
}
