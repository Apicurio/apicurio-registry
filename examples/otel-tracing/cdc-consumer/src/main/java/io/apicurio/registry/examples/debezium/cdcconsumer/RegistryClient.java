/*
 * Copyright 2024 Red Hat Inc
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

package io.apicurio.registry.examples.debezium.cdcconsumer;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Map;

/**
 * REST client for Apicurio Registry v3 API.
 * This client is auto-instrumented with OpenTelemetry by Quarkus.
 */
@RegisterRestClient(configKey = "registry-api")
@Path("/apis/registry/v3")
public interface RegistryClient {

    /**
     * Get artifact content and reference by global ID.
     * In v3, this returns ArtifactReferenceWithContent including groupId, artifactId, version.
     * This demonstrates traced HTTP calls to the registry.
     */
    @GET
    @Path("/ids/globalIds/{globalId}")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> getArtifactMetaDataByGlobalId(@PathParam("globalId") long globalId);

    /**
     * Search for artifacts in the registry using v3 search API.
     */
    @GET
    @Path("/search/artifacts")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> searchArtifacts();
}
