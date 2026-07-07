package io.apicurio.registry.xregistry.rest.v1.impl;

import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableGroupMetaDataDto;
import io.apicurio.registry.storage.dto.EditableResourceMetaDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.ResourceMetaDto;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.xregistry.rest.v1.beans.Meta;
import io.apicurio.registry.xregistry.rest.v1.beans.Schema;
import io.apicurio.registry.xregistry.rest.v1.beans.SchemaVersion;
import io.apicurio.registry.xregistry.rest.v1.beans.Schemagroup;

import java.net.URI;
import java.util.Date;

/**
 * Utility class for converting between storage DTOs and xRegistry REST API beans.
 */
public final class XRegistryApiConverter {

    private XRegistryApiConverter() {
    }

    /**
     * Converts a GroupMetaDataDto to an xRegistry Schemagroup bean.
     */
    public static Schemagroup toSchemagroup(GroupMetaDataDto dto, URI basePath) {
        Schemagroup sg = new Schemagroup();
        sg.setSchemagroupid(dto.getGroupId());
        sg.setDescription(dto.getDescription());
        sg.setEpoch((int) dto.getEpoch());
        sg.setCreatedat(new Date(dto.getCreatedOn()));
        sg.setModifiedat(new Date(dto.getModifiedOn()));
        if (basePath != null) {
            sg.setSelf(basePath.resolve("/apis/xregistry/v1/schemagroups/" + dto.getGroupId()));
            sg.setXid(basePath.resolve(
                    "/apis/xregistry/v1/schemagroups/" + dto.getGroupId()).toString());
        }
        return sg;
    }

    /**
     * Converts an ArtifactMetaDataDto (with optional ResourceMetaDto) to an xRegistry Schema bean.
     */
    public static Schema toSchema(ArtifactMetaDataDto amd, ResourceMetaDto rmd, URI basePath) {
        Schema schema = new Schema();
        schema.setSchemaid(amd.getArtifactId());
        schema.setName(amd.getName());
        schema.setDescription(amd.getDescription());
        schema.setEpoch((int) amd.getEpoch());
        schema.setCreatedat(new Date(amd.getCreatedOn()));
        schema.setModifiedat(new Date(amd.getModifiedOn()));
        if (basePath != null) {
            String path = "/apis/xregistry/v1/schemagroups/" + amd.getGroupId()
                    + "/schemas/" + amd.getArtifactId();
            schema.setSelf(basePath.resolve(path));
            schema.setXid(basePath.resolve(path).toString());
        }
        return schema;
    }

    /**
     * Converts a SearchedArtifactDto to an xRegistry Schema bean.
     */
    public static Schema toSchema(SearchedArtifactDto dto, URI basePath) {
        Schema schema = new Schema();
        schema.setSchemaid(dto.getArtifactId());
        schema.setName(dto.getName());
        schema.setDescription(dto.getDescription());
        if (dto.getCreatedOn() != null) {
            schema.setCreatedat(dto.getCreatedOn());
        }
        if (dto.getModifiedOn() != null) {
            schema.setModifiedat(dto.getModifiedOn());
        }
        if (basePath != null) {
            String path = "/apis/xregistry/v1/schemagroups/" + dto.getGroupId()
                    + "/schemas/" + dto.getArtifactId();
            schema.setSelf(basePath.resolve(path));
            schema.setXid(basePath.resolve(path).toString());
        }
        return schema;
    }

    /**
     * Converts an ArtifactVersionMetaDataDto to an xRegistry SchemaVersion bean.
     */
    public static SchemaVersion toSchemaVersion(ArtifactVersionMetaDataDto dto, URI basePath) {
        SchemaVersion sv = new SchemaVersion();
        sv.setVersionid(dto.getVersion());
        sv.setSchemaid(dto.getArtifactId());
        sv.setName(dto.getName());
        sv.setDescription(dto.getDescription());
        sv.setEpoch((int) dto.getEpoch());
        sv.setCreatedat(new Date(dto.getCreatedOn()));
        sv.setModifiedat(new Date(dto.getModifiedOn()));
        if (dto.getArtifactType() != null) {
            sv.setFormat(dto.getArtifactType());
        }
        if (basePath != null) {
            String path = "/apis/xregistry/v1/schemagroups/" + dto.getGroupId()
                    + "/schemas/" + dto.getArtifactId()
                    + "/versions/" + dto.getVersion();
            sv.setSelf(basePath.resolve(path));
            sv.setXid(basePath.resolve(path).toString());
        }
        return sv;
    }

