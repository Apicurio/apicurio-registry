/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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
package io.apicurio.registry.ibmcompat.api;

import io.apicurio.registry.ibmcompat.model.NewSchema;
import io.apicurio.registry.ibmcompat.model.NewSchemaVersion;
import io.apicurio.registry.ibmcompat.model.Schema;
import io.apicurio.registry.ibmcompat.model.SchemaInfo;
import io.apicurio.registry.ibmcompat.model.SchemaListItem;
import io.apicurio.registry.ibmcompat.model.SchemaModificationPatch;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import java.util.List;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public interface ApiService {
    List<SchemaListItem>  apiSchemasGet(int page, int perPage)
    throws ArtifactNotFoundException;

    void apiSchemasPost(AsyncResponse response, NewSchema schema, boolean verify)
    throws ArtifactAlreadyExistsException;

    Response apiSchemasSchemaidDelete(String schemaid)
    throws ArtifactNotFoundException;

    SchemaInfo apiSchemasSchemaidGet(String schemaid)
    throws ArtifactNotFoundException;

    Response apiSchemasSchemaidPatch(String schemaid, List<SchemaModificationPatch> schemaModificationPatches)
    throws ArtifactNotFoundException;

    void apiSchemasSchemaidVersionsPost(AsyncResponse response, String schemaid, NewSchemaVersion schema, boolean verify)
    throws ArtifactNotFoundException, ArtifactAlreadyExistsException;

    Response apiSchemasSchemaidVersionsVersionnumDelete(String schemaid, int versionnum)
    throws ArtifactNotFoundException;

    Schema apiSchemasSchemaidVersionsVersionnumGet(String schemaid, int versionnum)
    throws ArtifactNotFoundException;

    Response apiSchemasSchemaidVersionsVersionnumPatch(String schemaid, int versionnum, List<SchemaModificationPatch> schemaModificationPatches)
    throws ArtifactNotFoundException;
}
