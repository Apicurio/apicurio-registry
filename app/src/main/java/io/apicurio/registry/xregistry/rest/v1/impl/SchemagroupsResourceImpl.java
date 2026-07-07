package io.apicurio.registry.xregistry.rest.v1.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableGroupMetaDataDto;
import io.apicurio.registry.storage.dto.EditableResourceMetaDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.ResourceMetaDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.xregistry.rest.v1.SchemagroupsResource;
import io.apicurio.registry.xregistry.rest.v1.beans.Meta;
import io.apicurio.registry.xregistry.rest.v1.beans.Schema;
import io.apicurio.registry.xregistry.rest.v1.beans.SchemaVersion;
import io.apicurio.registry.xregistry.rest.v1.beans.Schemagroup;
import io.apicurio.registry.xregistry.rest.v1.beans.Schemas;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SchemagroupsResourceImpl extends AbstractXRegistryResource
        implements SchemagroupsResource {

    private static final int DEFAULT_PAGE_SIZE = 20;

    // ===== Group operations =====

    @Override
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Read)
    public Schemagroup getSchemagroup(String groupid, String specversion, String inline,
            List<String> filter, Boolean doc) {
        GroupMetaDataDto dto = storage.getGroupMetaData(groupid);
        URI basePath = buildSelfUri("");
        return XRegistryApiConverter.toSchemagroup(dto, basePath);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public Schemagroup putSchemagroup(String groupid, String specversion, String inline,
            List<String> filter, Boolean doc, Long epoch, Schemagroup data) {
        URI basePath = buildSelfUri("");
        if (storage.isGroupExists(groupid)) {
            GroupMetaDataDto existing = storage.getGroupMetaData(groupid);
            if (epoch != null) {
                checkEpochConflict(epoch.intValue(), existing.getEpoch());
            }
            EditableGroupMetaDataDto editable =
                    XRegistryApiConverter.toEditableGroupMetaData(data);
            storage.updateGroupMetaData(groupid, editable);
        } else {
            GroupMetaDataDto newGroup = GroupMetaDataDto.builder()
                    .groupId(groupid)
                    .description(data.getDescription())
                    .build();
            storage.createGroup(newGroup);
        }
        GroupMetaDataDto result = storage.getGroupMetaData(groupid);
        return XRegistryApiConverter.toSchemagroup(result, basePath);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public void deleteSchemagroup(String groupid, String specversion, String inline,
            List<String> filter, Boolean doc, long epoch) {
        GroupMetaDataDto existing = storage.getGroupMetaData(groupid);
        checkEpochConflict((int) epoch, existing.getEpoch());
        storage.deleteGroup(groupid);
    }

    // ===== Schema listing =====

    @Override
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Read)
    public Response getSchemagroupSchemasAll(String groupid, String specversion,
            String inline, BigInteger skip, BigInteger top, List<String> filter,
            Boolean doc) {
        int offset = skip != null ? skip.intValue() : 0;
        int limit = top != null ? top.intValue() : DEFAULT_PAGE_SIZE;

        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroupId(groupid));

        ArtifactSearchResultsDto results = storage.searchArtifacts(
                filters, OrderBy.name, OrderDirection.asc, offset, limit, false);

        URI basePath = buildSelfUri("");
        Schemas schemasMap = new Schemas();
        for (SearchedArtifactDto dto : results.getArtifacts()) {
            Schema schema = XRegistryApiConverter.toSchema(dto, basePath);
            schemasMap.setAdditionalProperty(dto.getArtifactId(), schema);
        }
        return Response.ok(schemasMap, MediaType.APPLICATION_JSON).build();
    }

    // ===== Schema meta operations =====

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Meta getSchemagroupSchemaMeta(String groupid, String resourceid,
            String specversion, String inline, List<String> filter, Boolean doc,
            Long epoch) {
        ArtifactMetaDataDto amd = storage.getArtifactMetaData(groupid, resourceid);
        ResourceMetaDto rmd = storage.getResourceMeta(groupid, resourceid);
        URI basePath = buildSelfUri("");
        return XRegistryApiConverter.toMeta(rmd, amd, basePath);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public Meta putSchemagroupSchemaMeta(String groupid, String resourceid,
            String specversion, String inline, List<String> filter, Boolean doc,
            Long epoch, Meta data) {
        ArtifactMetaDataDto amd = storage.getArtifactMetaData(groupid, resourceid);
        ResourceMetaDto existingMeta = storage.getResourceMeta(groupid, resourceid);
        if (epoch != null) {
            checkEpochConflict(epoch.intValue(), existingMeta.getEpoch());
        }
        EditableResourceMetaDto editable = XRegistryApiConverter.toEditableResourceMeta(data);
        storage.updateResourceMeta(groupid, resourceid, editable);
        ResourceMetaDto updatedMeta = storage.getResourceMeta(groupid, resourceid);
        URI basePath = buildSelfUri("");
        return XRegistryApiConverter.toMeta(updatedMeta, amd, basePath);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public Meta patchSchemagroupSchemaMeta(String groupid, String resourceid,
            String specversion, String inline, List<String> filter, Boolean doc,
            Long epoch, Meta data) {
        ArtifactMetaDataDto amd = storage.getArtifactMetaData(groupid, resourceid);
        ResourceMetaDto existingMeta = storage.getResourceMeta(groupid, resourceid);
        if (epoch != null) {
            checkEpochConflict(epoch.intValue(), existingMeta.getEpoch());
        }
        // Merge: only update fields that are non-null in the patch
        EditableResourceMetaDto.EditableResourceMetaDtoBuilder builder =
                EditableResourceMetaDto.builder();
        builder.compatibility(data.getCompatibility() != null
                ? data.getCompatibility() : existingMeta.getCompatibility());
        builder.readonly(data.getReadonly() != null
                ? data.getReadonly() : existingMeta.getReadonly());
        builder.defaultVersionId(data.getDefaultversionid() != null
                ? data.getDefaultversionid() : existingMeta.getDefaultVersionId());
        builder.defaultVersionSticky(data.getDefaultversionsticky() != null
                ? data.getDefaultversionsticky() : existingMeta.getDefaultVersionSticky());
        if (data.getXref() != null) {
            builder.xref(data.getXref().toString());
        } else if (existingMeta.getXref() != null) {
            builder.xref(existingMeta.getXref());
        }
        storage.updateResourceMeta(groupid, resourceid, builder.build());
        ResourceMetaDto updatedMeta = storage.getResourceMeta(groupid, resourceid);
        URI basePath = buildSelfUri("");
        return XRegistryApiConverter.toMeta(updatedMeta, amd, basePath);
    }

    // ===== Schema details =====

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Schema getSchemagroupSchemaDetails(String groupid, String resourceid,
            String specversion, String inline, List<String> filter, Boolean doc) {
        ArtifactMetaDataDto amd = storage.getArtifactMetaData(groupid, resourceid);
        ResourceMetaDto rmd = storage.getResourceMeta(groupid, resourceid);
        URI basePath = buildSelfUri("");
        return XRegistryApiConverter.toSchema(amd, rmd, basePath);
    }

    // ===== Schema resource (content) operations =====

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Response getSchemagroupSchema(String groupid, String resourceid,
            String specversion, Boolean meta, Boolean noepoch,
            Boolean nodefaultversionid, Boolean nodefaultversionsticky,
            Boolean noreadonly, String setdefaultversionid, String inline,
            List<String> filter, Boolean doc) {
        // If meta=true, return metadata view instead of content
        if (Boolean.TRUE.equals(meta)) {
            ArtifactMetaDataDto amd = storage.getArtifactMetaData(groupid, resourceid);
            ResourceMetaDto rmd = storage.getResourceMeta(groupid, resourceid);
            URI basePath = buildSelfUri("");
            Schema schema = XRegistryApiConverter.toSchema(amd, rmd, basePath);
            return Response.ok(schema, MediaType.APPLICATION_JSON).build();
        }

        // Get the latest version content
        GAV latestGav = storage.getBranchTip(new GA(groupid, resourceid),
                BranchId.LATEST, RegistryStorage.RetrievalBehavior.SKIP_DISABLED_LATEST);
        StoredArtifactVersionDto versionContent = storage.getArtifactVersionContent(
                groupid, resourceid, latestGav.getRawVersionId());
        String contentType = versionContent.getContentType() != null
                ? versionContent.getContentType() : MediaType.APPLICATION_OCTET_STREAM;
        return Response.ok(versionContent.getContent().bytes(), contentType).build();
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public Schema putSchemagroupSchema(String groupid, String resourceid,
            String specversion, Boolean meta, Boolean noepoch,
            Boolean nodefaultversionid, Boolean nodefaultversionsticky,
            Boolean noreadonly, String setdefaultversionid, String inline,
            List<String> filter, Boolean doc, Long epoch, InputStream data) {
        byte[] content = readInputStream(data);
        return upsertSchemaFromContent(groupid, resourceid, epoch, content, null);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public Schema putSchemagroupSchema(String groupid, String resourceid,
            String specversion, Boolean meta, Boolean noepoch,
            Boolean nodefaultversionid, Boolean nodefaultversionsticky,
            Boolean noreadonly, String setdefaultversionid, String inline,
            List<String> filter, Boolean doc, Long epoch, Schema data) {
        return upsertSchemaFromBean(groupid, resourceid, epoch, data);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public Response postSchemagroupSchema(String groupid, String resourceid,
            String specversion, Boolean meta, Boolean noepoch,
            Boolean nodefaultversionid, Boolean nodefaultversionsticky,
            Boolean noreadonly, String setdefaultversionid, String inline,
            List<String> filter, Boolean doc, String resourceDescription,
            String resourceDocumentation, String resourceLabels, InputStream data) {
        byte[] content = readInputStream(data);
        Schema schema = createSchemaVersion(groupid, resourceid, content,
                resourceDescription);
        return Response.status(Response.Status.CREATED).entity(schema)
                .type(MediaType.APPLICATION_JSON).build();
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public Response postSchemagroupSchema(String groupid, String resourceid,
            String specversion, Boolean meta, Boolean noepoch,
            Boolean nodefaultversionid, Boolean nodefaultversionsticky,
            Boolean noreadonly, String setdefaultversionid, String inline,
            List<String> filter, Boolean doc, String resourceDescription,
            String resourceDocumentation, String resourceLabels, Schema data) {
        Schema schema = createSchemaVersionFromBean(groupid, resourceid, data,
                resourceDescription);
        return Response.status(Response.Status.CREATED).entity(schema)
                .type(MediaType.APPLICATION_JSON).build();
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteSchemagroupSchema(String groupid, String resourceid,
            String specversion, Boolean meta, Boolean noepoch,
            Boolean nodefaultversionid, Boolean nodefaultversionsticky,
            Boolean noreadonly, String setdefaultversionid, String inline,
            List<String> filter, Boolean doc, long epoch) {
        ArtifactMetaDataDto amd = storage.getArtifactMetaData(groupid, resourceid);
        checkEpochConflict((int) epoch, amd.getEpoch());
        storage.deleteArtifact(groupid, resourceid);
    }

    // ===== Version operations =====

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Schema getSchemagroupSchemaVersionsAll(String groupid, String resourceid,
            String specversion, String inline, List<String> filter, Boolean doc,
            BigInteger skip, BigInteger top) {
        URI basePath = buildSelfUri("");
        ArtifactMetaDataDto amd = storage.getArtifactMetaData(groupid, resourceid);
        ResourceMetaDto rmd = storage.getResourceMeta(groupid, resourceid);
        // Return the Schema bean with resource-level metadata.
        // Version details are returned inline when the xRegistry inline parameter is set.
        return XRegistryApiConverter.toSchema(amd, rmd, basePath);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public Response postSchemagroupSchemaVersions(String groupid, String resourceid,
            String specversion, String inline, List<String> filter, Boolean doc,
            String setdefaultversionid, InputStream data) {
        byte[] content = readInputStream(data);
        ContentWrapperDto contentWrapper = ContentWrapperDto.builder()
                .content(ContentHandle.create(content))
                .contentType(MediaType.APPLICATION_JSON)
                .references(Collections.emptyList())
                .build();

        EditableVersionMetaDataDto vmd = EditableVersionMetaDataDto.builder().build();
        storage.createArtifactVersion(groupid, resourceid, null,
                ArtifactType.JSON, contentWrapper, vmd, Collections.emptyList(),
                false, false, null);

        ArtifactMetaDataDto amd = storage.getArtifactMetaData(groupid, resourceid);
        ResourceMetaDto rmd = storage.getResourceMeta(groupid, resourceid);
        URI basePath = buildSelfUri("");
        Schema schema = XRegistryApiConverter.toSchema(amd, rmd, basePath);
        return Response.status(Response.Status.CREATED).entity(schema)
                .type(MediaType.APPLICATION_JSON).build();
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Response getSchemagroupSchemaVersion(String groupid, String resourceid,
            String versionid, Boolean meta, String specversion, String inline,
            List<String> filter, Boolean doc) {
        if (Boolean.TRUE.equals(meta)) {
            io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto vmd =
                    storage.getArtifactVersionMetaData(groupid, resourceid, versionid);
            URI basePath = buildSelfUri("");
            SchemaVersion sv = XRegistryApiConverter.toSchemaVersion(vmd, basePath);
            return Response.ok(sv, MediaType.APPLICATION_JSON).build();
        }
        StoredArtifactVersionDto versionContent = storage.getArtifactVersionContent(
                groupid, resourceid, versionid);
        String contentType = versionContent.getContentType() != null
                ? versionContent.getContentType() : MediaType.APPLICATION_OCTET_STREAM;
        return Response.ok(versionContent.getContent().bytes(), contentType).build();
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteSchemagroupSchemaVersion(String groupid, String resourceid,
            String versionid, Boolean meta, String specversion, String inline,
            List<String> filter, Boolean doc, long epoch) {
        io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto vmd =
                storage.getArtifactVersionMetaData(groupid, resourceid, versionid);
        checkEpochConflict((int) epoch, vmd.getEpoch());
        storage.deleteArtifactVersion(groupid, resourceid, versionid);
    }

    // ===== Private helper methods =====

    private Schema upsertSchemaFromContent(String groupid, String resourceid,
            Long epoch, byte[] content, String description) {
        ContentWrapperDto contentWrapper = ContentWrapperDto.builder()
                .content(ContentHandle.create(content))
                .contentType(MediaType.APPLICATION_JSON)
                .references(Collections.emptyList())
                .build();

        if (storage.isArtifactExists(groupid, resourceid)) {
            ArtifactMetaDataDto existing =
                    storage.getArtifactMetaData(groupid, resourceid);
            if (epoch != null) {
                checkEpochConflict(epoch.intValue(), existing.getEpoch());
            }
            EditableVersionMetaDataDto vmd = EditableVersionMetaDataDto.builder()
                    .description(description)
                    .build();
            storage.createArtifactVersion(groupid, resourceid, null,
                    ArtifactType.JSON, contentWrapper, vmd, Collections.emptyList(),
                    false, false, null);
        } else {
            EditableArtifactMetaDataDto amd = EditableArtifactMetaDataDto.builder()
                    .name(resourceid)
                    .description(description)
                    .build();
            EditableVersionMetaDataDto vmd = EditableVersionMetaDataDto.builder().build();
            storage.createArtifact(groupid, resourceid, ArtifactType.JSON, amd, null,
                    contentWrapper, vmd, Collections.emptyList(), false, false, null);
        }

        ArtifactMetaDataDto resultAmd = storage.getArtifactMetaData(groupid, resourceid);
        ResourceMetaDto rmd = storage.getResourceMeta(groupid, resourceid);
        URI basePath = buildSelfUri("");
        return XRegistryApiConverter.toSchema(resultAmd, rmd, basePath);
    }

    private Schema upsertSchemaFromBean(String groupid, String resourceid,
            Long epoch, Schema data) {
        // When a Schema bean is provided, we treat the serialized JSON as content
        // and also extract metadata from the bean
        EditableArtifactMetaDataDto amdEditable =
                XRegistryApiConverter.toEditableArtifactMetaData(data);
        ContentWrapperDto contentWrapper = ContentWrapperDto.builder()
                .content(ContentHandle.create("{}"))
                .contentType(MediaType.APPLICATION_JSON)
                .references(Collections.emptyList())
                .build();

        if (storage.isArtifactExists(groupid, resourceid)) {
            ArtifactMetaDataDto existing =
                    storage.getArtifactMetaData(groupid, resourceid);
            if (epoch != null) {
                checkEpochConflict(epoch.intValue(), existing.getEpoch());
            }
            storage.updateArtifactMetaData(groupid, resourceid, amdEditable);
        } else {
            EditableVersionMetaDataDto vmd = EditableVersionMetaDataDto.builder().build();
            storage.createArtifact(groupid, resourceid, ArtifactType.JSON, amdEditable,
                    null, contentWrapper, vmd, Collections.emptyList(), false, false, null);
        }

        ArtifactMetaDataDto resultAmd = storage.getArtifactMetaData(groupid, resourceid);
        ResourceMetaDto rmd = storage.getResourceMeta(groupid, resourceid);
        URI basePath = buildSelfUri("");
        return XRegistryApiConverter.toSchema(resultAmd, rmd, basePath);
    }

    private Schema createSchemaVersion(String groupid, String resourceid,
            byte[] content, String description) {
        ContentWrapperDto contentWrapper = ContentWrapperDto.builder()
                .content(ContentHandle.create(content))
                .contentType(MediaType.APPLICATION_JSON)
                .references(Collections.emptyList())
                .build();

        EditableVersionMetaDataDto vmd = EditableVersionMetaDataDto.builder()
                .description(description)
                .build();

        try {
            storage.createArtifactVersion(groupid, resourceid, null,
                    ArtifactType.JSON, contentWrapper, vmd, Collections.emptyList(),
                    false, false, null);
        } catch (ArtifactNotFoundException e) {
            // Resource does not exist yet, create it
            EditableArtifactMetaDataDto amd = EditableArtifactMetaDataDto.builder()
                    .name(resourceid)
                    .description(description)
                    .build();
            storage.createArtifact(groupid, resourceid, ArtifactType.JSON, amd, null,
                    contentWrapper, vmd, Collections.emptyList(), false, false, null);
        }

        ArtifactMetaDataDto resultAmd = storage.getArtifactMetaData(groupid, resourceid);
        ResourceMetaDto rmd = storage.getResourceMeta(groupid, resourceid);
        URI basePath = buildSelfUri("");
        return XRegistryApiConverter.toSchema(resultAmd, rmd, basePath);
    }

    private Schema createSchemaVersionFromBean(String groupid, String resourceid,
            Schema data, String description) {
        ContentWrapperDto contentWrapper = ContentWrapperDto.builder()
                .content(ContentHandle.create("{}"))
                .contentType(MediaType.APPLICATION_JSON)
                .references(Collections.emptyList())
                .build();

        String desc = description != null ? description : data.getDescription();
        EditableVersionMetaDataDto vmd = EditableVersionMetaDataDto.builder()
                .name(data.getName())
                .description(desc)
                .build();

        try {
            storage.createArtifactVersion(groupid, resourceid, null,
                    ArtifactType.JSON, contentWrapper, vmd, Collections.emptyList(),
                    false, false, null);
        } catch (ArtifactNotFoundException e) {
            EditableArtifactMetaDataDto amd =
                    XRegistryApiConverter.toEditableArtifactMetaData(data);
            storage.createArtifact(groupid, resourceid, ArtifactType.JSON, amd, null,
                    contentWrapper, vmd, Collections.emptyList(), false, false, null);
        }

        ArtifactMetaDataDto resultAmd = storage.getArtifactMetaData(groupid, resourceid);
        ResourceMetaDto rmd = storage.getResourceMeta(groupid, resourceid);
        URI basePath = buildSelfUri("");
        return XRegistryApiConverter.toSchema(resultAmd, rmd, basePath);
    }

    private byte[] readInputStream(InputStream is) {
        try {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new WebApplicationException("Failed to read request body",
                    Response.Status.BAD_REQUEST);
        }
    }
}
