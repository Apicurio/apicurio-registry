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

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import io.apicurio.registry.rest.beans.Error;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleNotFoundException;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class ErrorFactory {
    
    private static Error toError(Throwable t, int code) {
        Error error = new Error();
        error.setCode(code);
        error.setMessage(t.getLocalizedMessage());
        // TODO also return a full stack trace as "detail"?
        return error;
    }
    
    private static WebApplicationException toWebApplicationException(Throwable t, int code) {
        Error error = toError(t, code);
        Response response = Response.status(code).entity(error).build();
        WebApplicationException wae = new WebApplicationException(response);
        return wae;
    }
    
    /**
     * Creates an appropriate jax-rs exception from the given error.
     * @param e
     */
    public WebApplicationException create(RegistryStorageException e) {
        return toWebApplicationException(e, 500);
    }

    /**
     * Creates an appropriate jax-rs exception from the given error.
     * @param e
     */
    public WebApplicationException create(RuleAlreadyExistsException e) {
        return toWebApplicationException(e, 409);
    }

    /**
     * Creates an appropriate jax-rs exception from the given error.
     * @param e
     */
    public WebApplicationException create(RuleNotFoundException e) {
        return toWebApplicationException(e, 404);
    }

}
