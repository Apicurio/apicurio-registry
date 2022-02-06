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

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.v2.shared.DataExporter;
import io.apicurio.registry.storage.DownloadNotFoundException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.dto.DownloadContextType;
import io.apicurio.registry.types.Current;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
@Path("/apis/registry/v2/downloads")
public class DownloadsResourceImpl {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    DataExporter exporter;

    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.None)
    @GET
    @Path("{downloadId}")
    @Produces("*/*")
    public Response download(@PathParam("downloadId") String downloadId) {
        DownloadContextDto downloadContext = storage.consumeDownload(downloadId);
        if (downloadContext.getType() == DownloadContextType.EXPORT) {
            return exporter.exportData();
        }

        // TODO support other types of downloads (e.g. download content by contentId)

        throw new DownloadNotFoundException();
    }

    /**
     * A duplicate version of the above that will allow a filename to be added
     * for download purposes.  So e.g. /apis/registry/v2/downloads/ABCD-1234 can
     * be aliased as /apis/registry/v2/downloads/ABCD-1234/export.zip and work
     * the same way.  But when saving from a browser, the filename should be
     * useful.
     * @param downloadId
     * @return
     */
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.None)
    @GET
    @Path("{downloadId}/{fileName}")
    @Produces("*/*")
    public Response downloadAsFile(@PathParam("downloadId") String downloadId) {
        return this.download(downloadId);
    }

}
