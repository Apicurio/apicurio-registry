/*
 * Copyright 2019 Red Hat
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

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.types.ArtifactType;

/**
 * @author eric.wittmann@gmail.com
 */
public final class DtoUtil {

    /**
     * Creates a jax-rs meta-data entity from the id, type, and storage meta-data.
     *
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    public static final ArtifactMetaData dtoToMetaData(String artifactId, ArtifactType artifactType,
                                                  ArtifactMetaDataDto dto) {
        ArtifactMetaData metaData = new ArtifactMetaData();
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(dto.getCreatedOn());
        metaData.setDescription(dto.getDescription());
        metaData.setId(artifactId);
        metaData.setModifiedBy(dto.getModifiedBy());
        metaData.setModifiedOn(dto.getModifiedOn());
        metaData.setName(dto.getName());
        metaData.setType(artifactType);
        metaData.setVersion(dto.getVersion());
        metaData.setGlobalId(dto.getGlobalId());
        metaData.setState(dto.getState());
        return metaData;
    }

    /**
     * Creates a jax-rs version meta-data entity from the id, type, and storage meta-data.
     *
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    public static final VersionMetaData dtoToVersionMetaData(String artifactId, ArtifactType artifactType,
                                                        ArtifactMetaDataDto dto) {
        VersionMetaData metaData = new VersionMetaData();
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(dto.getCreatedOn());
        metaData.setDescription(dto.getDescription());
        metaData.setName(dto.getName());
        metaData.setType(artifactType);
        metaData.setVersion(dto.getVersion());
        metaData.setGlobalId(dto.getGlobalId());
        metaData.setState(dto.getState());
        return metaData;
    }

    /**
     * Creates a jax-rs version meta-data entity from the id, type, and storage meta-data.
     *
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    public static final VersionMetaData dtoToVersionMetaData(String artifactId, ArtifactType artifactType,
                                                        ArtifactVersionMetaDataDto dto) {
        VersionMetaData metaData = new VersionMetaData();
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(dto.getCreatedOn());
        metaData.setDescription(dto.getDescription());
        metaData.setName(dto.getName());
        metaData.setType(artifactType);
        metaData.setVersion(dto.getVersion());
        metaData.setGlobalId(dto.getGlobalId());
        metaData.setState(dto.getState());
        return metaData;
    }

}
