package io.apicurio.registry.cncf.schemaregistry.impl;

import static io.apicurio.registry.cncf.schemaregistry.impl.CNCFApiUtil.dtoToSchemaGroup;

import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.rest.error.ConflictException;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.cncf.schemaregistry.SchemagroupsResource;
import io.apicurio.registry.cncf.schemaregistry.beans.SchemaGroup;
import io.apicurio.registry.cncf.schemaregistry.beans.SchemaId;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.ArtifactRetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableGroupMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.util.VersionUtil;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SchemagroupsResourceImpl implements SchemagroupsResource {

    private static final Integer GET_GROUPS_LIMIT = 1000;
    private static final String LABEL_CONTENT_TYPE = "x-content-type";

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
                .labels(data.getGroupProperties());

        String user = securityIdentity.getPrincipal().getName();

        try {
            group.owner(user)
                .createdOn(new Date().getTime());

            storage.createGroup(group.build());
        } catch (GroupAlreadyExistsException e) {
            EditableGroupMetaDataDto dto = EditableGroupMetaDataDto.builder()
                    .description(data.getDescription())
                    .labels(data.getGroupProperties())
                    .build();

            storage.updateGroupMetaData(groupId, dto);
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
                .map(dto -> dto.getArtifactId())
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
        
        GAV latestGAV = storage.getArtifactBranchTip(new GA(groupId, schemaId), BranchId.LATEST, ArtifactRetrievalBehavior.SKIP_DISABLED_LATEST);
        StoredArtifactVersionDto artifact = storage.getArtifactVersionContent(groupId, schemaId, latestGAV.getRawVersionId());

        ArtifactMetaDataDto metadata = storage.getArtifactMetaData(groupId, schemaId);
        String contentType = metadata.getLabels().get(LABEL_CONTENT_TYPE);

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
            storage.getArtifactVersionMetaDataByContent(groupId, schemaId, false, content, Collections.emptyList());
            SchemaId id = new SchemaId();
            id.setId(schemaId);
            return id;
        } catch (ArtifactNotFoundException nfe) {
            // This is OK - when it happens just move on and create
        }

        String artifactType = ArtifactTypeUtil.determineArtifactType(content, null, request.getContentType(), factory.getAllArtifactTypes());

        //spec says: The ´Content-Type´ for the payload MUST be preserved by the registry and returned when the schema is requested, independent of the format identifier.
        EditableArtifactMetaDataDto metadata = new EditableArtifactMetaDataDto();
        metadata.setLabels(Map.of(LABEL_CONTENT_TYPE, request.getContentType()));

        ArtifactVersionMetaDataDto res;
        try {
            if (!artifactExists(groupId, schemaId)) {
                rulesService.applyRules(groupId, schemaId, artifactType, content, RuleApplicationType.CREATE, Collections.emptyList(), Collections.emptyMap()); //FIXME:references handle artifact references
                res = storage.createArtifactWithMetadata(groupId, schemaId, null, artifactType, content, metadata, null);
            } else {
                rulesService.applyRules(groupId, schemaId, artifactType, content, RuleApplicationType.UPDATE, Collections.emptyList(), Collections.emptyMap()); //FIXME:references handle artifact references
                res = storage.createArtifactVersionWithMetadata(groupId, schemaId, null, artifactType, 
                        content, EditableVersionMetaDataDto.fromEditableArtifactMetaDataDto(metadata), null);
            }
        } catch (RuleViolationException ex) {
            if (ex.getRuleType() == RuleType.VALIDITY) {
                throw new UnprocessableEntityException(ex);
            } else {
                throw new ConflictException(ex);
            }
        }

        String artifactId = res.getArtifactId();
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
        StoredArtifactVersionDto artifact = storage.getArtifactVersionContent(groupId, schemaId, VersionUtil.toString(versionNumber));

        ArtifactVersionMetaDataDto metadata = storage.getArtifactVersionMetaData(groupId, schemaId, VersionUtil.toString(versionNumber));
        String contentType = metadata.getLabels().get(LABEL_CONTENT_TYPE);

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
            return storage.isArtifactExists(groupId, schemaId);
        } catch (ArtifactNotFoundException ignored) {
            return false;
        }
    }

    private void verifyGroupExists(String groupId) {
        // this will throw GroupNotFoundException if the group does not exist
        storage.getGroupMetaData(groupId);
    }

}
