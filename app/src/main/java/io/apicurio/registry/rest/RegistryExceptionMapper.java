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
import io.apicurio.registry.storage.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.HashMap;
import java.util.Map;

import static java.net.HttpURLConnection.*;

/**
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 * @author Jakub Senko <jsenko@redhat.com>
 */
@ApplicationScoped
@Provider
public class RegistryExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger log = LoggerFactory.getLogger(RegistryExceptionMapper.class);

    private static final Map<Class, Integer> CODE_MAP = new HashMap<>();

    @Inject
    ResponseErrorLivenessCheck liveness;

    static {
        CODE_MAP.put(AlreadyExistsException.class, HTTP_CONFLICT);
        CODE_MAP.put(NotFoundException.class, HTTP_NOT_FOUND);
        CODE_MAP.put(RuleViolationException.class, HTTP_BAD_REQUEST);
    }

    /**
     * @see javax.ws.rs.ext.ExceptionMapper#toResponse(java.lang.Throwable)
     */
    @Override
    public Response toResponse(Throwable t) {
        int code = CODE_MAP.getOrDefault(t.getClass(), HTTP_INTERNAL_ERROR);
        if (code == HTTP_INTERNAL_ERROR) {
            liveness.suspect();
        }
        Error error = toError(t, code);
        return Response.status(code)
                .type(MediaType.APPLICATION_JSON)
                .entity(error)
                .build();
    }

    private static Error toError(Throwable t, int code) {
        Error error = new Error();
        error.setCode(code);
        error.setMessage(t.getLocalizedMessage());
        // TODO also return a full stack trace as "detail"?
        return error;
    }
}
