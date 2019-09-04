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

package io.apicurio.registry.ccompat.rest;

import io.apicurio.registry.ccompat.dto.CompatibilityCheckResponse;
import io.apicurio.registry.ccompat.dto.RegisterSchemaRequest;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;

/**
 * @author Ales Justin
 */
@Path("/confluent/compatibility")
@Consumes({RestConstants.JSON, RestConstants.SR})
@Produces({RestConstants.JSON, RestConstants.SR})
public class CompatibilityResource extends AbstractResource {

    @Inject
    RulesService rules;

    @POST
    @Path("/subjects/{subject}/versions/{version}")
    public void testCompatabilityBySubjectName(
        @Suspended AsyncResponse response,
        @HeaderParam("Content-Type") String contentType,
        @HeaderParam("Accept") String accept,
        @PathParam("subject") String subject,
        @PathParam("version") String version,
        @NotNull RegisterSchemaRequest request) throws Exception {

        // TODO - headers, level?
        boolean isCompatible = true;
        try {
            rules.applyRule(subject, ArtifactType.AVRO, request.getSchema(), RuleType.COMPATIBILITY, 
                    CompatibilityLevel.BACKWARD_TRANSITIVE.name(), RuleApplicationType.UPDATE);
        } catch (RuleViolationException e) {
            isCompatible = false;
        }
        CompatibilityCheckResponse result = new CompatibilityCheckResponse();
        result.setIsCompatible(isCompatible);
        response.resume(result);
    }
}
