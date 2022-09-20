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
import io.apicurio.registry.utils.impexp.ContentEntity;

/**
 * @author eric.wittmann@gmail.com
 */
public class ContentEntityMapper implements RowMapper<ContentEntity> {

    public static final ContentEntityMapper instance = new ContentEntityMapper();

    /**
     * Constructor.
     */
    private ContentEntityMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public ContentEntity map(ResultSet rs) throws SQLException {
        ContentEntity entity = new ContentEntity();
        entity.contentId = rs.getLong("contentId");
        entity.canonicalHash = rs.getString("canonicalHash");
        entity.contentHash = rs.getString("contentHash");
        entity.contentBytes = rs.getBytes("content");
        entity.serializedReferences = rs.getString("artifactreferences");
        return entity;
    }

}