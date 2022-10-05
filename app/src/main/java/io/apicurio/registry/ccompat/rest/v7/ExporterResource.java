/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.ccompat.rest.v7;

import io.apicurio.registry.ccompat.dto.ExporterDto;
import io.apicurio.registry.ccompat.dto.ExporterStatus;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST;
import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_V1;
import static io.apicurio.registry.ccompat.rest.ContentTypes.JSON;
import static io.apicurio.registry.ccompat.rest.ContentTypes.OCTET_STREAM;

/**
 * Note:
 * <p/>
 * This <a href="https://docs.confluent.io/platform/7.2.1/schema-registry/develop/api.html#exporters">API specification</a> is owned by Confluent.
 * <p>
 * The exporters resource allows you to query the information or manipulate the lifecycle of schema exporters..
 *
 * @author Carles Arnal
 */
@Path("/apis/ccompat/v7/exporters")
@Consumes({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
@Produces({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
public interface ExporterResource {

    @GET
    List<String> getExporters() throws Exception;

    @POST
    String createExporter(ExporterDto exporter) throws Exception;

    @GET
    @Path("/{exporter}")
    String getExporter(@PathParam("exporter") String exporterName) throws Exception;

    @PUT
    @Path("/{exporter}")
    String updateExporter(@PathParam("exporter") String exporterName, ExporterDto exporter) throws Exception;

    @DELETE
    @Path("/{exporter}")
    String deleteExporter(@PathParam("exporter") String exporterName) throws Exception;

    @PUT
    @Path("/{exporter}/pause")
    String pauseExporter(@PathParam("exporter") String exporterName) throws Exception;

    @PUT
    @Path("/{exporter}/reset")
    String resetExporter(@PathParam("exporter") String exporterName) throws Exception;

    @PUT
    @Path("/{exporter}/resume")
    String resumeExporter(@PathParam("exporter") String exporterName) throws Exception;

    @PUT
    @Path("/{exporter}/config")
    String updateExporterConfig(@PathParam("exporter") String exporterName, Map<String, String> config) throws Exception;

    @GET
    @Path("/{exporter}/status")
    ExporterStatus getExporterStatus(@PathParam("exporter") String exporterName) throws Exception;

    @GET
    @Path("/{exporter}/config")
    Map<String, String> getExporterConfig(@PathParam("exporter") String exporterName) throws Exception;
}
