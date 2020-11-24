/*
 * Copyright 2020 JBoss Inc
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

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;

/**
 * Used to map a single row in the versions table to a {@link ArtifactVersionMetaDataDto} instance.
 * @author eric.wittmann@gmail.com
 */
public class ArtifactVersionMetaDataDtoMapper implements RowMapper<ArtifactVersionMetaDataDto> {

    public static final ArtifactVersionMetaDataDtoMapper instance = new ArtifactVersionMetaDataDtoMapper();

    /**
     * Constructor.
     */
    private ArtifactVersionMetaDataDtoMapper() {
    }

    /**
     * @see org.jdbi.v3.core.mapper.RowMapper#map(java.sql.ResultSet, org.jdbi.v3.core.statement.StatementContext)
     */
    @Override
    public ArtifactVersionMetaDataDto map(ResultSet rs, StatementContext ctx) throws SQLException {
        ArtifactVersionMetaDataDto dto = new ArtifactVersionMetaDataDto();
        dto.setGlobalId(rs.getLong("globalId"));
        dto.setState(ArtifactState.valueOf(rs.getString("state")));
        dto.setCreatedBy(rs.getString("createdBy"));
        dto.setCreatedOn(rs.getTimestamp("createdOn").getTime());
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        dto.setVersion(rs.getInt("version"));
        dto.setType(ArtifactType.valueOf(rs.getString("type")));
        dto.setLabels(SqlUtil.deserializeLabels(rs.getString("labels")));
        dto.setProperties(SqlUtil.deserializeProperties(rs.getString("properties")));
        return dto;
    }

}