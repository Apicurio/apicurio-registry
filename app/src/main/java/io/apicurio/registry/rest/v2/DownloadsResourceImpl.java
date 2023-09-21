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

package io.apicurio.registry.rest.v2;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
@Path("/apis/registry/v2/downloads")
public class DownloadsResourceImpl {
    
    @Inject
    io.apicurio.registry.rest.v3.DownloadsResourceImpl v3Impl;

    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.None)
    @GET
    @Path("{downloadId}")
    @Produces("*/*")
    public Response download(@PathParam("downloadId") String downloadId) {
        return v3Impl.download(downloadId);
    }

    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.None)
    @GET
    @Path("{downloadId}/{fileName}")
    @Produces("*/*")
    public Response downloadAsFile(@PathParam("downloadId") String downloadId) {
        return v3Impl.downloadAsFile(downloadId);
    }
}
