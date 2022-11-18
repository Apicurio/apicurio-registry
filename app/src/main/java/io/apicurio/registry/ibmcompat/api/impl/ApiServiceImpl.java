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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;

import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.ibmcompat.api.ApiService;
import io.apicurio.registry.ibmcompat.model.EnabledModification;
import io.apicurio.registry.ibmcompat.model.NewSchema;
import io.apicurio.registry.ibmcompat.model.NewSchemaVersion;
import io.apicurio.registry.ibmcompat.model.Schema;
import io.apicurio.registry.ibmcompat.model.SchemaInfo;
import io.apicurio.registry.ibmcompat.model.SchemaListItem;
import io.apicurio.registry.ibmcompat.model.SchemaModificationPatch;
import io.apicurio.registry.ibmcompat.model.SchemaState;
import io.apicurio.registry.ibmcompat.model.SchemaSummary;
import io.apicurio.registry.ibmcompat.model.SchemaVersion;
import io.apicurio.registry.ibmcompat.model.StateModification;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.util.ArtifactIdGenerator;
import io.apicurio.registry.util.VersionUtil;

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

    private static final String SCHEMA_NAME_ADDITIONAL_PROPERTY = "ibmcompat-schema-name";
    private static final String SCHEMA_STATE_COMMENT_ADDITIONAL_PROPERTY = "ibmcompat-schema-state-comment";

    private List<SchemaVersion> getSchemaVersions(String schemaid) {
        return storage.getArtifactVersions(null, schemaid)
                      .stream()
                      .map(versionid -> getSchemaVersionFromStorage(schemaid, versionid))
                      .filter(schemaVersion -> schemaVersion != null)
                      .collect(Collectors.toList());
    }

    private SchemaVersion getLatestSchemaVersion(String schemaid) {
        return getSchemaVersionFromStorage(schemaid, storage.getArtifact(null, schemaid).getVersion());
    }

    private SchemaVersion getSchemaVersionFromStorage(String schemaid, String versionid) {
        SchemaVersion schemaVersion = null;
        try {
            ArtifactVersionMetaDataDto avmdd = storage.getArtifactVersionMetaData(null, schemaid, versionid);
            schemaVersion = getSchemaVersion(avmdd.getVersionId(), avmdd.getVersion(), avmdd.getName(), avmdd.getCreatedOn(), avmdd.getState(), avmdd.getDescription());
        } catch (ArtifactNotFoundException e) {
            // If artifact version does not exist (which may occur due to race conditions), swallow
            // the exception here and return a null result, to be filtered out
        }
        return schemaVersion;
    }

    private SchemaVersion getSchemaVersion(int versionId, String version, String name, long createdOn, ArtifactState state, String description) {
        SchemaVersion schemaVersion = new SchemaVersion();
        schemaVersion.setId(versionId);
        schemaVersion.setDate(new Date(createdOn));
        schemaVersion.setName(name);
        schemaVersion.setEnabled(!ArtifactState.DISABLED.equals(state));
        SchemaState versionState = new SchemaState();
        if(ArtifactState.DEPRECATED.equals(state)) {
            versionState.setState(SchemaState.StateEnum.DEPRECATED);
        } else {
            versionState.setState(SchemaState.StateEnum.ACTIVE);
        }
        if(description != null) {
            versionState.setComment(description);
        }
        schemaVersion.setState(versionState);
        return schemaVersion;
    }

    private void populateSchemaSummary(String schemaid, SchemaSummary schemaSummary) {
        List<ArtifactState> versionStates = storage.getArtifactVersions(null, schemaid).stream()
            .map(version -> storage.getArtifactVersionMetaData(null, schemaid, version).getState())
            .collect(Collectors.toList());
        Map<String, String> properties = storage.getArtifactMetaData(null, schemaid).getProperties();

        schemaSummary.setId(schemaid);

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

        if (properties != null) {
            schemaSummary.setName(properties.getOrDefault(SCHEMA_NAME_ADDITIONAL_PROPERTY, schemaid));
            schemaState.setComment(properties.get(SCHEMA_STATE_COMMENT_ADDITIONAL_PROPERTY));
        } else {
            schemaSummary.setName(schemaid);
        }

    }

    private SchemaInfo getSchemaInfo(ArtifactMetaDataDto amdd) {
        SchemaInfo schemaInfo = new SchemaInfo();
        schemaInfo.setId(amdd.getId());
        schemaInfo.setEnabled(true);

        SchemaState schemaState = new SchemaState();
        schemaState.setState(SchemaState.StateEnum.ACTIVE);
        schemaInfo.setState(schemaState);

        Map<String, String> properties = amdd.getProperties();
        if (properties != null) {
            schemaInfo.setName(properties.getOrDefault(SCHEMA_NAME_ADDITIONAL_PROPERTY, amdd.getId()));
            schemaState.setComment(properties.get(SCHEMA_STATE_COMMENT_ADDITIONAL_PROPERTY));
        } else {
            schemaInfo.setName(amdd.getId());
        }

        return schemaInfo;
    }

    private void handleArtifactCreation(AsyncResponse response, String artifactId, ArtifactMetaDataDto amdd) {
        // Prepare the response
        SchemaInfo info = getSchemaInfo(amdd);
        List<SchemaVersion> schemaVersions = new ArrayList<>();
        info.setVersions(schemaVersions);

        try {
            schemaVersions.addAll(getSchemaVersions(artifactId));

            if (schemaVersions.isEmpty() || amdd.getVersionId() != schemaVersions.get(schemaVersions.size() - 1).getId()) {
                // Async artifactStore types may not yet be ready to call artifactStore.getArtifactVersionMetaData(),
                // so add the new version to the response
                schemaVersions.add(getSchemaVersion(amdd.getVersionId(), amdd.getVersion(), amdd.getName(), amdd.getCreatedOn(), amdd.getState(), null));
            } else {
                // Async artifactStore types may not have updated the version metadata yet, so set the version name in the response
                schemaVersions.get(schemaVersions.size() - 1).setName(amdd.getName());
            }
        } catch (ArtifactNotFoundException anfe) {
            // If this is a newly created schema, async artifactStore types may not yet be ready to call
            // artifactStore.getArtifactVersions(), so add the new version to the response
            schemaVersions.add(getSchemaVersion(amdd.getVersionId(), amdd.getVersion(), amdd.getName(), amdd.getCreatedOn(), amdd.getState(), null));
        } catch (Throwable throwable) {
            response.resume(throwable);
            return;
        }
        response.resume(Response.status(Response.Status.CREATED).entity(info).build());
    }

    @Nullable
    private ArtifactState getPatchedArtifactState(List<SchemaModificationPatch> schemaModificationPatches) {
        ArtifactState artifactState = null;
        boolean isEnabled = true;
        boolean isDeprecated = false;

        // Get the final enabled and deprecated states from the list of patches
        for (SchemaModificationPatch schemaModificationPatch : schemaModificationPatches) {
            if (schemaModificationPatch instanceof EnabledModification) {
                isEnabled = ((EnabledModification) schemaModificationPatch).getValue();
            } else if (schemaModificationPatch instanceof StateModification && !ArtifactState.DISABLED.equals(artifactState)) {
                isDeprecated = SchemaState.StateEnum.DEPRECATED.equals(((StateModification) schemaModificationPatch).getValue().getState());
            }
        }

        // Get the final artifact state - disabled overrides deprecated, which overrrides enabled.
        if (!isEnabled) {
            artifactState = ArtifactState.DISABLED;
        } else if (isDeprecated) {
            artifactState = ArtifactState.DEPRECATED;
        } else {
            artifactState = ArtifactState.ENABLED;
        }

        return artifactState;
    }

    @Nullable
    private String getPatchedArtifactStateComment(List<SchemaModificationPatch> schemaModificationPatches) {
        String comment = null;
        for (SchemaModificationPatch schemaModificationPatch : schemaModificationPatches) {
            if (schemaModificationPatch instanceof StateModification) {
                comment = ((StateModification) schemaModificationPatch).getValue().getComment();
            }
        }
        return comment;
    }

    private void setSchemaVersionState(ArtifactState artifactState, SchemaVersion version) {
        if(ArtifactState.DISABLED.equals(artifactState)) {
            version.setEnabled(false);
        } else if(ArtifactState.ENABLED.equals(artifactState)) {
            version.setEnabled(true);
        } else if(ArtifactState.DEPRECATED.equals(artifactState)) {
            version.getState().setState(SchemaState.StateEnum.DEPRECATED);
        }
    }

    private void updateArtifactVersionState(String schemaid, int versionnum, ArtifactState artifactState) {
        ArtifactVersionMetaDataDto avmdd = storage.getArtifactVersionMetaData(null, schemaid, VersionUtil.toString(versionnum));
        if (artifactState != null && !artifactState.equals(avmdd.getState())) {
            // Modify the artifact version state
            storage.updateArtifactState(null, schemaid, VersionUtil.toString(versionnum), artifactState);
        }
    }

    /**
     * Return the verfied schema contents as a JSON-escaped string.
     * @param response
     * @param content
     */
    private void handleVerifiedArtifact(AsyncResponse response, ContentHandle content) {
        String verifiedContent;
        try {
            verifiedContent = new ObjectMapper().writeValueAsString(content.content());
        } catch (JsonProcessingException e) {
            throw new BadRequestException(e);
        }
        response.resume(Response.ok().entity(verifiedContent).build());
    }

    @Override
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
                          // If artifact does not exist (which may occur due to race conditions), swallow
                          // the exception here and filter the null result out below.
                          return null;
                      }
                      return item;
                  })
                  .filter(item -> item != null)
                  .collect(Collectors.toList());
    }

    @Override
    public void apiSchemasPost(AsyncResponse response, NewSchema schema, boolean verify)
    throws ArtifactNotFoundException, ArtifactAlreadyExistsException {
        String schemaName = schema.getName();
        final String artifactId;
        if (schemaName == null) {
            artifactId = idGenerator.generate();
            schemaName = artifactId;
        } else {
            artifactId = ApiUtil.normalizeSchemaID(schemaName);
        }
        ContentHandle content = ContentHandle.create(schema.getDefinition());
        rulesService.applyRules(null, artifactId, ArtifactType.AVRO, content, RuleApplicationType.CREATE, Collections.emptyMap()); //FIXME:references handle artifact references
        if (verify) {
            handleVerifiedArtifact(response, content);
        } else {
            EditableArtifactMetaDataDto dto = new EditableArtifactMetaDataDto();
            dto.setName(schema.getVersion());

            Map<String, String> properties = new HashMap<>();
            properties.put(SCHEMA_NAME_ADDITIONAL_PROPERTY, schemaName);
            dto.setProperties(properties);
            try {
                ArtifactMetaDataDto amdd = storage.createArtifactWithMetadata(null, artifactId, null, ArtifactType.AVRO, content, dto, null);
                handleArtifactCreation(response, artifactId, amdd);
            } catch (Exception e) {
                response.resume(e);
            }
        }
    }

    @Override
    public Response apiSchemasSchemaidDelete(String schemaid)
    throws ArtifactNotFoundException {
        List<String> ids = storage.deleteArtifact(null, schemaid);
        return Response.status(Response.Status.NO_CONTENT).entity(ids).build();
    }

    @Override
    public SchemaInfo apiSchemasSchemaidGet(String schemaid)
    throws ArtifactNotFoundException {
        SchemaInfo info = new SchemaInfo();
        populateSchemaSummary(schemaid, info);
        info.setVersions(getSchemaVersions(schemaid));
        return info;
    }

    @Override
    public Response apiSchemasSchemaidPatch(String schemaid, List<SchemaModificationPatch> schemaModificationPatches)
    throws ArtifactNotFoundException {

        ArtifactState artifactState = getPatchedArtifactState(schemaModificationPatches);
        if(artifactState != null) {
            // Modify all the artifact version states
            for (String version : storage.getArtifactVersions(null, schemaid)) {
                try {
                    int versionnum = Integer.parseInt(version);
                    updateArtifactVersionState(schemaid, versionnum, artifactState);
                } catch (NumberFormatException e) {
                    // TODO what to do with an incompatible version #
                }
            }
        }
        String schemaStateComment = getPatchedArtifactStateComment(schemaModificationPatches);
        if (schemaStateComment != null) {
            updateStateCommentInArtifactMetadata(schemaid, schemaStateComment);
        }

        // Return the updated schema info
        SchemaInfo info = new SchemaInfo();
        populateSchemaSummary(schemaid, info);
        info.setVersions(getSchemaVersions(schemaid));

        // updateArtifactState call may be async, so also set the version state in the response
        if(ArtifactState.DISABLED.equals(artifactState)) {
            info.setEnabled(false);
        } else if(ArtifactState.ENABLED.equals(artifactState)) {
            info.setEnabled(true);
        } else if(ArtifactState.DEPRECATED.equals(artifactState)) {
            info.getState().setState(SchemaState.StateEnum.DEPRECATED);

            if (schemaStateComment != null) {
                info.getState().setComment(schemaStateComment);
            }
            // Note: Can't be deprecated and disabled at the same time - Apicurio Registry has a single state for this.
            info.setEnabled(true);
        }
        for(SchemaVersion version: info.getVersions()) {
            setSchemaVersionState(artifactState, version);
        }

        return Response.ok().entity(info).build();
    }

    private void updateStateCommentInArtifactMetadata(String schemaid, String schemaStateComment) {
        ArtifactMetaDataDto amdd = storage.getArtifactMetaData(null, schemaid);
        Map<String, String> properties = amdd.getProperties();
        if(properties == null) {
            properties = new HashMap<>();
        }
        properties.put(SCHEMA_STATE_COMMENT_ADDITIONAL_PROPERTY, schemaStateComment);
        EditableArtifactMetaDataDto dto = EditableArtifactMetaDataDto.builder()
            .name(amdd.getName())
            .description(amdd.getDescription())
            .labels(amdd.getLabels())
            .properties(properties)
            .build();
        storage.updateArtifactMetaData(null, schemaid, dto);
    }

    @Override
    public void apiSchemasSchemaidVersionsPost(AsyncResponse response, String schemaid, NewSchemaVersion newSchemaVersion, boolean verify)
    throws ArtifactNotFoundException, ArtifactAlreadyExistsException {
        ContentHandle body = ContentHandle.create(newSchemaVersion.getDefinition());
        rulesService.applyRules(null, schemaid, ArtifactType.AVRO, body, RuleApplicationType.UPDATE, Collections.emptyMap()); //FIXME:references handle artifact references
        if (verify) {
            handleVerifiedArtifact(response, body);
        } else {
            EditableArtifactMetaDataDto dto = new EditableArtifactMetaDataDto();
            dto.setName(newSchemaVersion.getVersion());
            try {
                ArtifactMetaDataDto amdd = storage.updateArtifactWithMetadata(null, schemaid, null, ArtifactType.AVRO, body, dto, null);
                handleArtifactCreation(response, schemaid, amdd);
            } catch (Exception e) {
                response.resume(e);
            }
        }
    }

    @Override
    public Response apiSchemasSchemaidVersionsVersionnumDelete(String schemaid, int versionnum)
    throws ArtifactNotFoundException {
        storage.deleteArtifactVersion(null, schemaid, VersionUtil.toString(versionnum));
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @Override
    public Schema apiSchemasSchemaidVersionsVersionnumGet(String schemaid, int versionnum)
    throws ArtifactNotFoundException {
        Schema schema = new Schema();
        populateSchemaSummary(schemaid, schema);
        StoredArtifactDto artifact = storage.getArtifactVersion(null, schemaid, VersionUtil.toString(versionnum));
        schema.setDefinition(artifact.getContent().content());
        schema.setVersion(getSchemaVersionFromStorage(schemaid, artifact.getVersion()));
        return schema;
    }

    @Override
    public Response apiSchemasSchemaidVersionsVersionnumPatch(String schemaid, int versionnum, List<SchemaModificationPatch> schemaModificationPatches)
    throws ArtifactNotFoundException {

        ArtifactState artifactState = getPatchedArtifactState(schemaModificationPatches);
        updateArtifactVersionState(schemaid, versionnum, artifactState);

        String schemaVersionStateComment = getPatchedArtifactStateComment(schemaModificationPatches);
        if (schemaVersionStateComment != null) {
            ArtifactVersionMetaDataDto avmdd = storage.getArtifactVersionMetaData(null, schemaid, VersionUtil.toString(versionnum));
            EditableArtifactMetaDataDto dto = EditableArtifactMetaDataDto.builder()
                .name(avmdd.getName())
                .description(schemaVersionStateComment)
                .build();
            storage.updateArtifactVersionMetaData(null, schemaid, VersionUtil.toString(versionnum), dto);
        }

        // Return the updated schema info
        SchemaInfo info = new SchemaInfo();
        populateSchemaSummary(schemaid, info);
        info.setVersions(getSchemaVersions(schemaid));

        // updateArtifactState call may be async, so also set the version state in the response
        SchemaVersion schemaVersion = info.getVersions()
                .stream()
                .filter(version -> versionnum == version.getId())
                .findFirst()
                .orElseThrow(() -> new VersionNotFoundException(null, schemaid, VersionUtil.toString(versionnum)));
        setSchemaVersionState(artifactState, schemaVersion);

        return Response.ok().entity(info).build();
    }
}
