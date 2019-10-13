package io.apicurio.registry.cibm.api;

import io.apicurio.registry.cibm.model.AnyOfStateModificationEnabledModification;
import io.apicurio.registry.cibm.model.NewSchema;
import io.apicurio.registry.cibm.model.NewSchemaVersion;
import io.apicurio.registry.cibm.model.Schema;
import io.apicurio.registry.cibm.model.SchemaInfo;
import io.apicurio.registry.cibm.model.SchemaListItem;
import io.apicurio.registry.storage.ArtifactNotFoundException;

import java.util.List;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public interface ApiService {
    List<SchemaListItem>  apiSchemasGet(int page, int perPage)
    throws ArtifactNotFoundException;

    void apiSchemasPost(AsyncResponse response, NewSchema schema, boolean verify)
    throws ArtifactNotFoundException;

    Response apiSchemasSchemaidDelete(String schemaid)
    throws ArtifactNotFoundException;

    SchemaInfo apiSchemasSchemaidGet(String schemaid)
    throws ArtifactNotFoundException;

    Response apiSchemasSchemaidPatch(String schemaid, List<AnyOfStateModificationEnabledModification> anyOfStateModificationEnabledModification)
    throws ArtifactNotFoundException;

    void apiSchemasSchemaidVersionsPost(AsyncResponse response, String schemaid, NewSchemaVersion schema, boolean verify)
    throws ArtifactNotFoundException;

    Response apiSchemasSchemaidVersionsVersionnumDelete(String schemaid, int versionnum)
    throws ArtifactNotFoundException;

    Schema apiSchemasSchemaidVersionsVersionnumGet(String schemaid, int versionnum)
    throws ArtifactNotFoundException;

    Response apiSchemasSchemaidVersionsVersionnumPatch(String schemaid, int versionnum, List<AnyOfStateModificationEnabledModification> anyOfStateModificationEnabledModification)
    throws ArtifactNotFoundException;
}
