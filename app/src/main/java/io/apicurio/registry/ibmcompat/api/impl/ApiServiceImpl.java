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
package io.apicurio.registry.ibmcompat.api.impl;

import io.apicurio.registry.ibmcompat.model.SchemaState;
import io.apicurio.registry.ibmcompat.model.SchemaSummary;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.types.ArtifactState;
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

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
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
                      .map(versionid -> getSchemaVersion(schemaid, versionid))
                      .filter(schemaVersion -> schemaVersion != null)
                      .collect(Collectors.toList());
    }

    private SchemaVersion getLatestSchemaVersion(String schemaid) {
        return getSchemaVersion(schemaid, storage.getArtifact(schemaid).getVersion());
    }

    private SchemaVersion getSchemaVersion(String schemaid, Long versionid) {
        SchemaVersion schemaVersion = new SchemaVersion();
        try {
            ArtifactVersionMetaDataDto artifactMetaData = storage.getArtifactVersionMetaData(schemaid, versionid);
            schemaVersion.setId(versionid.intValue()); // TODO not safe!
            schemaVersion.setDate(new Date(artifactMetaData.getCreatedOn()));
            schemaVersion.setName(artifactMetaData.getName());
            schemaVersion.setEnabled(!ArtifactState.DISABLED.equals(artifactMetaData.getState()));
            SchemaState versionState = new SchemaState();
            if(ArtifactState.DEPRECATED.equals(artifactMetaData.getState())) {
                versionState.setState(SchemaState.StateEnum.DEPRECATED);
            } else {
                versionState.setState(SchemaState.StateEnum.ACTIVE);
            }
            schemaVersion.setState(versionState);
        } catch (ArtifactNotFoundException e) {
            return null;
        }
        return schemaVersion;
    }

    private void populateSchemaSummary(String schemaid, SchemaSummary schemaSummary) {
        List<ArtifactState> versionStates = storage.getArtifactVersions(schemaid).stream()
            .map(version -> storage.getArtifactVersionMetaData(schemaid, version).getState())
            .collect(Collectors.toList());

        schemaSummary.setId(schemaid);
        schemaSummary.setName(schemaid); // TODO - add mechanism to store an artifact-level name

        // The schema is disabled if all versions are disabled
        boolean isSchemaDisabled = versionStates.stream().allMatch(versionState -> ArtifactState.DISABLED.equals(versionState));
        schemaSummary.setEnabled(!isSchemaDisabled);

        // The schema is deprecated if all versions are deprecated
        boolean isSchemaDeprecated = versionStates.stream().allMatch(versionState -> ArtifactState.DEPRECATED.equals(versionState));
        SchemaState schemaState = new SchemaState();
        if(isSchemaDeprecated) {
            schemaState.setState(SchemaState.StateEnum.DEPRECATED);
        } else {
            schemaState.setState(SchemaState.StateEnum.ACTIVE);
        }
        schemaSummary.setState(schemaState);
    }

    private void handleArtifactCreation(AsyncResponse response, String artifactId, String versionName, Throwable t) {
        if (t != null) {
            response.resume(t);
            return;
        }

        if (!doesArtifactExist(artifactId)) {
            // Some storage (such as asyncmem) needs a wait before the artifact is available for updating
            try {
                waitForArtifactCreation(artifactId);
            } catch (ArtifactNotFoundException e) {
                response.resume(e);
                return;
            }
        }

        try {
            // Set the artifact name from the version name
            EditableArtifactMetaDataDto dto = new EditableArtifactMetaDataDto();
            dto.setName(versionName);
            // TODO: On Kafka topic storage, updateArtifactMetaData does not return. Help needed here.
            // storage.updateArtifactMetaData(artifactId, dto);

            // Prepare the response
            SchemaInfo info = new SchemaInfo();
            populateSchemaSummary(artifactId, info);
            info.setVersions(getSchemaVersions(artifactId));

            // updateArtifactMetaData call may be async, so also set the version name in the response
            List<SchemaVersion> versions = info.getVersions();
            versions.get(versions.size() - 1).setName(versionName);

            response.resume(Response.status(Response.Status.CREATED).entity(info).build());
        } catch (Throwable throwable) {
            response.resume(throwable);
        }
    }

    private void waitForArtifactCreation(String artifactId) throws ArtifactNotFoundException {
        for (int i = 0; i < 5 && !doesArtifactExist(artifactId); i++) {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {}
        }
        storage.getArtifact(artifactId);
    }

    private boolean doesArtifactExist(String artifactId) {
        try {
            storage.getArtifact(artifactId);
            return true;
        } catch (ArtifactNotFoundException ignored) {
            return false;
        }
    }

    public List<SchemaListItem> apiSchemasGet(int page, int perPage)
    throws ArtifactNotFoundException {
        // best guess ... order set, and then limit things via stream
        Set<String> ids = new TreeSet<>(storage.getArtifactIds(null));
        return ids.stream()
                  .skip(page * perPage)
                  .limit(perPage)
                  .map(id -> {
                      SchemaListItem item = new SchemaListItem();
                      try {
                          populateSchemaSummary(id, item);
                          item.setLatest(getLatestSchemaVersion(id));
                      } catch (ArtifactNotFoundException e) {
                          return null;
                      }
                      return item;
                  })
                  .filter(item -> item != null)
                  .collect(Collectors.toList());
    }

    public void apiSchemasPost(AsyncResponse response, NewSchema schema, boolean verify)
    throws ArtifactNotFoundException, ArtifactAlreadyExistsException {
        String schemaName = schema.getName();
        final String artifactId;
        if (schemaName == null) {
            artifactId = idGenerator.generate();
        } else {
            artifactId = schemaName.toLowerCase();
        }
        ContentHandle content = ContentHandle.create(schema.getDefinition());

        if (verify) {
            rulesService.applyRules(artifactId, ArtifactType.AVRO, content, RuleApplicationType.CREATE);
            response.resume(Response.ok().entity(content).build());
        } else {
            storage.createArtifact(artifactId, ArtifactType.AVRO, content)
                    .whenComplete((amdd, t) -> handleArtifactCreation(response, artifactId, schema.getVersion(), t));
        }
    }

    public Response apiSchemasSchemaidDelete(String schemaid)
    throws ArtifactNotFoundException {
        SortedSet<Long> ids = storage.deleteArtifact(schemaid);
        return Response.status(Response.Status.NO_CONTENT).entity(ids).build();
    }

    public SchemaInfo apiSchemasSchemaidGet(String schemaid)
    throws ArtifactNotFoundException {
        SchemaInfo info = new SchemaInfo();
        populateSchemaSummary(schemaid, info);
        info.setVersions(getSchemaVersions(schemaid));
        return info;
    }

    public Response apiSchemasSchemaidPatch(String schemaid, List<AnyOfStateModificationEnabledModification> anyOfStateModificationEnabledModification)
    throws ArtifactNotFoundException {
        // do some magic!
        return Response.ok().entity("OK").build();
    }

    public void apiSchemasSchemaidVersionsPost(AsyncResponse response, String schemaid, NewSchemaVersion newSchemaVersion, boolean verify)
    throws ArtifactNotFoundException, ArtifactAlreadyExistsException {
        ContentHandle body = ContentHandle.create(newSchemaVersion.getDefinition());
        if (verify) {
            rulesService.applyRules(schemaid, ArtifactType.AVRO, body, RuleApplicationType.UPDATE);
            response.resume(Response.ok().entity(body).build());
        } else {
            storage.updateArtifact(schemaid, ArtifactType.AVRO, body)
                .whenComplete((amdd, t) -> handleArtifactCreation(response, schemaid, newSchemaVersion.getVersion(), t));
        }
    }

    public Response apiSchemasSchemaidVersionsVersionnumDelete(String schemaid, int versionnum)
    throws ArtifactNotFoundException {
        storage.deleteArtifactVersion(schemaid, versionnum);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    public Schema apiSchemasSchemaidVersionsVersionnumGet(String schemaid, int versionnum)
    throws ArtifactNotFoundException {
        Schema schema = new Schema();
        populateSchemaSummary(schemaid, schema);
        StoredArtifact artifact = storage.getArtifactVersion(schemaid, versionnum);
        schema.setDefinition(artifact.getContent().content());
        schema.setVersion(getSchemaVersion(schemaid, artifact.getVersion()));
        return schema;
    }

    public Response apiSchemasSchemaidVersionsVersionnumPatch(String schemaid, int versionnum, List<AnyOfStateModificationEnabledModification> anyOfStateModificationEnabledModification)
    throws ArtifactNotFoundException {
        // do some magic!
        return Response.ok().entity("OK").build();
    }
}
