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

import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;

/**
 * @author eric.wittmann@gmail.com
 */
public class ArtifactVersionEntityMapper implements RowMapper<ArtifactVersionEntity> {

    public static final ArtifactVersionEntityMapper instance = new ArtifactVersionEntityMapper();

    /**
     * Constructor.
     */
    private ArtifactVersionEntityMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public ArtifactVersionEntity map(ResultSet rs) throws SQLException {
        ArtifactVersionEntity entity = new ArtifactVersionEntity();
        entity.globalId = rs.getLong("globalId");
        entity.groupId = SqlUtil.denormalizeGroupId(rs.getString("groupId"));
        entity.artifactId = rs.getString("artifactId");
        entity.version = rs.getString("version");
        entity.versionId = rs.getInt("versionId");
        entity.name = rs.getString("name");
        entity.description = rs.getString("description");
        entity.createdBy = rs.getString("createdBy");
        entity.createdOn = rs.getTimestamp("createdOn").getTime();
        entity.state = ArtifactState.valueOf(rs.getString("state"));
        entity.labels = SqlUtil.deserializeLabels(rs.getString("labels"));
        entity.properties = SqlUtil.deserializeProperties(rs.getString("properties"));
        entity.contentId = rs.getLong("contentId");
        entity.isLatest = entity.globalId == rs.getLong("latest");
        entity.artifactType = rs.getString("type");
        return entity;
    }

}