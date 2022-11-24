/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.cncf.schemaregistry.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.rest.error.ConflictException;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.cncf.schemaregistry.SchemagroupsResource;
import io.apicurio.registry.cncf.schemaregistry.beans.SchemaGroup;
import io.apicurio.registry.cncf.schemaregistry.beans.SchemaId;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.GroupAlreadyExistsException;
import io.apicurio.registry.storage.GroupNotFoundException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.util.VersionUtil;
import io.quarkus.security.identity.SecurityIdentity;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.apicurio.registry.cncf.schemaregistry.impl.CNCFApiUtil.dtoToSchemaGroup;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SchemagroupsResourceImpl implements SchemagroupsResource {

    private static final Integer GET_GROUPS_LIMIT = 1000;
    private static final String PROP_CONTENT_TYPE = "x-content-type";

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RulesService rulesService;

    @Context
    HttpServletRequest request;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Read)
    public List<String> getGroups() {
        return storage.getGroupIds(GET_GROUPS_LIMIT);
    }

    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Read)
    public SchemaGroup getGroup(String groupId) {
        GroupMetaDataDto group = storage.getGroupMetaData(groupId);
        return dtoToSchemaGroup(group);
    }

    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Write)
    public void createGroup(String groupId, SchemaGroup data) {
        //createdOn and modifiedOn are set by the storage
        GroupMetaDataDto.GroupMetaDataDtoBuilder group = GroupMetaDataDto.builder()
                .groupId(groupId)
                .description(data.getDescription())
                .artifactsType(data.getFormat())
                .properties(data.getGroupProperties());

        String user = securityIdentity.getPrincipal().getName();

        try {
            group.createdBy(user)
                .createdOn(new Date().getTime());

            storage.createGroup(group.build());
        } catch (GroupAlreadyExistsException e) {
            GroupMetaDataDto existing = storage.getGroupMetaData(groupId);

            group.createdBy(existing.getCreatedBy())
                .createdOn(existing.getCreatedOn())
                .modifiedBy(user)
                .modifiedOn(new Date().getTime());

            storage.updateGroupMetaData(group.build());
        }
    }

    @Override
    @Authorized(style=AuthorizedStyle.GroupOnly, level=AuthorizedLevel.Write)
    public void deleteGroup(String groupId) {
        storage.deleteGroup(groupId);
    }

    @Override
    @Authorized(style=AuthorizedStyle.GroupOnly, level=AuthorizedLevel.Read)
    public List<String> getSchemasByGroup(String groupId) {
        verifyGroupExists(groupId);
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroup(groupId));

        ArtifactSearchResultsDto resultsDto = storage.searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 1000);

        return resultsDto.getArtifacts()
                .stream()
                .map(dto -> dto.getId())
                .collect(Collectors.toList());
    }

    @Override
    @Authorized(style=AuthorizedStyle.GroupOnly, level=AuthorizedLevel.Write)
    public void deleteSchemasByGroup(String groupId) {
        verifyGroupExists(groupId);
        storage.deleteArtifacts(groupId);
    }

    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Read)
    public Response getLatestSchema(String groupId, String schemaId) {
        verifyGroupExists(groupId);
        StoredArtifactDto artifact = storage.getArtifact(groupId, schemaId);

        ArtifactMetaDataDto metadata = storage.getArtifactMetaData(groupId, schemaId);
        String contentType = metadata.getProperties().get(PROP_CONTENT_TYPE);

        return Response.ok(artifact.getContent(), contentType).build();
    }

    //TODO spec says: If schema with identical content already exists, existing schema's ID is returned. Our storage API does not allow to know if some content belongs to any other artifactId
    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Write)
    public SchemaId createSchema(String groupId, String schemaId, InputStream data) {

        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException("Error: Empty content");
        }

        try {
            verifyGroupExists(groupId);
        } catch (GroupNotFoundException e) {
            try {
                storage.createGroup(GroupMetaDataDto.builder()
                                        .groupId(groupId)
                                        .build());
            } catch (GroupAlreadyExistsException a) {
                //ignored
            }
        }

        // Check to see if this content is already registered - return the ID of that content
        // if it exists.  If not, then register the new content.
        try {
            storage.getArtifactVersionMetaData(groupId, schemaId, false, content);
            SchemaId id = new SchemaId();
            id.setId(schemaId);
            return id;
        } catch (ArtifactNotFoundException nfe) {
            // This is OK - when it happens just move on and create
        }

        String artifactType = ArtifactTypeUtil.determineArtifactType(content, null, request.getContentType(), factory.getAllArtifactTypes());

        //spec says: The ´Content-Type´ for the payload MUST be preserved by the registry and returned when the schema is requested, independent of the format identifier.
        EditableArtifactMetaDataDto metadata = new EditableArtifactMetaDataDto();
        metadata.setProperties(Map.of(PROP_CONTENT_TYPE, request.getContentType()));

        ArtifactMetaDataDto res;
        try {
            if (!artifactExists(groupId, schemaId)) {
                rulesService.applyRules(groupId, schemaId, artifactType, content, RuleApplicationType.CREATE, Collections.emptyMap()); //FIXME:references handle artifact references
                res = storage.createArtifactWithMetadata(groupId, schemaId, null, artifactType, content, metadata, null);
            } else {
                rulesService.applyRules(groupId, schemaId, artifactType, content, RuleApplicationType.UPDATE, Collections.emptyMap()); //FIXME:references handle artifact references
                res = storage.updateArtifactWithMetadata(groupId, schemaId, null, artifactType, content, metadata, null);
            }
        } catch (RuleViolationException ex) {
            if (ex.getRuleType() == RuleType.VALIDITY) {
                throw new UnprocessableEntityException(ex.getMessage(), ex);
            } else {
                throw new ConflictException(ex.getMessage(), ex);
            }
        }

        String artifactId = res.getId();
        SchemaId id = new SchemaId();
        id.setId(artifactId);
        return id;
    }

    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Write)
    public void deleteSchema(String groupId, String schemaId) {
        verifyGroupExists(groupId);
        storage.deleteArtifact(groupId, schemaId);
    }

    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Read)
    public List<Integer> getSchemaVersions(String groupId, String schemaId) {
        verifyGroupExists(groupId);
        return storage.getArtifactVersions(groupId, schemaId).stream()
                .map(v -> Long.valueOf(v).intValue())
                .collect(Collectors.toList());
    }

    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Read)
    public Response getSchemaVersion(String groupId, String schemaId, Integer versionNumber) {
        verifyGroupExists(groupId);
        StoredArtifactDto artifact = storage.getArtifactVersion(groupId, schemaId, VersionUtil.toString(versionNumber));

        ArtifactVersionMetaDataDto metadata = storage.getArtifactVersionMetaData(groupId, schemaId, VersionUtil.toString(versionNumber));
        String contentType = metadata.getProperties().get(PROP_CONTENT_TYPE);

        return Response.ok(artifact.getContent(), contentType).build();
    }

    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Write)
    public void deleteSchemaVersion(String groupId, String schemaId, Integer versionNumber) {
        verifyGroupExists(groupId);
        storage.deleteArtifactVersion(groupId, schemaId, VersionUtil.toString(versionNumber));
    }

    private boolean artifactExists(String groupId, String schemaId) {
        try {
            storage.getArtifactMetaData(groupId, schemaId);
            return true;
        } catch (ArtifactNotFoundException ignored) {
            return false;
        }
    }

    private void verifyGroupExists(String groupId) {
        // this will throw GroupNotFoundException if the group does not exist
        storage.getGroupMetaData(groupId);
    }

}
