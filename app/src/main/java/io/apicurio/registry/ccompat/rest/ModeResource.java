/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.ccompat.rest;

import io.apicurio.registry.ccompat.dto.ModeDto;
import io.apicurio.registry.metrics.RestMetricsResponseFilteredNameBinding;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import static io.apicurio.registry.ccompat.rest.ContentTypes.*;

/**
 * Note:
 * <p/>
 * This <a href="https://docs.confluent.io/5.5.0/schema-registry/develop/api.html#free-up-artifactStore-space-in-the-registry-for-new-schemas">API specification</a> is owned by Confluent.
 *
 * We <b>DO NOT</b> support this endpoint. Fails with 404.
 *
 * @author Ales Justin
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@Path("/apis/ccompat/v6/mode")
@RestMetricsResponseFilteredNameBinding
@Consumes({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
@Produces({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
public interface ModeResource {

    // ----- Path: /mode -----

    @GET
    ModeDto getGlobalMode();


    @PUT
    ModeDto updateGlobalMode(
            @NotNull ModeDto request);
}
