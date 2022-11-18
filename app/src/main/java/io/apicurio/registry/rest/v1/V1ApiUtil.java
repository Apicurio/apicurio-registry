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

package io.apicurio.registry.rest.v1;

import java.util.ArrayList;
import io.apicurio.registry.rest.v1.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v1.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v1.beans.SearchedArtifact;
import io.apicurio.registry.rest.v1.beans.SearchedVersion;
import io.apicurio.registry.rest.v1.beans.VersionMetaData;
import io.apicurio.registry.rest.v1.beans.VersionSearchResults;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.util.VersionUtil;

/**
 * @author eric.wittmann@gmail.com
 */
public final class V1ApiUtil {

    /**
     * Creates a jax-rs meta-data entity from the id, type, and artifactStore meta-data.
     *
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    public static final ArtifactMetaData dtoToMetaData(String artifactId, String artifactType,
                                                  ArtifactMetaDataDto dto) {
        ArtifactMetaData metaData = new ArtifactMetaData();
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(dto.getCreatedOn());
        metaData.setDescription(dto.getDescription());
        if (artifactId != null) {
            metaData.setId(artifactId);
        } else {
            metaData.setId(dto.getId());
        }
        metaData.setModifiedBy(dto.getModifiedBy());
        metaData.setModifiedOn(dto.getModifiedOn());
        metaData.setName(dto.getName());
        if (artifactType != null) {
            metaData.setType(artifactType);
        } else {
            metaData.setType(dto.getType());
        }
        metaData.setVersion(VersionUtil.toInteger(dto.getVersion()));
        metaData.setGlobalId(dto.getGlobalId());
        metaData.setState(dto.getState());
        metaData.setLabels(dto.getLabels());
        metaData.setProperties(dto.getProperties());
        return metaData;
    }

    public static final ArtifactMetaData dtoToMetaData(String artifactId, String artifactType,
            ArtifactVersionMetaDataDto dto) {
        ArtifactMetaData metaData = new ArtifactMetaData();
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(dto.getCreatedOn());
        metaData.setDescription(dto.getDescription());
        metaData.setId(artifactId);
        metaData.setModifiedBy(dto.getCreatedBy());
        metaData.setModifiedOn(dto.getCreatedOn());
        metaData.setName(dto.getName());
        if (artifactType != null) {
            metaData.setType(artifactType);
        } else {
            metaData.setType(dto.getType());
        }
        metaData.setVersion(VersionUtil.toInteger(dto.getVersion()));
        metaData.setGlobalId(dto.getGlobalId());
        metaData.setState(dto.getState());
        metaData.setLabels(dto.getLabels());
        metaData.setProperties(dto.getProperties());
        return metaData;
    }


    /**
     * Creates a jax-rs version meta-data entity from the id, type, and artifactStore meta-data.
     *
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    public static final VersionMetaData dtoToVersionMetaData(String artifactId, String artifactType,
                                                        ArtifactMetaDataDto dto) {
        VersionMetaData metaData = new VersionMetaData();
        metaData.setId(artifactId);
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(dto.getCreatedOn());
        metaData.setDescription(dto.getDescription());
        metaData.setName(dto.getName());
        metaData.setType(artifactType);
        metaData.setVersion(VersionUtil.toInteger(dto.getVersion()));
        metaData.setGlobalId(dto.getGlobalId());
        metaData.setState(dto.getState());
        metaData.setLabels(dto.getLabels());
        metaData.setProperties(dto.getProperties());
        return metaData;
    }

    /**
     * Creates a jax-rs version meta-data entity from the id, type, and artifactStore meta-data.
     *
     * @param artifactId
     * @param artifactType
     * @param amd
     */
    public static final VersionMetaData dtoToVersionMetaData(String artifactId, String artifactType,
                                                             ArtifactMetaData amd) {
        VersionMetaData metaData = new VersionMetaData();
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
    public static final VersionMetaData dtoToVersionMetaData(String artifactId, String artifactType,
                                                             ArtifactVersionMetaDataDto dto) {
        VersionMetaData metaData = new VersionMetaData();
        metaData.setId(artifactId);
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(dto.getCreatedOn());
        metaData.setDescription(dto.getDescription());
        metaData.setName(dto.getName());
        metaData.setType(artifactType);
        metaData.setVersion(VersionUtil.toInteger(dto.getVersion()));
        metaData.setGlobalId(dto.getGlobalId());
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

    public static ArtifactSearchResults dtoToSearchResults(ArtifactSearchResultsDto dto) {
        ArtifactSearchResults results = new ArtifactSearchResults();
        results.setCount((int) dto.getCount());
        results.setArtifacts(new ArrayList<>(dto.getArtifacts().size()));
        dto.getArtifacts().forEach(artifact -> {
            SearchedArtifact sa = new SearchedArtifact();
            sa.setCreatedBy(artifact.getCreatedBy());
            sa.setCreatedOn(artifact.getCreatedOn().getTime());
            sa.setDescription(artifact.getDescription());
            sa.setId(artifact.getId());
            sa.setLabels(artifact.getLabels());
            sa.setModifiedBy(artifact.getModifiedBy());
            sa.setModifiedOn(artifact.getModifiedOn().getTime());
            sa.setName(artifact.getName());
            sa.setState(artifact.getState());
            sa.setType(artifact.getType());
            results.getArtifacts().add(sa);
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
            sv.setCreatedOn(version.getCreatedOn().getTime());
            sv.setDescription(version.getDescription());
            sv.setGlobalId(version.getGlobalId());
            sv.setLabels(version.getLabels());
            sv.setName(version.getName());
            sv.setState(version.getState());
            sv.setType(version.getType());
            sv.setVersion(VersionUtil.toLong(version.getVersion()));
            results.getVersions().add(sv);
        });
        return results;
    }

}
