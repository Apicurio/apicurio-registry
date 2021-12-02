/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.rest;

import io.apicurio.registry.ccompat.rest.error.ErrorCode;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.rest.v2.beans.Error;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.services.http.ErrorHttpResponse;
import io.apicurio.registry.services.http.RegistryExceptionMapperService;
import io.apicurio.registry.storage.AlreadyExistsException;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ContentNotFoundException;
import io.apicurio.registry.storage.VersionNotFoundException;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.net.HttpURLConnection.HTTP_CONFLICT;

/**
 * TODO use v1 beans when appropriate (when handling REST API v1 calls)
 *
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@ApplicationScoped
@Provider
public class RegistryExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Map<Class<? extends Exception>, Integer> CONFLUENT_CODE_MAP;

    @Inject
    Logger log;

    @Inject
    RegistryExceptionMapperService exceptionMapper;

    @Context
    HttpServletRequest request;

    static {
        Map<Class<? extends Exception>, Integer> map = new HashMap<>();
        map.put(AlreadyExistsException.class, HTTP_CONFLICT);
        map.put(ArtifactAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(ArtifactNotFoundException.class, ErrorCode.SUBJECT_NOT_FOUND.value());
        map.put(ContentNotFoundException.class, ErrorCode.SCHEMA_NOT_FOUND.value());
        map.put(RuleViolationException.class, ErrorCode.INVALID_COMPATIBILITY_LEVEL.value());
        map.put(VersionNotFoundException.class, ErrorCode.VERSION_NOT_FOUND.value());
        map.put(UnprocessableEntityException.class, ErrorCode.INVALID_SCHEMA.value());
        CONFLUENT_CODE_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * @see javax.ws.rs.ext.ExceptionMapper#toResponse(java.lang.Throwable)
     */
    @Override
    public Response toResponse(Throwable t) {

        ErrorHttpResponse res = exceptionMapper.mapException(t);

        Response.ResponseBuilder builder;
        if (res.getJaxrsResponse() != null) {
            builder = Response.fromResponse(res.getJaxrsResponse());
        } else {
            builder = Response.status(res.getStatus());
        }

        Error error = res.getError();
        if (isCompatEndpoint()) {
            error.setDetail(null);
            error.setName(null);
            error.setErrorCode(CONFLUENT_CODE_MAP.getOrDefault(t.getClass(), 0));
        }
        return builder.type(MediaType.APPLICATION_JSON)
                      .entity(error)
                      .build();
    }

    /**
     * Returns true if the endpoint that caused the error is a "ccompat" endpoint.  If so
     * we need to simplify the error we return.  The apicurio error structure has at least
     * one additional property.
     */
    private boolean isCompatEndpoint() {
        if (this.request != null) {
            return this.request.getRequestURI().contains("ccompat");
        }
        return false;
    }

}
