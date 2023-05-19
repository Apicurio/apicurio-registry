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

import io.apicurio.registry.storage.dto.CommentDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

/**
 * @author eric.wittmann@gmail.com
 */
public class CommentDtoMapper implements RowMapper<CommentDto> {

    public static final CommentDtoMapper instance = new CommentDtoMapper();

    /**
     * Constructor.
     */
    private CommentDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public CommentDto map(ResultSet rs) throws SQLException {
        return CommentDto.builder()
                .commentId(rs.getString("commentId"))
                .createdBy(rs.getString("createdBy"))
                .createdOn(rs.getTimestamp("createdOn").getTime())
                .value(rs.getString("cvalue"))
                .build();
    }

}