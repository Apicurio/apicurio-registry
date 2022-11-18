/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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

package io.apicurio.registry.rest.v2;

import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.GroupMetaData;
import io.apicurio.registry.rest.v2.beans.GroupSearchResults;
import io.apicurio.registry.rest.v2.beans.SearchedArtifact;
import io.apicurio.registry.rest.v2.beans.SearchedGroup;
import io.apicurio.registry.rest.v2.beans.SearchedVersion;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.VersionSearchResults;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;

/**
 * @author eric.wittmann@gmail.com
 */
public final class V2ApiUtil {

    /**
     * Creates a jax-rs meta-data entity from the id, type, and artifactStore meta-data.
     *
     * @param groupId
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    public static final ArtifactMetaData dtoToMetaData(String groupId, String artifactId,
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
        metaData.setProperties(dto.getProperties());
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
        metaData.setProperties(dto.getProperties());
        return metaData;
    }


    /**
     * Creates a jax-rs version meta-data entity from the id, type, and artifactStore meta-data.
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
        metaData.setProperties(dto.getProperties());
        return metaData;
    }

    /**
     * Creates a jax-rs version meta-data entity from the id, type, and artifactStore meta-data.
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
        metaData.setProperties(amd.getProperties());
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
        metaData.setProperties(dto.getProperties());
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
        if (editableArtifactMetaData.getProperties() != null) {
            amdd.setProperties(editableArtifactMetaData.getProperties());
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
            sa.setLabels(artifact.getLabels());
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
            sv.setLabels(version.getLabels());
            sv.setName(version.getName());
            sv.setState(version.getState());
            sv.setType(version.getType());
            sv.setProperties(version.getProperties());
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
        group.setProperties(dto.getProperties());
        return group;
    }
}
