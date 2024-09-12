package io.apicurio.registry.rest.v3;

import io.apicurio.common.apps.config.DynamicConfigPropertyDef;
import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.rest.v3.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v3.beans.BranchMetaData;
import io.apicurio.registry.rest.v3.beans.BranchSearchResults;
import io.apicurio.registry.rest.v3.beans.Comment;
import io.apicurio.registry.rest.v3.beans.ConfigurationProperty;
import io.apicurio.registry.rest.v3.beans.GroupMetaData;
import io.apicurio.registry.rest.v3.beans.GroupSearchResults;
import io.apicurio.registry.rest.v3.beans.RoleMapping;
import io.apicurio.registry.rest.v3.beans.RoleMappingSearchResults;
import io.apicurio.registry.rest.v3.beans.SearchedArtifact;
import io.apicurio.registry.rest.v3.beans.SearchedBranch;
import io.apicurio.registry.rest.v3.beans.SearchedGroup;
import io.apicurio.registry.rest.v3.beans.SearchedVersion;
import io.apicurio.registry.rest.v3.beans.SortOrder;
import io.apicurio.registry.rest.v3.beans.VersionMetaData;
import io.apicurio.registry.rest.v3.beans.VersionSearchResults;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.BranchMetaDataDto;
import io.apicurio.registry.storage.dto.BranchSearchResultsDto;
import io.apicurio.registry.storage.dto.CommentDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.RoleMappingDto;
import io.apicurio.registry.storage.dto.RoleMappingSearchResultsDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.types.RoleType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.stream.Collectors;

public final class V3ApiUtil {

    private V3ApiUtil() {
    }

    /**
     * Creates a jax-rs meta-data entity from the id, type, and artifactStore meta-data.
     * 
     * @param dto
     */
    public static ArtifactMetaData dtoToArtifactMetaData(ArtifactMetaDataDto dto) {
        ArtifactMetaData metaData = new ArtifactMetaData();
        metaData.setOwner(dto.getOwner());
        metaData.setCreatedOn(new Date(dto.getCreatedOn()));
        metaData.setDescription(dto.getDescription());
        metaData.setGroupId(dto.getGroupId());
        metaData.setArtifactId(dto.getArtifactId());
        metaData.setModifiedBy(dto.getModifiedBy());
        metaData.setModifiedOn(new Date(dto.getModifiedOn()));
        metaData.setName(dto.getName());
        metaData.setArtifactType(dto.getArtifactType());
        metaData.setLabels(dto.getLabels());
        return metaData;
    }

