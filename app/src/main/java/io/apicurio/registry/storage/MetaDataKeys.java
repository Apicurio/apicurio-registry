package io.apicurio.registry.storage;

import java.util.Date;
import java.util.Map;

import io.apicurio.registry.rest.beans.ArtifactType;

/**
 * @author Ales Justin
 */
public class MetaDataKeys {
    public static String ARTIFACT_ID = "artifact_id";
    public static String CONTENT = "content";
    public static String GLOBAL_ID = "global_id";
    public static String VERSION = "version";
    public static String NAME = "name";
    public static String TYPE = "type";
    public static String DESCRIPTION = "description";
    public static String CREATED_BY = "createdBy";
    public static String CREATED_ON = "createdOn";
    public static String MODIFIED_BY = "modifiedBy";
    public static String MODIFIED_ON = "modifiedOn";

    // Internal

    public static String DELETED = "_deleted";

    // Helpers

    public static ArtifactMetaDataDto toArtifactMetaData(Map<String, String> content) {
        ArtifactMetaDataDto dto = new ArtifactMetaDataDto();
        
        String createdOn = content.get(CREATED_ON);
        String modifiedOn = content.get(MODIFIED_ON);
        
        dto.setCreatedBy(content.get(CREATED_BY));
        if (createdOn != null) {
            dto.setCreatedOn(new Date(Long.parseLong(createdOn)));
        }
        dto.setModifiedBy(content.get(MODIFIED_BY));
        if (modifiedOn != null) {
            dto.setModifiedOn(new Date(Long.parseLong(modifiedOn)));
        }
        dto.setDescription(content.get(DESCRIPTION));
        dto.setName(content.get(NAME));
        dto.setType(ArtifactType.fromValue(content.get(TYPE)));
        dto.setVersion(Integer.parseInt(content.get(VERSION)));
        dto.setGlobalId(Long.parseLong(content.get(GLOBAL_ID)));
        return dto;
    }

    public static ArtifactVersionMetaDataDto toArtifactVersionMetaData(Map<String, String> content) {
        ArtifactVersionMetaDataDto dto = new ArtifactVersionMetaDataDto();
        String createdOn = content.get(CREATED_ON);
        if (createdOn != null) {
            dto.setCreatedOn(new Date(Long.parseLong(createdOn)));
        }
        dto.setCreatedBy(content.get(CREATED_BY));
        dto.setDescription(content.get(DESCRIPTION));
        dto.setName(content.get(NAME));
        dto.setType(ArtifactType.fromValue(content.get(TYPE)));
        dto.setVersion(Integer.parseInt(content.get(VERSION)));
        dto.setGlobalId(Long.parseLong(content.get(GLOBAL_ID)));
        return dto;
    }
}
