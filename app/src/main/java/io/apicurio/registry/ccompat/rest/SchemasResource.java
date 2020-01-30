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

import io.apicurio.registry.ccompat.dto.SchemaString;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

/**
 * @author Ales Justin
 */
@Path("/ccompat/schemas")
@Consumes({RestConstants.JSON, RestConstants.SR})
@Produces({RestConstants.JSON, RestConstants.SR})
public class SchemasResource extends AbstractResource {

    @GET
    @Path("/ids/{id}")
    public SchemaString getSchema(@PathParam("id") Integer id) {
        String schema = facade.getSchema(id);
        if (schema == null) {
            Errors.schemaNotFound(id);
        }
        return new SchemaString(schema);
    }
}
