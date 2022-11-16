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

package io.apicurio.registry.cncf.schemaregistry.impl;

import java.util.Date;

import io.apicurio.registry.cncf.schemaregistry.beans.SchemaGroup;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;

/**
 * @author Fabian Martinez
 */
public final class CNCFApiUtil {

    public static SchemaGroup dtoToSchemaGroup(GroupMetaDataDto dto) {
        SchemaGroup group = new SchemaGroup();
        group.setId(dto.getGroupId());
        group.setDescription(dto.getDescription());
        if (dto.getArtifactsType() != null) {
            group.setFormat(dto.getArtifactsType());
        }
        group.setCreatedtimeutc(new Date(dto.getCreatedOn()));
        group.setUpdatedtimeutc(new Date(dto.getModifiedOn()));
        group.setGroupProperties(dto.getProperties());
        return group;
    }

}
