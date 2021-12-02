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
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;

/**
 * Request filter that rewrites requests from the path the API had for the 1.X releases to it's new location under "/apis/registry/v1"
 * This is just a compatibility utility to make upgrades from 1.X to 2.X easier and less error prone,
 * it's preferable to update the clients configuration to use the proper new API path.
 *
 * This filter will warn when being used to advice users about updating it's clients or it's configuration.
 *
 * Because of being a utility to make updates easier this filter may be removed in future releases.
 *
 * @author Fabian Martinez
 */
@PreMatching
@Priority(Priorities.USER)
@Provider
public class CompatibilityV1ApiRequestFilter implements ContainerRequestFilter {

    @Inject
    Logger log;

    public static final String V1_API_OLD_PATH = "/api/";

    /**
     * @see javax.ws.rs.container.ContainerRequestFilter#filter(javax.ws.rs.container.ContainerRequestContext)
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {

        URI reqUri = requestContext.getUriInfo().getRequestUri();
        String uri = reqUri.getPath();

        if (uri.startsWith(V1_API_OLD_PATH)) {
            log.warn("Rewriting request to v1 API old path, "
                    + "it's recommended to update your clients to the new version and use the \"/apis/registry/v2\" api "
                    + "or at least update your clients configuration to use the new path for the v1 api \"/apis/registry/v1\"");

            String newUri = uri.replaceFirst(V1_API_OLD_PATH, "/apis/registry/v1/");

            log.debug("New uri {}", newUri);

            requestContext.setRequestUri(UriBuilder.fromUri(reqUri).replacePath(newUri).build());
        }

    }

}
