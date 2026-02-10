package io.apicurio.registry.rest;

import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;

/**
 * Shared utility methods for working with API DTOs.
 * This class contains methods that are used by both v2 and v3 API implementations.
 */
public final class ApiDtoUtils {

    private ApiDtoUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Sets values from the EditableArtifactMetaDataDto into the ArtifactMetaDataDto.
     *
     * @param dto the target DTO to update
     * @param editableMetaData the source of editable metadata
     * @return the updated ArtifactMetaDataDto object
     */
    public static ArtifactMetaDataDto setEditableMetaDataInArtifact(ArtifactMetaDataDto dto,
            EditableArtifactMetaDataDto editableMetaData) {
        if (editableMetaData.getName() != null) {
            dto.setName(editableMetaData.getName());
        }
        if (editableMetaData.getDescription() != null) {
            dto.setDescription(editableMetaData.getDescription());
        }
        if (editableMetaData.getLabels() != null && !editableMetaData.getLabels().isEmpty()) {
            dto.setLabels(editableMetaData.getLabels());
        }
        return dto;
    }

    /**
     * Converts a "default" group ID to null.
     *
     * @param groupId the group ID
     * @return null if the groupId is "default", otherwise the original value
     */
    public static String defaultGroupIdToNull(String groupId) {
        if ("default".equalsIgnoreCase(groupId)) {
            return null;
        }
        return groupId;
    }

    /**
     * Converts a null group ID to "default".
     *
     * @param groupId the group ID
     * @return "default" if the groupId is null, otherwise the original value
     */
    public static String nullGroupIdToDefault(String groupId) {
        return groupId != null ? groupId : "default";
    }

}
