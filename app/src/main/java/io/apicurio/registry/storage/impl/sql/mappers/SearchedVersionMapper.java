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

package io.apicurio.registry.storage.impl.sql.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.ArtifactState;

/**
 * @author eric.wittmann@gmail.com
 */
public class SearchedVersionMapper implements RowMapper<SearchedVersionDto> {

    public static final SearchedVersionMapper instance = new SearchedVersionMapper();

    /**
     * Constructor.
     */
    private SearchedVersionMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public SearchedVersionDto map(ResultSet rs) throws SQLException {
        SearchedVersionDto dto = new SearchedVersionDto();
        dto.setGlobalId(rs.getLong("globalId"));
        dto.setVersion(rs.getString("version"));
        dto.setVersionId(rs.getInt("versionId"));
        dto.setContentId(rs.getLong("contentId"));
        dto.setState(ArtifactState.valueOf(rs.getString("state")));
        dto.setCreatedBy(rs.getString("createdBy"));
        dto.setCreatedOn(rs.getTimestamp("createdOn"));
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        dto.setLabels(SqlUtil.deserializeLabels(rs.getString("labels")));
        dto.setProperties(SqlUtil.deserializeProperties(rs.getString("properties")));
        dto.setType(rs.getString("type"));
        dto.setState(ArtifactState.valueOf(rs.getString("state")));
        return dto;
    }

}