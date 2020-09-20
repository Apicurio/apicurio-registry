/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.util;

import io.apicurio.registry.rest.beans.SearchedArtifact;
import io.apicurio.registry.rest.beans.SearchedVersion;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;

import java.util.Comparator;

/**
 * @author Ales Justin
 */
public class SearchUtil {

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

    public static SearchedArtifact buildSearchedArtifact(ArtifactMetaDataDto artifactMetaData) {
        final SearchedArtifact searchedArtifact = new SearchedArtifact();
        searchedArtifact.setId(artifactMetaData.getId());
        searchedArtifact.setName(artifactMetaData.getName());
        searchedArtifact.setState(artifactMetaData.getState());
        searchedArtifact.setDescription(artifactMetaData.getDescription());
        searchedArtifact.setCreatedOn(artifactMetaData.getCreatedOn());
        searchedArtifact.setCreatedBy(artifactMetaData.getCreatedBy());
        searchedArtifact.setModifiedBy(artifactMetaData.getModifiedBy());
        searchedArtifact.setModifiedOn(artifactMetaData.getModifiedOn());
        searchedArtifact.setType(artifactMetaData.getType());
        searchedArtifact.setLabels(artifactMetaData.getLabels());

        return searchedArtifact;
    }

    public static SearchedVersion buildSearchedVersion(ArtifactVersionMetaDataDto artifactVersionMetaData) {

        final SearchedVersion searchedVersion = new SearchedVersion();
        searchedVersion.setCreatedBy(artifactVersionMetaData.getCreatedBy());
        searchedVersion.setCreatedOn(artifactVersionMetaData.getCreatedOn());
        searchedVersion.setDescription(artifactVersionMetaData.getDescription());
        searchedVersion.setGlobalId(artifactVersionMetaData.getGlobalId());
        searchedVersion.setName(artifactVersionMetaData.getName());
        searchedVersion.setState(artifactVersionMetaData.getState());
        searchedVersion.setType(artifactVersionMetaData.getType());
        searchedVersion.setVersion(artifactVersionMetaData.getVersion());
        searchedVersion.setLabels(artifactVersionMetaData.getLabels());

        return searchedVersion;
    }
}
