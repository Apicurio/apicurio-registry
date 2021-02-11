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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rest.client.exception.ForbiddenException;
import io.apicurio.registry.rest.client.exception.NotAuthorizedException;
import io.apicurio.registry.rest.client.exception.RestClientException;
import io.apicurio.registry.rest.client.response.ExceptionMapper;
import io.apicurio.registry.rest.v2.beans.Error;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class BodyHandler<W> implements HttpResponse.BodyHandler<Supplier<W>> {

    private final Class<W> wClass;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(BodyHandler.class.getName());

    public BodyHandler(Class<W> wClass) {
        this.wClass = wClass;
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public HttpResponse.BodySubscriber<Supplier<W>> apply(HttpResponse.ResponseInfo responseInfo) {
        return asJSON(wClass, responseInfo);
    }

    public static <W> HttpResponse.BodySubscriber<Supplier<W>> asJSON(Class<W> targetType, HttpResponse.ResponseInfo responseInfo) {
        HttpResponse.BodySubscriber<InputStream> upstream = HttpResponse.BodySubscribers.ofInputStream();
        return HttpResponse.BodySubscribers.mapping(
                upstream,
                inputStream -> toSupplierOfType(inputStream, targetType, responseInfo));
    }

    public static <W> Supplier<W> toSupplierOfType(InputStream body, Class<W> targetType, HttpResponse.ResponseInfo responseInfo) {
        return () -> {
            try {
                if (isFailure(responseInfo)) {
                    throw handleErrorResponse(body, responseInfo);
                } else {
                    if (targetType.getSimpleName().equals(InputStream.class.getSimpleName())) {
                        return (W) body;
                    } else {
                        return mapper.readValue(body, targetType);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private static boolean isFailure(HttpResponse.ResponseInfo responseInfo) {
        return responseInfo.statusCode() / 100 != 2;
    }

    private static RestClientException handleErrorResponse(InputStream body, HttpResponse.ResponseInfo responseInfo) {
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