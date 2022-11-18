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

package io.apicurio.registry.storage.impl.sql.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

/**
 * @author Fabian Martinez
 */
public class GroupMetaDataDtoMapper implements RowMapper<GroupMetaDataDto> {

    public static final GroupMetaDataDtoMapper instance = new GroupMetaDataDtoMapper();

    /**
     * Constructor.
     */
    private GroupMetaDataDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public GroupMetaDataDto map(ResultSet rs) throws SQLException {
        GroupMetaDataDto dto = new GroupMetaDataDto();
        dto.setGroupId(SqlUtil.denormalizeGroupId(rs.getString("groupId")));
        dto.setDescription(rs.getString("description"));

        String type = rs.getString("artifactsType");
        dto.setArtifactsType(type);

        dto.setCreatedBy(rs.getString("createdBy"));
        dto.setCreatedOn(rs.getTimestamp("createdOn").getTime());

        dto.setModifiedBy(rs.getString("modifiedBy"));
        Timestamp modifiedOn = rs.getTimestamp("modifiedOn");
        dto.setModifiedOn(modifiedOn == null ? 0 : modifiedOn.getTime());

        dto.setProperties(SqlUtil.deserializeProperties(rs.getString("properties")));

        return dto;
    }

}