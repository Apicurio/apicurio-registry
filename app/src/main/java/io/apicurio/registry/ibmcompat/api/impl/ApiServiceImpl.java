package io.apicurio.registry.ibmcompat.api.impl;

import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.ibmcompat.api.ApiService;
import io.apicurio.registry.ibmcompat.model.AnyOfStateModificationEnabledModification;
import io.apicurio.registry.ibmcompat.model.NewSchema;
import io.apicurio.registry.ibmcompat.model.NewSchemaVersion;
import io.apicurio.registry.ibmcompat.model.Schema;
import io.apicurio.registry.ibmcompat.model.SchemaInfo;
import io.apicurio.registry.ibmcompat.model.SchemaListItem;
import io.apicurio.registry.ibmcompat.model.SchemaVersion;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.util.ArtifactIdGenerator;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;

/**
 * @author Ales Justin
 */
@ApplicationScoped
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
@Logged
public class ApiServiceImpl implements ApiService {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RulesService rulesService;

    @Inject
    ArtifactIdGenerator idGenerator;

    private List<SchemaVersion> getSchemaVersions(String schemaid) {
        return storage.getArtifactVersions(schemaid)
                      .stream()
                      .map(l -> {
                          SchemaVersion sv = new SchemaVersion();
                          sv.setId(l.intValue());
                          sv.setEnabled(true);
                          return sv;
                      }).collect(Collectors.toList());
    }

    public List<SchemaListItem> apiSchemasGet(int page, int perPage)
    throws ArtifactNotFoundException {
        // best guess ... order set, and then limit things via stream
        Set<String> ids = new TreeSet<>(storage.getArtifactIds());
        return ids.stream()
                  .skip(page * perPage)
                  .limit(perPage)
                  .map(id -> {
                      SchemaListItem item = new SchemaListItem();
                      try {
                          StoredArtifact artifact = storage.getArtifact(id);
                          item.setId(id);
                          item.setEnabled(true);
                          SchemaVersion version = new SchemaVersion();
                          version.setId(artifact.getVersion().intValue()); // TODO not safe!
                          item.setLatest(version);
                      } catch (ArtifactNotFoundException e) {
                          // we can have deleted artifact ...
                      }
                      return item;
                  })
                  .filter(SchemaListItem::isEnabled)
                  .collect(Collectors.toList());
    }

    public void apiSchemasPost(AsyncResponse response, NewSchema schema, boolean verify)
    throws ArtifactNotFoundException {
        String artifactId = schema.getName();
        if (artifactId == null) {
            artifactId = idGenerator.generate();
        }

        ContentHandle content = ContentHandle.create(schema.getDefinition());

        if (verify) {
            rulesService.applyRules(artifactId, ArtifactType.AVRO, content, RuleApplicationType.CREATE);
            response.resume(Response.ok().entity(content).build());
        } else {
            CompletionStage<ArtifactMetaDataDto> csArtifact = storage.createArtifact(artifactId, ArtifactType.AVRO, content);
            csArtifact.whenComplete((amdd, t) -> {
                if (t != null) {
                    response.resume(t);
                } else {
                    SchemaInfo info = new SchemaInfo();
                    info.setId(amdd.getId());
                    info.setEnabled(true);
                    info.setVersions(getSchemaVersions(amdd.getId()));
                    response.resume(Response.status(Response.Status.CREATED).entity(info).build());
                }
            });
        }
    }

    public Response apiSchemasSchemaidDelete(String schemaid)
    throws ArtifactNotFoundException {
        SortedSet<Long> ids = storage.deleteArtifact(schemaid);
        return Response.status(Response.Status.NO_CONTENT).entity(ids).build();
    }

    public SchemaInfo apiSchemasSchemaidGet(String schemaid)
    throws ArtifactNotFoundException {
        storage.getArtifact(schemaid);
        SchemaInfo info = new SchemaInfo();
        info.setId(schemaid);
        info.setEnabled(true);
        info.setVersions(getSchemaVersions(schemaid));
        return info;
    }

    public Response apiSchemasSchemaidPatch(String schemaid, List<AnyOfStateModificationEnabledModification> anyOfStateModificationEnabledModification)
    throws ArtifactNotFoundException {
        // do some magic!
        return Response.ok().entity("OK").build();
    }

    public void apiSchemasSchemaidVersionsPost(AsyncResponse response, String schemaid, NewSchemaVersion schema, boolean verify)
    throws ArtifactNotFoundException {
        ContentHandle body = ContentHandle.create(schema.getDefinition());
        if (verify) {
            rulesService.applyRules(schemaid, ArtifactType.AVRO, body, RuleApplicationType.UPDATE);
            response.resume(Response.ok().entity(body).build());
        } else {
            CompletionStage<ArtifactMetaDataDto> csArtifact = storage.updateArtifact(schemaid, ArtifactType.AVRO, body);
            csArtifact.whenComplete((amdd, t) -> {
                if (t != null) {
                    response.resume(t);
                } else {
                    SchemaInfo info = new SchemaInfo();
                    info.setId(amdd.getId());
                    info.setEnabled(true);
                    info.setVersions(getSchemaVersions(amdd.getId()));
                    response.resume(Response.status(Response.Status.CREATED).entity(info).build());
                }
            });
        }
    }

    public Response apiSchemasSchemaidVersionsVersionnumDelete(String schemaid, int versionnum)
    throws ArtifactNotFoundException {
        storage.deleteArtifactVersion(schemaid, versionnum);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    public Schema apiSchemasSchemaidVersionsVersionnumGet(String schemaid, int versionnum)
    throws ArtifactNotFoundException {
        StoredArtifact artifact = storage.getArtifactVersion(schemaid, versionnum);
        Schema schema = new Schema();
        schema.setId(schemaid);
        schema.setEnabled(true);
        schema.setDefinition(artifact.getContent().content());
        SchemaVersion version = new SchemaVersion();
        version.setId(artifact.getVersion().intValue()); // TODO not safe!
        schema.setVersion(version);
        return schema;
    }

    public Response apiSchemasSchemaidVersionsVersionnumPatch(String schemaid, int versionnum, List<AnyOfStateModificationEnabledModification> anyOfStateModificationEnabledModification)
    throws ArtifactNotFoundException {
        // do some magic!
        return Response.ok().entity("OK").build();
    }
}
