package io.apicurio.registry.storage.impl.sql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.utils.StringUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SqlUtil {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Serializes the given collection of labels to a string for artifactStore in the DB.
     *
     * @param labels
     */
    public static String serializeLabels(List<String> labels) {
        try {
            if (labels == null) {
                return null;
            }
            if (labels.isEmpty()) {
                return null;
            }
            return mapper.writeValueAsString(labels);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deserialize the labels from their string form to a <code>List&lt;String&gt;</code> form.
     *
     * @param labelsStr
     */
    @SuppressWarnings("unchecked")
    public static List<String> deserializeLabels(String labelsStr) {
        try {
            if (StringUtil.isEmpty(labelsStr)) {
                return null;
            }
            return mapper.readValue(labelsStr, List.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Serializes the given collection of properties to a string for artifactStore in the DB.
     *
     * @param properties
     */
    public static String serializeProperties(Map<String, String> properties) {
        try {
            if (properties == null) {
                return null;
            }
            if (properties.isEmpty()) {
                return null;
            }
            return mapper.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deserialize the properties from their string form to a Map<String, String> form.
     *
     * @param propertiesStr
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> deserializeProperties(String propertiesStr) {
        try {
            if (StringUtil.isEmpty(propertiesStr)) {
                return null;
            }
            return mapper.readValue(propertiesStr, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Serializes the given collection of references to a string for artifactStore in the DB.
     *
     * @param references
     */
    public static String serializeReferences(List<ArtifactReferenceDto> references) {
        try {
            if (references == null || references.isEmpty()) {
                return null;
            }
            return mapper.writeValueAsString(references);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deserialize the references from their string form to a List<ArtifactReferenceDto> form.
     *
     * @param references
     */
    public static List<ArtifactReferenceDto> deserializeReferences(String references) {
        try {
            if (StringUtil.isEmpty(references)) {
                return Collections.emptyList();
            }
            return mapper.readValue(references, new TypeReference<List<ArtifactReferenceDto>>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String normalizeGroupId(String groupId) {
        return new GroupId(groupId).getRawGroupId();
    }

    public static String denormalizeGroupId(String groupId) {
        return new GroupId(groupId).getRawGroupIdWithNull();
    }


    public static ArtifactMetaDataDto convert(String groupId, String artifactId, ArtifactVersionMetaDataDto versionMeta) {
        ArtifactMetaDataDto artifactMeta = new ArtifactMetaDataDto();
        artifactMeta.setGlobalId(versionMeta.getGlobalId());
        artifactMeta.setContentId(versionMeta.getContentId());
        artifactMeta.setGroupId(denormalizeGroupId(groupId));
        artifactMeta.setId(artifactId);
        artifactMeta.setModifiedBy(versionMeta.getCreatedBy());
        artifactMeta.setModifiedOn(versionMeta.getCreatedOn());
        artifactMeta.setState(versionMeta.getState());
        artifactMeta.setName(versionMeta.getName());
        artifactMeta.setDescription(versionMeta.getDescription());
        artifactMeta.setLabels(versionMeta.getLabels());
        artifactMeta.setProperties(versionMeta.getProperties());
        artifactMeta.setType(versionMeta.getType());
        artifactMeta.setVersion(versionMeta.getVersion());
        artifactMeta.setVersionOrder(versionMeta.getVersionOrder());
        return artifactMeta;
    }
}