    /**
     * Creates a jax-rs version meta-data entity from the id, type, and artifactStore meta-data.
     * 
     * @param dto
     */
    public static VersionMetaData dtoToVersionMetaData(ArtifactVersionMetaDataDto dto) {
        VersionMetaData metaData = new VersionMetaData();
        metaData.setGroupId(dto.getGroupId());
        metaData.setArtifactId(dto.getArtifactId());
        metaData.setOwner(dto.getOwner());
        metaData.setCreatedOn(new Date(dto.getCreatedOn()));
        metaData.setDescription(dto.getDescription());
        metaData.setName(dto.getName());
        metaData.setArtifactType(dto.getArtifactType());
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
     * @param dto
     * @param editableArtifactMetaData
     * @return the updated ArtifactMetaDataDto object
     */
    public static ArtifactMetaDataDto setEditableMetaDataInArtifact(ArtifactMetaDataDto dto,
            EditableArtifactMetaDataDto editableArtifactMetaData) {
        if (editableArtifactMetaData.getName() != null) {
            dto.setName(editableArtifactMetaData.getName());
        }
        if (editableArtifactMetaData.getDescription() != null) {
            dto.setDescription(editableArtifactMetaData.getDescription());
        }
        if (editableArtifactMetaData.getLabels() != null && !editableArtifactMetaData.getLabels().isEmpty()) {
            dto.setLabels(editableArtifactMetaData.getLabels());
        }
        return dto;
    }

    public static Comparator<ArtifactMetaDataDto> comparator(SortOrder sortOrder) {
        return (id1, id2) -> compare(sortOrder, id1, id2);
    }

    public static int compare(SortOrder sortOrder, ArtifactMetaDataDto metaDataDto1,
            ArtifactMetaDataDto metaDataDto2) {
        String name1 = metaDataDto1.getName();
        if (name1 == null) {
            name1 = metaDataDto1.getArtifactId();
        }
        String name2 = metaDataDto2.getName();
        if (name2 == null) {
            name2 = metaDataDto2.getArtifactId();
        }
        return sortOrder == SortOrder.desc ? name2.compareToIgnoreCase(name1)
            : name1.compareToIgnoreCase(name2);
    }

    public static ArtifactSearchResults dtoToSearchResults(ArtifactSearchResultsDto dto) {
        ArtifactSearchResults results = new ArtifactSearchResults();
        results.setCount((int) dto.getCount());
        results.setArtifacts(new ArrayList<>(dto.getArtifacts().size()));
        dto.getArtifacts().forEach(artifact -> {
            SearchedArtifact sa = new SearchedArtifact();
            sa.setOwner(artifact.getOwner());
            sa.setCreatedOn(artifact.getCreatedOn());
            sa.setDescription(artifact.getDescription());
            sa.setArtifactId(artifact.getArtifactId());
            sa.setGroupId(artifact.getGroupId());
            sa.setModifiedBy(artifact.getModifiedBy());
            sa.setModifiedOn(artifact.getModifiedOn());
            sa.setName(artifact.getName());
            sa.setArtifactType(artifact.getArtifactType());
            results.getArtifacts().add(sa);
        });
        return results;
    }

    public static GroupSearchResults dtoToSearchResults(GroupSearchResultsDto dto) {
        GroupSearchResults results = new GroupSearchResults();
        results.setCount(dto.getCount());
        results.setGroups(new ArrayList<>(dto.getGroups().size()));
        dto.getGroups().forEach(group -> {
            SearchedGroup sg = new SearchedGroup();
            sg.setOwner(group.getOwner());
            sg.setCreatedOn(group.getCreatedOn());
            sg.setDescription(group.getDescription());
            sg.setGroupId(group.getId());
            sg.setModifiedBy(group.getModifiedBy());
            sg.setModifiedOn(group.getModifiedOn());
            results.getGroups().add(sg);
        });
        return results;
    }

    public static BranchSearchResults dtoToSearchResults(BranchSearchResultsDto dto) {
        BranchSearchResults results = new BranchSearchResults();
        results.setCount(dto.getCount());
        results.setBranches(new ArrayList<>(dto.getBranches().size()));
        dto.getBranches().forEach(branch -> {
            SearchedBranch searchedBranch = new SearchedBranch();
            searchedBranch.setOwner(branch.getOwner());
            searchedBranch.setCreatedOn(new Date(branch.getCreatedOn()));
            searchedBranch.setDescription(branch.getDescription());
            searchedBranch.setSystemDefined(branch.isSystemDefined());
            searchedBranch.setBranchId(branch.getBranchId());
            searchedBranch.setModifiedBy(branch.getModifiedBy());
            searchedBranch.setModifiedOn(new Date(branch.getModifiedOn()));
            results.getBranches().add(searchedBranch);
        });
        return results;
    }

    public static VersionSearchResults dtoToSearchResults(VersionSearchResultsDto dto) {
        VersionSearchResults results = new VersionSearchResults();
        results.setCount((int) dto.getCount());
        results.setVersions(new ArrayList<>(dto.getVersions().size()));
        dto.getVersions().forEach(version -> {
            SearchedVersion sv = new SearchedVersion();
            sv.setGroupId(version.getGroupId());
            sv.setArtifactId(version.getArtifactId());
            sv.setVersion(version.getVersion());
            sv.setOwner(version.getOwner());
            sv.setCreatedOn(version.getCreatedOn());
            sv.setDescription(version.getDescription());
            sv.setGlobalId(version.getGlobalId());
            sv.setContentId(version.getContentId());
            sv.setName(version.getName());
            sv.setState(version.getState());
            sv.setArtifactType(version.getArtifactType());
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
        group.setGroupId(dto.getGroupId());
        group.setDescription(dto.getDescription());
        group.setOwner(dto.getOwner());
        group.setModifiedBy(dto.getModifiedBy());
        group.setCreatedOn(new Date(dto.getCreatedOn()));
        group.setModifiedOn(new Date(dto.getModifiedOn()));
        group.setLabels(dto.getLabels());
        return group;
    }

    public static Comment commentDtoToComment(CommentDto dto) {
        return Comment.builder().commentId(dto.getCommentId()).owner(dto.getOwner())
                .createdOn(new Date(dto.getCreatedOn())).value(dto.getValue()).build();
    }

    public static RoleMapping dtoToRoleMapping(RoleMappingDto dto) {
        RoleMapping mapping = new RoleMapping();
        mapping.setPrincipalId(dto.getPrincipalId());
        mapping.setRole(RoleType.valueOf(dto.getRole()));
        mapping.setPrincipalName(dto.getPrincipalName());
        return mapping;
    }

    public static RoleMappingSearchResults dtoToRoleMappingSearchResults(RoleMappingSearchResultsDto dto) {
        RoleMappingSearchResults results = new RoleMappingSearchResults();
        results.setCount((int) dto.getCount());
        results.setRoleMappings(dto.getRoleMappings().stream().map(rm -> {
            return dtoToRoleMapping(rm);
        }).collect(Collectors.toList()));
        return results;
    }

    public static ConfigurationProperty dtoToConfigurationProperty(DynamicConfigPropertyDef def,
            DynamicConfigPropertyDto dto) {
        ConfigurationProperty rval = new ConfigurationProperty();
        rval.setName(def.getName());
        rval.setValue(dto.getValue());
        rval.setType(def.getType().getName());
        rval.setLabel(def.getLabel());
        rval.setDescription(def.getDescription());
        return rval;
    }

    public static BranchMetaData dtoToBranchMetaData(BranchMetaDataDto branch) {
        return BranchMetaData.builder().groupId(branch.getGroupId()).artifactId(branch.getArtifactId())
                .branchId(branch.getBranchId()).description(branch.getDescription()).owner(branch.getOwner())
                .systemDefined(branch.isSystemDefined()).createdOn(new Date(branch.getCreatedOn()))
                .modifiedBy(branch.getModifiedBy()).modifiedOn(new Date(branch.getModifiedOn())).build();
    }
}
