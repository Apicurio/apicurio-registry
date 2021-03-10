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

package io.apicurio.registry.rest;

import java.io.IOException;
import java.net.URI;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import io.apicurio.registry.services.DisabledApisMatcherService;

/**
 * @author Fabian Martinez
 */
@PreMatching
@Priority(1)
@Provider
public class DisableApisFilter implements ContainerRequestFilter {

    @Inject
    DisabledApisMatcherService disabledApisMatcherService;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        URI reqUri = requestContext.getUriInfo().getRequestUri();
        String path = reqUri.getPath();

        boolean disabled = disabledApisMatcherService.isDisabled(path);

        if (disabled) {
            Response response = Response.status(Status.NOT_FOUND).build();
            requestContext.abortWith(response);
        }

    }
}
