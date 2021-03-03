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

package io.apicurio.registry.streams.utils;

import java.util.Objects;

import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.proto.Str.GroupMetaDataValue;
import io.apicurio.registry.types.ArtifactType;

/**
 * @author Fabian Martinez
 */
public class GroupMetaDataUtils {

    public static GroupMetaDataValue toValue(String groupId) {
        return GroupMetaDataValue.newBuilder()
                .setGroupId(groupId)
                .build();
    }

    public static GroupMetaDataValue toValue(GroupMetaDataDto dto) {
        Objects.requireNonNull(dto);
        GroupMetaDataValue.Builder builder = GroupMetaDataValue.newBuilder()
                .setGroupId(dto.getGroupId())
                .setDescription(dto.getDescription())
                .setArtifactsType(dto.getArtifactsType() == null ? -1 : dto.getArtifactsType().ordinal())
                .setCreatedBy(dto.getCreatedBy())
                .setCreatedOn(dto.getCreatedOn())
                .setModifiedBy(dto.getModifiedBy())
                .setModifiedOn(dto.getModifiedOn());
        if (dto.getProperties() != null) {
            builder.putAllProperties(dto.getProperties());
        }
        return builder.build();
    }

    public static GroupMetaDataDto toDto(GroupMetaDataValue value) {
        return GroupMetaDataDto.builder()
                .groupId(value.getGroupId())
                .description(value.getDescription())
                .artifactsType(value.getArtifactsType() == -1 ? null : ArtifactType.values()[value.getArtifactsType()])
                .createdBy(value.getCreatedBy())
                .createdOn(value.getCreatedOn())
                .modifiedBy(value.getModifiedBy())
                .modifiedOn(value.getModifiedOn())
                .properties(value.getPropertiesMap())
                .build();
    }

}
