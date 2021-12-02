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
package io.apicurio.multitenant.api;

import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;

import io.apicurio.multitenant.storage.TenantAlreadyExistsException;
import io.apicurio.multitenant.storage.TenantNotFoundException;
import io.apicurio.multitenant.api.datamodel.Error;

/**
 * Custom Exception Mapper to map internal exceptions to http responses
 * @author Fabian Martinez
 */
@ApplicationScoped
@Provider
public class TenantManagerExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Map<Class<? extends Exception>, Integer> CODE_MAP;

    static {
        Map<Class<? extends Exception>, Integer> map = new HashMap<>();
        map.put(TenantNotFoundException.class, HTTP_NOT_FOUND);
        map.put(TenantAlreadyExistsException.class, HTTP_CONFLICT);
        CODE_MAP = Collections.unmodifiableMap(map);
    }

    @Inject
    Logger log;

    @Override
    public Response toResponse(Throwable exception) {
        Response.ResponseBuilder builder;
        int code;
        if (exception instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) exception;
            Response response = wae.getResponse();
            builder = Response.fromResponse(response);
            code = response.getStatus();
        } else {
            code = CODE_MAP.getOrDefault(exception.getClass(), HTTP_INTERNAL_ERROR);
            builder = Response.status(code);
        }

        if (code == HTTP_INTERNAL_ERROR) {
            log.error(exception.getMessage(), exception);
        }

        Error error = new Error();
        error.setMessage(exception.getMessage());
        error.setErrorCode(code);
        error.setName(exception.getClass().getName());
        return builder.type(MediaType.APPLICATION_JSON)
                      .entity(error)
                      .build();
    }

}
