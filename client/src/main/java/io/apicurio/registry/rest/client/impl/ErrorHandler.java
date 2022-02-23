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

package io.apicurio.registry.rest.client.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rest.client.exception.ExceptionMapper;
import io.apicurio.registry.rest.client.exception.RateLimitedClientException;
import io.apicurio.registry.rest.client.exception.RestClientException;
import io.apicurio.registry.rest.v2.beans.Error;
import io.apicurio.rest.client.auth.exception.ForbiddenException;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import io.apicurio.rest.client.error.ApicurioRestClientException;
import io.apicurio.rest.client.error.RestClientErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class ErrorHandler implements RestClientErrorHandler {

    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Logger logger = LoggerFactory.getLogger(ErrorHandler.class);
    public static final int UNAUTHORIZED_CODE = 401;
    public static final int FORBIDDEN_CODE = 403;
    public static final int TOO_MANY_REQUESTS_CODE = 429;

    @Override
    public ApicurioRestClientException handleErrorResponse(InputStream body, int statusCode) {
        try {
            // NOTE: io.apicurio.registry.mt.TenantNotFoundException is also mapped to HTTP code 403 to avoid scanning attacks
            if (statusCode == UNAUTHORIZED_CODE) {
                //authorization error
                return new NotAuthorizedException("Authentication exception");
            } else if (statusCode == FORBIDDEN_CODE) {
                //forbidden error
                return new ForbiddenException("Authorization error");
            } else if (statusCode == TOO_MANY_REQUESTS_CODE) {
                //rate limited - too many requests error
                return new RateLimitedClientException("Too many requests");
            }
            Error error = mapper.readValue(body, Error.class);
            logger.debug("Error returned by Registry application: {}", error.getMessage());
            return ExceptionMapper.map(new RestClientException(error));
        } catch (Exception e) {
            Throwable cause = extractRootCause(e);
            if (cause instanceof RestClientException) {
                throw (RestClientException) cause;
            } else {
                // completely unknown exception
                Error error = new Error();
                error.setMessage(cause.getMessage());
                error.setErrorCode(statusCode);
                return new RestClientException(error);
            }
        }
    }

    @Override
    public ApicurioRestClientException parseInputSerializingError(JsonProcessingException ex) {
        final Error error = new Error();
        error.setName(ex.getClass().getSimpleName());
        error.setDetail(ex.getMessage());
        error.setMessage("Error trying to parse request body");
        logger.debug("Error trying to parse request body:", ex);
        return new RestClientException(new Error());
    }

    @Override
    public ApicurioRestClientException parseError(Exception ex) {
        final Error error = new Error();
        error.setName(ex.getClass().getSimpleName());
        error.setMessage(ex.getMessage());
        logger.debug("Error returned:", ex);
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
        logger.debug("Unknown client exception:", cause);
        return cause;
    }
}
