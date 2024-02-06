package io.apicurio.registry.rest.v3;

import io.apicurio.registry.rest.v3.beans.*;
import io.apicurio.registry.storage.dto.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Collectors;

public final class V3ApiUtil {

    private V3ApiUtil() {
    }

    /**
     * Creates a jax-rs meta-data entity from the id, type, and artifactStore meta-data.
     *
     * @param groupId
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    public static ArtifactMetaData dtoToMetaData(String groupId, String artifactId,
                                                 String artifactType, ArtifactMetaDataDto dto) {
        ArtifactMetaData metaData = new ArtifactMetaData();
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(new Date(dto.getCreatedOn()));
        metaData.setDescription(dto.getDescription());
        if (groupId != null) {
            metaData.setGroupId(groupId);
        } else {
            metaData.setGroupId(dto.getGroupId());
        }
        if (artifactId != null) {
            metaData.setId(artifactId);
        } else {
            metaData.setId(dto.getId());
        }
        metaData.setModifiedBy(dto.getModifiedBy());
        metaData.setModifiedOn(new Date(dto.getModifiedOn()));
        metaData.setName(dto.getName());
        if (artifactType != null) {
            metaData.setType(artifactType);
        } else {
            metaData.setType(dto.getType());
        }
        metaData.setVersion(dto.getVersion());
        metaData.setGlobalId(dto.getGlobalId());
        metaData.setContentId(dto.getContentId());
        metaData.setState(dto.getState());
        metaData.setLabels(dto.getLabels());
        metaData.setReferences(Optional.ofNullable(dto.getReferences()).stream()
                .flatMap(references -> references.stream().map(V3ApiUtil::referenceDtoToReference))
                .collect(Collectors.toList()));
        return metaData;
    }

    /**
     * @param groupId
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    public static final ArtifactMetaData dtoToMetaData(String groupId, String artifactId, String artifactType,
                                                       ArtifactVersionMetaDataDto dto) {
        ArtifactMetaData metaData = new ArtifactMetaData();
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(new Date(dto.getCreatedOn()));
        metaData.setDescription(dto.getDescription());
        metaData.setGroupId(groupId);
        metaData.setId(artifactId);
        metaData.setModifiedBy(dto.getCreatedBy());
        metaData.setModifiedOn(new Date(dto.getCreatedOn()));
        metaData.setName(dto.getName());
        if (artifactType != null) {
            metaData.setType(artifactType);
        } else {
            metaData.setType(dto.getType());
        }
        metaData.setVersion(dto.getVersion());
        metaData.setGlobalId(dto.getGlobalId());
        metaData.setContentId(dto.getContentId());
        metaData.setState(dto.getState());
        metaData.setLabels(dto.getLabels());
        return metaData;
    }


    /**
     * Creates a jax-rs version meta-data entity from the id, type, and artifactStore meta-data.
     *
     * @param groupId
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    public static final VersionMetaData dtoToVersionMetaData(String groupId, String artifactId,
                                                             String artifactType, ArtifactMetaDataDto dto) {
        VersionMetaData metaData = new VersionMetaData();
        metaData.setGroupId(groupId);
        metaData.setId(artifactId);
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(new Date(dto.getCreatedOn()));
        metaData.setDescription(dto.getDescription());
        metaData.setName(dto.getName());
        metaData.setType(artifactType);
        metaData.setVersion(dto.getVersion());
        metaData.setGlobalId(dto.getGlobalId());
        metaData.setContentId(dto.getContentId());
        metaData.setState(dto.getState());
        metaData.setLabels(dto.getLabels());
        return metaData;
    }

    /**
     * Creates a jax-rs version meta-data entity from the id, type, and artifactStore meta-data.
     *
     * @param groupId
     * @param artifactId
     * @param artifactType
     * @param amd
     */
    public static final VersionMetaData dtoToVersionMetaData(String groupId, String artifactId,
                                                             String artifactType, ArtifactMetaData amd) {
        VersionMetaData metaData = new VersionMetaData();
        metaData.setGroupId(groupId);
        metaData.setId(artifactId);
        metaData.setCreatedBy(amd.getCreatedBy());
        metaData.setCreatedOn(amd.getCreatedOn());
        metaData.setDescription(amd.getDescription());
        metaData.setName(amd.getName());
        metaData.setType(artifactType);
        metaData.setVersion(amd.getVersion());
        metaData.setGlobalId(amd.getGlobalId());
        metaData.setState(amd.getState());
        metaData.setLabels(amd.getLabels());
        return metaData;
    }

    /**
     * Creates a jax-rs version meta-data entity from the id, type, and artifactStore meta-data.
     *
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    public static final VersionMetaData dtoToVersionMetaData(String groupId, String artifactId, String artifactType,
                                                             ArtifactVersionMetaDataDto dto) {
        VersionMetaData metaData = new VersionMetaData();
        metaData.setGroupId(groupId);
        metaData.setId(artifactId);
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(new Date(dto.getCreatedOn()));
        metaData.setDescription(dto.getDescription());
        metaData.setName(dto.getName());
        metaData.setType(artifactType);
        metaData.setVersion(dto.getVersion());
        metaData.setGlobalId(dto.getGlobalId());
        metaData.setContentId(dto.getContentId());
        metaData.setState(dto.getState());
        metaData.setLabels(dto.getLabels());
        return metaData;
    }

    /**
     * Sets values from the EditableArtifactMetaDataDto into the ArtifactMetaDataDto.
     *
     * @param amdd
     * @param editableArtifactMetaData
     * @return the updated ArtifactMetaDataDto object
     */
    public static final ArtifactMetaDataDto setEditableMetaDataInArtifact(ArtifactMetaDataDto amdd, EditableArtifactMetaDataDto editableArtifactMetaData) {
        if (editableArtifactMetaData.getName() != null) {
            amdd.setName(editableArtifactMetaData.getName());
        }
        if (editableArtifactMetaData.getDescription() != null) {
            amdd.setDescription(editableArtifactMetaData.getDescription());
        }
        if (editableArtifactMetaData.getLabels() != null && !editableArtifactMetaData.getLabels().isEmpty()) {
            amdd.setLabels(editableArtifactMetaData.getLabels());
        }
        return amdd;
    }

