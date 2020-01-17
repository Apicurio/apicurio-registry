/*
 * Copyright 2019 Red Hat
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

import io.apicurio.registry.metrics.ResponseErrorLivenessCheck;
import io.apicurio.registry.rest.beans.Error;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.storage.AlreadyExistsException;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.InvalidArtifactStateException;
import io.apicurio.registry.storage.NotFoundException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.VersionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

import java.util.HashMap;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 * @author Jakub Senko <jsenko@redhat.com>
 */
@ApplicationScoped
@Provider
public class RegistryExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger log = LoggerFactory.getLogger(RegistryExceptionMapper.class);

    private static final Map<Class<?>, Integer> CODE_MAP = new HashMap<>();

    @Inject
    ResponseErrorLivenessCheck liveness;

    static {
        CODE_MAP.put(AlreadyExistsException.class, HTTP_CONFLICT);
        CODE_MAP.put(ArtifactAlreadyExistsException.class, HTTP_CONFLICT);
        CODE_MAP.put(ArtifactNotFoundException.class, HTTP_NOT_FOUND);
        CODE_MAP.put(BadRequestException.class, HTTP_BAD_REQUEST);
        CODE_MAP.put(InvalidArtifactStateException.class, HTTP_BAD_REQUEST);
        CODE_MAP.put(NotFoundException.class, HTTP_NOT_FOUND);
        CODE_MAP.put(RuleAlreadyExistsException.class, HTTP_CONFLICT);
        CODE_MAP.put(RuleNotFoundException.class, HTTP_NOT_FOUND);
        CODE_MAP.put(RuleViolationException.class, HTTP_BAD_REQUEST);
        CODE_MAP.put(VersionNotFoundException.class, HTTP_NOT_FOUND);
    }

    /**
     * @see javax.ws.rs.ext.ExceptionMapper#toResponse(java.lang.Throwable)
     */
    @Override
    public Response toResponse(Throwable t) {
        int code = CODE_MAP.getOrDefault(t.getClass(), HTTP_INTERNAL_ERROR);
        if (code == HTTP_INTERNAL_ERROR) {
            liveness.suspect();
	    log.error(t.getMessage(), t);
        }
        Error error = toError(t, code);
        return Response.status(code)
                .type(MediaType.APPLICATION_JSON)
                .entity(error)
                .build();
    }

    private static Error toError(Throwable t, int code) {
        Error error = new Error();
        error.setErrorCode(code);
        error.setMessage(t.getLocalizedMessage());
        // TODO also return a full stack trace as "detail"?
        return error;
    }
}
