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

import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.utils.impexp.CommentEntity;

/**
 * @author eric.wittmann@gmail.com
 */
public class CommentEntityMapper implements RowMapper<CommentEntity> {

    public static final CommentEntityMapper instance = new CommentEntityMapper();

    /**
     * Constructor.
     */
    private CommentEntityMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public CommentEntity map(ResultSet rs) throws SQLException {
        CommentEntity entity = new CommentEntity();
        entity.globalId = rs.getLong("globalId");
        entity.commentId = rs.getString("commentId");
        entity.createdBy = rs.getString("createdBy");
        entity.createdOn = rs.getTimestamp("createdOn").getTime();
        entity.value = rs.getString("cvalue");
        return entity;
    }

}