    public static Comparator<ArtifactMetaDataDto> comparator(SortOrder sortOrder) {
        return (id1, id2) -> compare(sortOrder, id1, id2);
    }

    public static int compare(SortOrder sortOrder, ArtifactMetaDataDto metaDataDto1, ArtifactMetaDataDto metaDataDto2) {
        String name1 = metaDataDto1.getName();
        if (name1 == null) {
            name1 = metaDataDto1.getId();
        }
        String name2 = metaDataDto2.getName();
        if (name2 == null) {
            name2 = metaDataDto2.getId();
        }
        return sortOrder == SortOrder.desc ? name2.compareToIgnoreCase(name1) : name1.compareToIgnoreCase(name2);
    }

    public static ArtifactSearchResults dtoToSearchResults(ArtifactSearchResultsDto dto) {
        ArtifactSearchResults results = new ArtifactSearchResults();
        results.setCount((int) dto.getCount());
        results.setArtifacts(new ArrayList<>(dto.getArtifacts().size()));
        dto.getArtifacts().forEach(artifact -> {
            SearchedArtifact sa = new SearchedArtifact();
            sa.setCreatedBy(artifact.getCreatedBy());
            sa.setCreatedOn(artifact.getCreatedOn());
            sa.setDescription(artifact.getDescription());
            sa.setId(artifact.getId());
            sa.setGroupId(artifact.getGroupId());
            sa.setModifiedBy(artifact.getModifiedBy());
            sa.setModifiedOn(artifact.getModifiedOn());
            sa.setName(artifact.getName());
            sa.setState(artifact.getState());
            sa.setType(artifact.getType());
            results.getArtifacts().add(sa);
        });
        return results;
    }

    public static GroupSearchResults dtoToSearchResults(GroupSearchResultsDto dto) {
        GroupSearchResults results = new GroupSearchResults();
        results.setCount((int) dto.getCount());
        results.setGroups(new ArrayList<>(dto.getGroups().size()));
        dto.getGroups().forEach(group -> {
            SearchedGroup sg = new SearchedGroup();
            sg.setCreatedBy(group.getCreatedBy());
            sg.setCreatedOn(group.getCreatedOn());
            sg.setDescription(group.getDescription());
            sg.setId(group.getId());
            sg.setModifiedBy(group.getModifiedBy());
            sg.setModifiedOn(group.getModifiedOn());
            results.getGroups().add(sg);
        });
        return results;
    }

    public static VersionSearchResults dtoToSearchResults(VersionSearchResultsDto dto) {
        VersionSearchResults results = new VersionSearchResults();
        results.setCount((int) dto.getCount());
        results.setVersions(new ArrayList<>(dto.getVersions().size()));
        dto.getVersions().forEach(version -> {
            SearchedVersion sv = new SearchedVersion();
            sv.setCreatedBy(version.getCreatedBy());
            sv.setCreatedOn(version.getCreatedOn());
            sv.setDescription(version.getDescription());
            sv.setGlobalId(version.getGlobalId());
            sv.setContentId(version.getContentId());
            sv.setName(version.getName());
            sv.setState(version.getState());
            sv.setType(version.getType());
            sv.setVersion(version.getVersion());
            results.getVersions().add(sv);
        });
        return results;
    }

    public static ArtifactReferenceDto referenceToDto(ArtifactReference reference) {
        final ArtifactReferenceDto artifactReference = new ArtifactReferenceDto();
        artifactReference.setGroupId(reference.getGroupId());
        artifactReference.setName(reference.getName());
        artifactReference.setVersion(reference.getVersion());
        artifactReference.setArtifactId(reference.getArtifactId());
        return artifactReference;
    }

    public static ArtifactReference referenceDtoToReference(ArtifactReferenceDto reference) {
        final ArtifactReference artifactReference = new ArtifactReference();
        artifactReference.setGroupId(reference.getGroupId());
        artifactReference.setName(reference.getName());
        artifactReference.setVersion(reference.getVersion());
        artifactReference.setArtifactId(reference.getArtifactId());
        return artifactReference;
    }

    public static GroupMetaData groupDtoToGroup(GroupMetaDataDto dto) {
        GroupMetaData group = new GroupMetaData();
        group.setId(dto.getGroupId());
        group.setDescription(dto.getDescription());
        group.setCreatedBy(dto.getCreatedBy());
        group.setModifiedBy(dto.getModifiedBy());
        group.setCreatedOn(new Date(dto.getCreatedOn()));
        group.setModifiedOn(new Date(dto.getModifiedOn()));
        group.setProperties(dto.getLabels());
        return group;
    }

    public static Comment commentDtoToComment(CommentDto dto) {
        return Comment.builder()
                .commentId(dto.getCommentId())
                .createdBy(dto.getCreatedBy())
                .createdOn(new Date(dto.getCreatedOn()))
                .value(dto.getValue())
                .build();
    }
}
