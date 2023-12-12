package io.apicurio.registry.cncf.schemaregistry.impl;

import io.apicurio.registry.cncf.schemaregistry.beans.SchemaGroup;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;

import java.util.Date;

public final class CNCFApiUtil {

    public static SchemaGroup dtoToSchemaGroup(GroupMetaDataDto dto) {
        SchemaGroup group = new SchemaGroup();
        group.setId(dto.getGroupId());
        group.setDescription(dto.getDescription());
        if (dto.getArtifactsType() != null) {
            group.setFormat(dto.getArtifactsType());
        }
        group.setCreatedtimeutc(new Date(dto.getCreatedOn()));
        group.setUpdatedtimeutc(new Date(dto.getModifiedOn()));
        group.setGroupProperties(dto.getProperties());
        return group;
    }

}
