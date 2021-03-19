/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.storage.impl;

import java.util.Comparator;
import java.util.Date;

import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.SearchedVersionDto;

/**
 * @author eric.wittmann@gmail.com
 */
public class SearchUtil {

    public static Comparator<ArtifactMetaDataDto> comparator(OrderBy orderBy, OrderDirection orderDirection) {
        return (dto1, dto2) -> compare(orderBy, orderDirection, dto1, dto2);
    }

    public static int compare(OrderBy orderBy, OrderDirection orderDirection, ArtifactMetaDataDto metaDataDto1, ArtifactMetaDataDto metaDataDto2) {
        String value1 = orderBy == OrderBy.name ? metaDataDto1.getName() : String.valueOf(metaDataDto1.getCreatedOn());
        if (value1 == null) {
            value1 = metaDataDto1.getId();
        }
        String value2 = orderBy == OrderBy.name ? metaDataDto2.getName() : String.valueOf(metaDataDto2.getCreatedOn());
        if (value2 == null) {
            value2 = metaDataDto2.getId();
        }
        return orderDirection == OrderDirection.desc ? value2.compareToIgnoreCase(value1) : value1.compareToIgnoreCase(value2);
    }

    public static SearchedArtifactDto buildSearchedArtifact(ArtifactMetaDataDto artifactMetaData) {
        final SearchedArtifactDto searchedArtifact = new SearchedArtifactDto();
        searchedArtifact.setGroupId(artifactMetaData.getGroupId());
        searchedArtifact.setId(artifactMetaData.getId());
        searchedArtifact.setName(artifactMetaData.getName());
        searchedArtifact.setState(artifactMetaData.getState());
        searchedArtifact.setDescription(artifactMetaData.getDescription());
        searchedArtifact.setCreatedOn(new Date(artifactMetaData.getCreatedOn()));
        searchedArtifact.setCreatedBy(artifactMetaData.getCreatedBy());
        searchedArtifact.setModifiedBy(artifactMetaData.getModifiedBy());
        searchedArtifact.setModifiedOn(new Date(artifactMetaData.getModifiedOn()));
        searchedArtifact.setType(artifactMetaData.getType());
        searchedArtifact.setLabels(artifactMetaData.getLabels());
        return searchedArtifact;
    }

    public static SearchedVersionDto buildSearchedVersion(ArtifactVersionMetaDataDto artifactVersionMetaData) {
        final SearchedVersionDto searchedVersion = new SearchedVersionDto();
        searchedVersion.setCreatedBy(artifactVersionMetaData.getCreatedBy());
        searchedVersion.setCreatedOn(new Date(artifactVersionMetaData.getCreatedOn()));
        searchedVersion.setDescription(artifactVersionMetaData.getDescription());
        searchedVersion.setGlobalId(artifactVersionMetaData.getGlobalId());
        searchedVersion.setContentId(artifactVersionMetaData.getContentId());
        searchedVersion.setName(artifactVersionMetaData.getName());
        searchedVersion.setState(artifactVersionMetaData.getState());
        searchedVersion.setType(artifactVersionMetaData.getType());
        searchedVersion.setVersion(artifactVersionMetaData.getVersion());
        searchedVersion.setLabels(artifactVersionMetaData.getLabels());
        return searchedVersion;
    }

}