    /**
     * Converts a SearchedVersionDto to an xRegistry SchemaVersion bean.
     */
    public static SchemaVersion toSchemaVersion(SearchedVersionDto dto, URI basePath) {
        SchemaVersion sv = new SchemaVersion();
        sv.setVersionid(dto.getVersion());
        sv.setSchemaid(dto.getArtifactId());
        sv.setName(dto.getName());
        sv.setDescription(dto.getDescription());
        if (dto.getCreatedOn() != null) {
            sv.setCreatedat(dto.getCreatedOn());
        }
        if (dto.getModifiedOn() != null) {
            sv.setModifiedat(dto.getModifiedOn());
        }
        if (dto.getArtifactType() != null) {
            sv.setFormat(dto.getArtifactType());
        }
        if (basePath != null) {
            String path = "/apis/xregistry/v1/schemagroups/" + dto.getGroupId()
                    + "/schemas/" + dto.getArtifactId()
                    + "/versions/" + dto.getVersion();
            sv.setSelf(basePath.resolve(path));
            sv.setXid(basePath.resolve(path).toString());
        }
        return sv;
    }

    /**
     * Converts ResourceMetaDto and ArtifactMetaDataDto to an xRegistry Meta bean.
     */
    public static Meta toMeta(ResourceMetaDto rmd, ArtifactMetaDataDto amd, URI basePath) {
        Meta meta = new Meta();
        meta.setRESOURCEid(amd.getArtifactId());
        meta.setEpoch((int) rmd.getEpoch());
        meta.setCreatedat(new Date(amd.getCreatedOn()));
        meta.setModifiedat(new Date(amd.getModifiedOn()));
        meta.setCompatibility(rmd.getCompatibility() != null ? rmd.getCompatibility() : "none");
        meta.setReadonly(rmd.getReadonly() != null ? rmd.getReadonly() : false);
        meta.setDefaultversionid(rmd.getDefaultVersionId());
        meta.setDefaultversionsticky(
                rmd.getDefaultVersionSticky() != null ? rmd.getDefaultVersionSticky() : false);
        if (rmd.getXref() != null) {
            meta.setXref(URI.create(rmd.getXref()));
        }
        if (basePath != null) {
            String path = "/apis/xregistry/v1/schemagroups/" + amd.getGroupId()
                    + "/schemas/" + amd.getArtifactId();
            meta.setSelf(basePath.resolve(path + "/meta"));
            meta.setXid(basePath.resolve(path).toString());
            if (rmd.getDefaultVersionId() != null) {
                meta.setDefaultversionurl(
                        basePath.resolve(path + "/versions/" + rmd.getDefaultVersionId()));
            }
        }
        return meta;
    }

    /**
     * Converts an xRegistry Schemagroup bean to an EditableGroupMetaDataDto.
     */
    public static EditableGroupMetaDataDto toEditableGroupMetaData(Schemagroup sg) {
        return EditableGroupMetaDataDto.builder()
                .description(sg.getDescription())
                .build();
    }

    /**
     * Converts an xRegistry Meta bean to an EditableResourceMetaDto.
     */
    public static EditableResourceMetaDto toEditableResourceMeta(Meta meta) {
        EditableResourceMetaDto.EditableResourceMetaDtoBuilder builder =
                EditableResourceMetaDto.builder();
        builder.compatibility(meta.getCompatibility());
        builder.readonly(meta.getReadonly());
        builder.defaultVersionId(meta.getDefaultversionid());
        builder.defaultVersionSticky(meta.getDefaultversionsticky());
        if (meta.getXref() != null) {
            builder.xref(meta.getXref().toString());
        }
        return builder.build();
    }

    /**
     * Converts an xRegistry Schema bean to an EditableArtifactMetaDataDto.
     */
    public static EditableArtifactMetaDataDto toEditableArtifactMetaData(Schema schema) {
        return EditableArtifactMetaDataDto.builder()
                .name(schema.getName())
                .description(schema.getDescription())
                .build();
    }
}
