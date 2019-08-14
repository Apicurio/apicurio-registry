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

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import io.apicurio.registry.rest.beans.Error;
import io.apicurio.registry.storage.AlreadyExistsException;
import io.apicurio.registry.storage.NotFoundException;
import io.apicurio.registry.storage.StorageException;

/**
 * @author eric.wittmann@gmail.com
 */
@Provider
public class StorageExceptionMapper implements ExceptionMapper<StorageException> {

    private static Error toError(Throwable t, int code) {
        Error error = new Error();
        error.setCode(code);
        error.setMessage(t.getLocalizedMessage());
        // TODO also return a full stack trace as "detail"?
        return error;
    }
    
    private static Response toResponse(Throwable t, int code) {
        Error error = toError(t, code);
        Response response = Response.status(code).type(MediaType.APPLICATION_JSON).entity(error).build();
        return response;
    }

    /**
     * @see javax.ws.rs.ext.ExceptionMapper#toResponse(java.lang.Throwable)
     */
    @Override
    public Response toResponse(StorageException exception) {
        int code = 500;
        if (exception instanceof AlreadyExistsException) {
            code = 409;
        }
        if (exception instanceof NotFoundException) {
            code = 404;
        }
        return toResponse(exception, code);
    }

}
