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

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.impexp.GroupEntity;

/**
 * @author eric.wittmann@gmail.com
 */
public class GroupEntityMapper implements RowMapper<GroupEntity> {

    public static final GroupEntityMapper instance = new GroupEntityMapper();

    /**
     * Constructor.
     */
    private GroupEntityMapper() {
    }

    /**
     * @see org.jdbi.v3.core.mapper.RowMapper#map(java.sql.ResultSet, org.jdbi.v3.core.statement.StatementContext)
     */
    @Override
    public GroupEntity map(ResultSet rs, StatementContext ctx) throws SQLException {
        GroupEntity entity = new GroupEntity();
        entity.groupId = SqlUtil.denormalizeGroupId(rs.getString("groupId"));
        entity.description = rs.getString("description");
        String type = rs.getString("artifactsType");
        entity.artifactsType = type == null ? null : ArtifactType.valueOf(type);
        entity.createdBy = rs.getString("createdBy");
        entity.createdOn = rs.getTimestamp("createdOn").getTime();
        entity.modifiedBy = rs.getString("modifiedBy");
        entity.modifiedOn = rs.getTimestamp("modifiedOn").getTime();
        entity.properties = SqlUtil.deserializeProperties(rs.getString("properties"));
        return entity;
    }

}