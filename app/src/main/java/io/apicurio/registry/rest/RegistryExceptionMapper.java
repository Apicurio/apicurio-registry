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

import io.apicurio.registry.rest.beans.Error;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.storage.AlreadyExistsException;
import io.apicurio.registry.storage.NotFoundException;
import io.apicurio.registry.types.RegistryException;

import java.net.HttpURLConnection;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 */
@Provider
public class RegistryExceptionMapper implements ExceptionMapper<RegistryException> {

    private static Error toError(Throwable t, int code) {
        Error error = new Error();
        error.setCode(code);
        error.setMessage(t.getLocalizedMessage());
        // TODO also return a full stack trace as "detail"?
        return error;
    }
    
    private static Response toResponse(Throwable t, int code) {
        Error error = toError(t, code);
        return Response.status(code)
                       .type(MediaType.APPLICATION_JSON)
                       .entity(error)
                       .build();
    }

    /**
     * @see javax.ws.rs.ext.ExceptionMapper#toResponse(java.lang.Throwable)
     */
    @Override
    public Response toResponse(RegistryException exception) {
        int code = HttpURLConnection.HTTP_INTERNAL_ERROR;
        if (exception instanceof AlreadyExistsException) {
            code = HttpURLConnection.HTTP_CONFLICT;
        }
        if (exception instanceof NotFoundException) {
            code = HttpURLConnection.HTTP_NOT_FOUND;
        }
        if (exception instanceof RuleViolationException) {
            code = HttpURLConnection.HTTP_BAD_REQUEST;
        }
        return toResponse(exception, code);
    }

}
